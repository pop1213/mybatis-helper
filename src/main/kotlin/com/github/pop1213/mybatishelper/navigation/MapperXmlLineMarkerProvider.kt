package com.github.pop1213.mybatishelper.navigation

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlTokenType
import com.intellij.psi.xml.XmlTag

class MapperXmlLineMarkerProvider : RelatedItemLineMarkerProvider() {

    private val statementTags = setOf("select", "insert", "update", "delete", "statement")

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        val tag = element.parent as? XmlTag ?: return
        if (tag.name !in statementTags) return

        // Attach the gutter icon to the start tag name token (which is a leaf element)
        val nameNode = tag.node.findChildByType(XmlTokenType.XML_NAME)
        if (nameNode?.psi != element) return

        val parentTag = tag.parent as? XmlTag ?: return
        if (parentTag.name != "mapper") return

        val namespace = parentTag.getAttributeValue("namespace") ?: return
        if (namespace.isEmpty()) return

        val id = tag.getAttributeValue("id") ?: return
        if (id.isEmpty()) return

        val project = element.project
        // Find the Java class matching the namespace (this also finds Kotlin interfaces)
        val psiClass = JavaPsiFacade.getInstance(project).findClass(namespace, GlobalSearchScope.projectScope(project)) ?: return

        val methods = psiClass.findMethodsByName(id, true)
        if (methods.isEmpty()) return

        val builder = NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementingMethod)
            .setTargets(methods.toList())
            .setTooltipText("Navigate to Mapper interface method")
        
        result.add(builder.createLineMarkerInfo(element))
    }
}
