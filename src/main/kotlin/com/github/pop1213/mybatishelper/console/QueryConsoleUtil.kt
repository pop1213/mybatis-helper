package com.github.pop1213.mybatishelper.console

import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.lang.Language
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import java.awt.datatransfer.StringSelection

/**
 * Opens or executes a reconstructed MyBatis SQL:
 *
 * - [openSql]  → copy + open in Query Console / scratch file (review before running)
 * - [executeSql] → copy + open in Query Console / scratch file + execute-focused notification
 *
 * When the IntelliJ Database plugin (`com.intellij.database`) is present the SQL is
 * injected into a Query Console for the first available data source. In all other cases
 * a scratch `.sql` file is created and the SQL is copied to the clipboard.
 */
object QueryConsoleUtil {

    /** NotificationGroup id — must match the declaration in plugin.xml. */
    private const val NOTIFICATION_GROUP = "MyBatis SQL Helper"

    // ------------------------------------------------------------------
    // Public entry points
    // ------------------------------------------------------------------

    /** 🔵 Open: copy SQL and open it in an editor / Query Console for review. */
    fun openSql(project: Project, sql: String) {
        copyToClipboard(sql)
        ApplicationManager.getApplication().invokeLater {
            if (tryOpenDatabaseQueryConsole(project, sql)) return@invokeLater
            if (tryOpenScratchFile(project, sql, forExecute = false)) return@invokeLater
            showOpenNotification(project, sql, copiedOnly = true)
        }
    }

    /**
     * 🟢 Execute: copy SQL, open it in a Query Console / scratch file, and prompt
     * the user to run it (Ctrl+Enter in the editor / console).
     */
    fun executeSql(project: Project, sql: String) {
        copyToClipboard(sql)
        ApplicationManager.getApplication().invokeLater {
            // Try to open in DB Query Console and auto-execute
            if (tryExecuteInDatabaseConsole(project, sql)) return@invokeLater
            // Fallback: open scratch file and instruct user to run
            if (tryOpenScratchFile(project, sql, forExecute = true)) return@invokeLater
            showExecuteNotification(project, sql, noConsole = true)
        }
    }

    // ------------------------------------------------------------------
    // Strategy 1a — Open in Database Query Console
    // ------------------------------------------------------------------

    private fun tryOpenDatabaseQueryConsole(project: Project, sql: String): Boolean =
        injectSqlToConsole(project, sql, execute = false)

    // ------------------------------------------------------------------
    // Strategy 1b — Execute in Database Query Console
    // ------------------------------------------------------------------

    private fun tryExecuteInDatabaseConsole(project: Project, sql: String): Boolean =
        injectSqlToConsole(project, sql, execute = true)

