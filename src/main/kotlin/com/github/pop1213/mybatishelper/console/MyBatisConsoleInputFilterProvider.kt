package com.github.pop1213.mybatishelper.console

import com.intellij.execution.filters.ConsoleInputFilterProvider
import com.intellij.execution.filters.InputFilter
import com.intellij.openapi.project.Project

/**
 * Registers [MyBatisConsoleInputFilter] for every console in the project.
 *
 * Declared in plugin.xml via the `consoleInputFilterProvider` extension point.
 */
class MyBatisConsoleInputFilterProvider : ConsoleInputFilterProvider {
    override fun getDefaultFilters(project: Project): Array<InputFilter> =
        arrayOf(MyBatisConsoleInputFilter())
}
