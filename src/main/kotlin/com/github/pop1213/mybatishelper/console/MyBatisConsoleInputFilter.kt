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
        /** Captures the `Parameters:` prefix and everything after it as two groups. */
        private val PARAMETERS_LINE = Regex("""(.*=+>\s*Parameters:)(\s*.*)""")

        private const val MAGIC_WAND = " 🪄"
    }

    override fun applyFilter(
        text: String,
        contentType: ConsoleViewContentType
    ): List<Pair<String, ConsoleViewContentType>>? {
        val match = PARAMETERS_LINE.find(text) ?: return null
        // Insert emoji between "Parameters:" and the rest of the line
        val modified = "${match.groupValues[1]}$MAGIC_WAND${match.groupValues[2]}"
        return listOf(Pair.create(modified, contentType))
    }
}