    /**
     * Injects [sql] into a Query Console for the first available data source.
     * When [execute] is true, triggers the "Execute" action after injecting.
     * Returns false if the Database plugin is absent or no data source exists.
     */
    private fun injectSqlToConsole(project: Project, sql: String, execute: Boolean): Boolean {
        return try {
            // Resolve classes lazily — safe to use even without the DB plugin at compile time
            val dbFacadeClass = Class.forName("com.intellij.database.psi.DbPsiFacade")
            val facade = dbFacadeClass.getMethod("getInstance", Project::class.java)
                .invoke(null, project) ?: return false

            @Suppress("UNCHECKED_CAST")
            val dataSources = dbFacadeClass.getMethod("getDataSources")
                .invoke(facade) as? List<*> ?: return false
            if (dataSources.isEmpty()) return false

            val dataSource = dataSources[0] ?: return false

            // Open or reuse a Query Console for this data source
            val consoleProviderClass = Class.forName("com.intellij.database.console.JdbcConsoleProvider")
            val console = consoleProviderClass
                .getMethod("getOrCreateConsole", Project::class.java, dataSource.javaClass)
                .invoke(null, project, dataSource) ?: return false

            // Inject SQL text into the console's backing virtual file
            val vFile = console.javaClass.getMethod("getVirtualFile").invoke(console)
                ?: return false

            val docManagerClass = Class.forName("com.intellij.openapi.fileEditor.FileDocumentManager")
            val docManagerInst  = docManagerClass.getMethod("getInstance").invoke(null)
            val doc = docManagerClass
                .getMethod("getDocument", Class.forName("com.intellij.openapi.vfs.VirtualFile"))
                .invoke(docManagerInst, vFile) ?: return false

            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                doc.javaClass.getMethod("setText", CharSequence::class.java).invoke(doc, sql)
            }

            val virtualFile = vFile as com.intellij.openapi.vfs.VirtualFile
            val fileEditorManager = FileEditorManager.getInstance(project)
            fileEditorManager.openFile(virtualFile, true)

            if (execute) {
                // Best-effort: trigger "Execute" action via DataManager (reflection avoids
                // compile-time dependency on internal DataManager implementation class).
                ApplicationManager.getApplication().invokeLater {
                    try {
                        val am = ActionManager.getInstance()
                        val executeAction = am.getAction("Console.Execute.Immediately")
                            ?: am.getAction("Console.Execute")
                        if (executeAction != null) {
                            val editors = fileEditorManager.getEditors(virtualFile)
                            val editor  = editors.firstOrNull()
                            if (editor != null) {
                                // Obtain DataContext via reflection to avoid direct dependency
                                // on DataManager implementation class.
                                val dmClass = Class.forName(
                                    "com.intellij.openapi.actionSystem.DataManager"
                                )
                                val dmInst  = dmClass.getMethod("getInstance").invoke(null)
                                val ctx     = dmClass
                                    .getMethod("getDataContext", java.awt.Component::class.java)
                                    .invoke(dmInst, editor.component)
                                val event = AnActionEvent.createFromDataContext(
                                    "MyBatisSqlHelper", null,
                                    ctx as com.intellij.openapi.actionSystem.DataContext
                                )
                                executeAction.actionPerformed(event)
                            }
                        }
                    } catch (_: Exception) { /* ignore — user can press Ctrl+Enter manually */ }
                }
                showExecuteNotification(project, sql, noConsole = false)
            } else {
                showOpenNotification(project, sql, copiedOnly = false)
            }
            true
        } catch (_: ClassNotFoundException) {
            false  // DB plugin not installed
        } catch (_: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Strategy 2 — Scratch SQL file (universal)
    // ------------------------------------------------------------------

    private fun tryOpenScratchFile(project: Project, sql: String, forExecute: Boolean): Boolean {
        return try {
            // Use SQL language when the Database plugin is present; fall back to plain text.
            val sqlLanguage: Language = Language.findLanguageByID("SQL")
                ?: PlainTextLanguage.INSTANCE

            val scratchFile = ScratchRootType.getInstance().createScratchFile(
                project, "mybatis_sql.sql", sqlLanguage, sql,
                ScratchFileService.Option.create_new_always
            ) ?: return false

            FileEditorManager.getInstance(project).openFile(scratchFile, true)

            if (forExecute) showExecuteNotification(project, sql, noConsole = false)
            else            showOpenNotification(project, sql, copiedOnly = false)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------
    // Clipboard
    // ------------------------------------------------------------------

    private fun copyToClipboard(sql: String) {
        try {
            CopyPasteManager.getInstance().setContents(StringSelection(sql))
        } catch (_: Exception) { /* ignore */ }
    }

    // ------------------------------------------------------------------
    // Notifications
    // ------------------------------------------------------------------

    private fun sqlPreview(sql: String): String = buildString {
        append("<pre style='font-family:monospace;white-space:pre-wrap;'>")
        val display = if (sql.length > 500) sql.take(500) + "\n…" else sql
        append(display.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"))
        append("</pre>")
    }

    /** Shown after the 🔵 Open button is clicked. */
    private fun showOpenNotification(project: Project, sql: String, copiedOnly: Boolean) {
        val title = if (copiedOnly) "MyBatis SQL — Copied to clipboard"
                    else           "MyBatis SQL — Opened in editor"
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(title, sqlPreview(sql), NotificationType.INFORMATION)
            .notify(project)
    }

    /** Shown after the 🟢 Execute button is clicked. */
    private fun showExecuteNotification(project: Project, sql: String, noConsole: Boolean) {
        val (title, content) = if (noConsole) {
            "MyBatis SQL — Ready to Execute" to
                "SQL copied to clipboard. Open a Query Console and press <b>Ctrl+Enter</b> to run.<br>" +
                sqlPreview(sql)
        } else {
            "MyBatis SQL — Execute" to
                "SQL opened in console. Press <b>Ctrl+Enter</b> to execute.<br>" +
                sqlPreview(sql)
        }
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(title, content, NotificationType.INFORMATION)
            .notify(project)
    }
}
