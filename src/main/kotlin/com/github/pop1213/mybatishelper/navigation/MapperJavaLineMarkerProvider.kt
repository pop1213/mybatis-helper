package com.github.pop1213.mybatishelper.navigation

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiIdentifier
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

class MapperJavaLineMarkerProvider : RelatedItemLineMarkerProvider() {

    private val statementTags = setOf("select", "insert", "update", "delete", "statement")

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        // We only attach gutter icon to the method name identifier to avoid performance issues
        if (element !is PsiIdentifier) return
        val method = element.parent as? PsiMethod ?: return
        if (element != method.nameIdentifier) return

        val psiClass = method.containingClass ?: return
        if (!psiClass.isInterface) return

        val qualifiedName = psiClass.qualifiedName ?: return
        val shortName = psiClass.name ?: return
        val methodName = method.name
        val project = element.project

        val targets = mutableListOf<XmlTag>()

        val scope = GlobalSearchScope.projectScope(project)
        val files = com.intellij.util.indexing.FileBasedIndex.getInstance().getContainingFiles(
            MyBatisNamespaceIndex.INDEX_ID,
            qualifiedName,
            scope
        )

        val psiManager = com.intellij.psi.PsiManager.getInstance(project)
        for (virtualFile in files) {
            val psiFile = psiManager.findFile(virtualFile) as? XmlFile ?: continue
            val rootTag = psiFile.rootTag
            if (rootTag != null && rootTag.name == "mapper") {
                for (subTag in rootTag.subTags) {
                    if (subTag.name in statementTags && subTag.getAttributeValue("id") == methodName) {
                        targets.add(subTag)
                    }
                }
            }
        }

        // Fallback for tests or unindexed/new files: check files by name (e.g. UserMapper.xml)
        if (targets.isEmpty()) {
            val filesByName = FilenameIndex.getFilesByName(project, "$shortName.xml", scope)
            for (file in filesByName) {
                if (file is XmlFile) {
                    val rootTag = file.rootTag
                    if (rootTag != null && rootTag.name == "mapper") {
                        val namespace = rootTag.getAttributeValue("namespace")
                        if (namespace == qualifiedName) {
                            for (subTag in rootTag.subTags) {
                                if (subTag.name in statementTags && subTag.getAttributeValue("id") == methodName) {
                                    targets.add(subTag)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (targets.isNotEmpty()) {
            val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
                .setTargets(targets)
                .setTooltipText("Navigate to MyBatis XML statement")
            result.add(builder.createLineMarkerInfo(element))
        }
    }
}
