package com.github.pop1213.mybatishelper.console

import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.openapi.project.Project

/**
 * Registers [MyBatisSqlConsoleFilter] for every console in the project.
 *
 * Declared in plugin.xml via the `consoleFilterProvider` extension point.
 */
class MyBatisSqlConsoleFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> =
        arrayOf(MyBatisSqlConsoleFilter(project))
}
