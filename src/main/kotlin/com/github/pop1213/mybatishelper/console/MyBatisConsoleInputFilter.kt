package com.github.pop1213.mybatishelper.console

import com.intellij.execution.filters.InputFilter
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.util.Pair

/**
 * An [InputFilter] that inserts a 🪄 emoji directly into the console text immediately
 * after the `Parameters:` marker produced by MyBatis logging.
 *
 * Because this is an *input* filter it runs **before** the text is written to the
 * console document, so the emoji becomes a real character in the console line and is
 * visible to subsequent [com.intellij.execution.filters.Filter] passes (including
 * [MyBatisSqlConsoleFilter] which still correctly strips it out before SQL parsing).
 *
 * Example transformation:
 * ```
 * ==>  Parameters: 1(Integer), tom(String)
 *               ↓
 * ==>  Parameters: 🪄 1(Integer), tom(String)
 * ```
 */
class MyBatisConsoleInputFilter : InputFilter {

    companion object {
        private val PARAMETERS_LINE = Regex("""(.*=+>\s*Parameters:)(\s*.*)""")
        private val SHARDING_LINE = Regex("""(.*:::)\s*(\[.*\])""")
        private const val MAGIC_WAND = " 🪄"
    }

    override fun applyFilter(
        text: String,
        contentType: ConsoleViewContentType
    ): List<Pair<String, ConsoleViewContentType>>? {
        val mybatisMatch = PARAMETERS_LINE.find(text)
        if (mybatisMatch != null) {
            val modified = "${mybatisMatch.groupValues[1]}$MAGIC_WAND${mybatisMatch.groupValues[2]}"
            return listOf(Pair.create(modified, contentType))
        }

        val shardingMatch = SHARDING_LINE.find(text)
        if (shardingMatch != null) {
            val modified = "${shardingMatch.groupValues[1]}$MAGIC_WAND ${shardingMatch.groupValues[2]}"
            return listOf(Pair.create(modified, contentType))
        }

        return null
    }
}
