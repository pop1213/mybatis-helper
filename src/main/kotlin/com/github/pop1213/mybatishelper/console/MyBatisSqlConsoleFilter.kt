package com.github.pop1213.mybatishelper.console

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import java.awt.Color
import java.awt.Font

class MyBatisSqlConsoleFilter(private val project: Project) : Filter {

    companion object {
        private val PREPARING_REGEX = Regex(""".*=+>\s*Preparing:\s+(.+)""")
        private val PARAMETERS_REGEX = Regex(""".*=+>\s*Parameters:\s*🪄?\s*(.*)""")
        private const val LINK_MARKER = "Parameters:"
        private val RUN_LINK_ATTRS = TextAttributes(
            JBColor(Color(0x2E, 0x7D, 0x32), Color(0x80, 0xCB, 0x4F)),
            null, null, null, Font.BOLD
        )
    }

    @Volatile private var pendingSqlTemplate: String? = null

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val trimmed = line.trimEnd('\n', '\r')

        val preparingMatch = PREPARING_REGEX.find(trimmed)
        if (preparingMatch != null) {
            pendingSqlTemplate = preparingMatch.groupValues[1].trim()
            return null
        }

        val parametersMatch = PARAMETERS_REGEX.find(trimmed) ?: return null
        val sqlTemplate = pendingSqlTemplate ?: return null
        pendingSqlTemplate = null

        // Truncate at "<==" in case Parameters: and Columns: are flushed on the same line
        val paramsStr = parametersMatch.groupValues[1].trim().substringBefore("<==").trimEnd()
        val fullSql = MyBatisSqlParser.buildSql(sqlTemplate, paramsStr)

        val keywordPos = line.indexOf(LINK_MARKER).takeIf { it >= 0 } ?: return null
        val lineStartOffset = entireLength - line.length
        val linkStart = lineStartOffset + keywordPos

        // Extend the hyperlink to cover "Parameters: 🪄" when the emoji is present
        val afterKeyword = keywordPos + LINK_MARKER.length
        val emojiSuffix = " 🪄"
        val linkEnd = lineStartOffset + if (line.startsWith(emojiSuffix, afterKeyword)) {
            afterKeyword + emojiSuffix.length
        } else {
            afterKeyword
        }

        val resultItems = mutableListOf(
            Filter.ResultItem(linkStart, linkEnd,
                HyperlinkInfo { proj -> QueryConsoleUtil.openSql(proj, fullSql) })
        )

        if (paramsStr.isNotBlank()) {
            val paramsStartInLine = line.indexOf(paramsStr, keywordPos + LINK_MARKER.length)
            if (paramsStartInLine in 0 until trimmed.length) {
                resultItems.add(Filter.ResultItem(
                    lineStartOffset + paramsStartInLine,
                    lineStartOffset + paramsStartInLine + paramsStr.length,
                    HyperlinkInfo { proj -> QueryConsoleUtil.executeSql(proj, fullSql) },
                    RUN_LINK_ATTRS
                ))
            }
        }

        return Filter.Result(resultItems)
    }
}
