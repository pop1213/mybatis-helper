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

        private val SHARDING_START_REGEX = Regex(""".*ShardingSphere-SQL\s*-.*(Actual SQL|Logic SQL):\s*(.*)""")
        private val SHARDING_END_REGEX = Regex(""".*:::\s*🪄?\s*\[(.*)\]""")
    }

    @Volatile private var pendingSqlTemplate: String? = null
    @Volatile private var isBufferingShardingSphere = false
    private val pendingShardingSphereSql = StringBuilder()

    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val trimmed = line.trimEnd('\n', '\r')

        // ── MyBatis log branch ───────────────────────────────────────────────
        val preparingMatch = PREPARING_REGEX.find(trimmed)
        if (preparingMatch != null) {
            isBufferingShardingSphere = false
            pendingShardingSphereSql.setLength(0)
            pendingSqlTemplate = preparingMatch.groupValues[1].trim()
            return null
        }

        val parametersMatch = PARAMETERS_REGEX.find(trimmed)
        if (parametersMatch != null) {
            val sqlTemplate = pendingSqlTemplate ?: return null
            pendingSqlTemplate = null

            // Truncate at "<==" in case Parameters: and Columns: are flushed on the same line
            var paramsStr = parametersMatch.groupValues[1].trim().substringBefore("<==").trimEnd()
            val placeholderCount = sqlTemplate.count { it == '?' }
            if (placeholderCount > 0) {
                val pattern = """^(\s*.*?\(\w+\)(?:\s*,\s*.*?\(\w+\)){${placeholderCount - 1}})"""
                val match = Regex(pattern).find(paramsStr)
                if (match != null) {
                    paramsStr = match.groupValues[1].trim()
                }
            }
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

        // ── ShardingSphere log branch ────────────────────────────────────────
        val shardingStartMatch = SHARDING_START_REGEX.find(trimmed)
        if (shardingStartMatch != null) {
            isBufferingShardingSphere = true
            pendingShardingSphereSql.setLength(0)
            val group2 = shardingStartMatch.groupValues[2]

            val endMatch = SHARDING_END_REGEX.find(trimmed)
            if (endMatch != null) {
                isBufferingShardingSphere = false
                val paramsStr = endMatch.groupValues[1].trim()
                val sqlTemplate = group2.substringAfter(":::").substringBeforeLast(":::").trim()
                val fullSql = MyBatisSqlParser.buildShardingSphereSql(sqlTemplate, paramsStr)
                return createShardingSphereResult(line, entireLength, paramsStr, fullSql)
            } else {
                val sqlTemplateStart = group2.substringAfter(":::").trim()
                pendingShardingSphereSql.append(sqlTemplateStart)
                return null
            }
        }

        if (isBufferingShardingSphere) {
            val endMatch = SHARDING_END_REGEX.find(trimmed)
            if (endMatch != null) {
                isBufferingShardingSphere = false
                val paramsStr = endMatch.groupValues[1].trim()
                val sqlPart = trimmed.substringBeforeLast(":::").trim()
                pendingShardingSphereSql.append("\n").append(sqlPart)
                val fullSql = MyBatisSqlParser.buildShardingSphereSql(pendingShardingSphereSql.toString(), paramsStr)
                pendingShardingSphereSql.setLength(0)
                return createShardingSphereResult(line, entireLength, paramsStr, fullSql)
            } else {
                pendingShardingSphereSql.append("\n").append(trimmed)
                return null
            }
        }

        return null
    }

    private fun createShardingSphereResult(
        line: String,
        entireLength: Int,
        paramsStr: String,
        fullSql: String
    ): Filter.Result? {
        val trimmed = line.trimEnd('\n', '\r')
        val keywordPos = line.lastIndexOf(":::").takeIf { it >= 0 } ?: return null
        val lineStartOffset = entireLength - line.length
        val linkStart = lineStartOffset + keywordPos

        val afterKeyword = keywordPos + 3
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
            val paramsStartInLine = line.indexOf(paramsStr, keywordPos + 3)
            if (paramsStartInLine in 0 until trimmed.length) {
                var highlightStart = paramsStartInLine
                var highlightEnd = paramsStartInLine + paramsStr.length
                if (highlightStart > 0 && line[highlightStart - 1] == '[') {
                    highlightStart--
                }
                if (highlightEnd < line.length && line[highlightEnd] == ']') {
                    highlightEnd++
                }
                resultItems.add(Filter.ResultItem(
                    lineStartOffset + highlightStart,
                    lineStartOffset + highlightEnd,
                    HyperlinkInfo { proj -> QueryConsoleUtil.executeSql(proj, fullSql) },
                    RUN_LINK_ATTRS
                ))
            }
        }

        return Filter.Result(resultItems)
    }
}
