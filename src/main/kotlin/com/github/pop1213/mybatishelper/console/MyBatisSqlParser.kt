package com.github.pop1213.mybatishelper.console

/**
 * Utility for parsing and reconstructing MyBatis SQL statements.
 *
 * MyBatis logs SQL in two lines:
 *   ==>  Preparing: SELECT * FROM user WHERE id = ? AND name = ?
 *   ==> Parameters: 1(Integer), tom(String)
 *
 * This parser replaces each `?` with its actual parameter value.
 */
object MyBatisSqlParser {

    private val PARAM_PATTERN = Regex("""^(.*)\((\w+)\)$""")

    /**
     * Replaces `?` placeholders in [template] with the parsed values from [paramsStr].
     * @param template  the SQL template from `Preparing:` line
     * @param paramsStr the parameter list string from `Parameters:` line (may be blank)
     */
    fun buildSql(template: String, paramsStr: String): String {
        if (paramsStr.isBlank()) return template

        val params = parseParams(paramsStr)
        var paramIndex = 0
        val sb = StringBuilder(template.length + params.sumOf { it.length })

        for (c in template) {
            if (c == '?') {
                sb.append(if (paramIndex < params.size) params[paramIndex++] else "?")
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * Replaces `?` placeholders in [template] with parsed values from ShardingSphere's paramsStr.
     * ShardingSphere params are enclosed in `[...]` and comma-separated, without explicit type suffixes.
     */
    fun buildShardingSphereSql(template: String, paramsStr: String): String {
        var cleanParamsStr = paramsStr.trim()
        if (cleanParamsStr.startsWith("[")) {
            cleanParamsStr = cleanParamsStr.substring(1)
        }
        if (cleanParamsStr.endsWith("]")) {
            cleanParamsStr = cleanParamsStr.substring(0, cleanParamsStr.length - 1)
        }
        if (cleanParamsStr.isBlank()) return template

        val params = cleanParamsStr.split(",").map { part ->
            val trimmed = part.trim()
            when {
                trimmed.equals("null", ignoreCase = true) -> "NULL"
                trimmed.matches(Regex("""^-?\d+(\.\d+)?$""")) -> trimmed
                trimmed.startsWith("'") && trimmed.endsWith("'") -> trimmed
                else -> "'${trimmed.replace("'", "''")}'"
            }
        }

        var paramIndex = 0
        val sb = StringBuilder(template.length + params.sumOf { it.length })

        for (c in template) {
            if (c == '?') {
                sb.append(if (paramIndex < params.size) params[paramIndex++] else "?")
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private fun parseParams(paramsStr: String): List<String> {
        val parts = splitByTopLevelComma(paramsStr)
        return parts.map { formatPart(it.trim()) }
    }

    /**
     * Splits the parameters string by commas that are NOT inside parentheses.
     * Example: "1(Integer), foo(String), now()(Timestamp)" → ["1(Integer)", " foo(String)", " now()(Timestamp)"]
     */
    private fun splitByTopLevelComma(s: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in s.indices) {
            when (s[i]) {
                '(' -> depth++
                ')' -> depth--
                ',' -> if (depth == 0) {
                    parts.add(s.substring(start, i))
                    start = i + 1
                }
            }
        }
        parts.add(s.substring(start))
        return parts
    }

    private fun formatPart(part: String): String {
        if (part.equals("null", ignoreCase = true)) return "NULL"

        val match = PARAM_PATTERN.find(part)
        return if (match != null) {
            val value = match.groupValues[1]
            val type  = match.groupValues[2]
            formatValue(value, type)
        } else {
            // No type annotation — treat as quoted string
            "'${part.replace("'", "''")}'"
        }
    }

    private fun formatValue(value: String, type: String): String = when (type.lowercase()) {
        // Numeric types — no quotes
        "integer", "int", "long", "short", "byte",
        "double", "float", "bigdecimal", "biginteger" -> value

        // Boolean — no quotes
        "boolean" -> value.lowercase()

        // Date/time types — single-quoted
        "date", "localdate", "localdatetime", "offsetdatetime",
        "zoneddatetime", "timestamp", "time", "localtime" -> "'$value'"

        // String types and everything else — escape single quotes and add quotes
        else -> "'${value.replace("'", "''")}'"
    }
}
