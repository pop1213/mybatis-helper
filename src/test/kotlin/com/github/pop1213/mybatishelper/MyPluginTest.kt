package com.github.pop1213.mybatishelper

import com.intellij.ide.highlighter.XmlFileType
import com.intellij.openapi.components.service
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.PsiErrorElementUtil
import com.github.pop1213.mybatishelper.services.MyProjectService

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class MyPluginTest : BasePlatformTestCase() {

    fun testXMLFile() {
        val psiFile = myFixture.configureByText(XmlFileType.INSTANCE, "<foo>bar</foo>")
        val xmlFile = assertInstanceOf(psiFile, XmlFile::class.java)

        assertFalse(PsiErrorElementUtil.hasErrors(project, xmlFile.virtualFile))

        assertNotNull(xmlFile.rootTag)

        xmlFile.rootTag?.let {
            assertEquals("foo", it.name)
            assertEquals("bar", it.value.text)
        }
    }

    fun testProjectService() {
        val projectService = project.service<MyProjectService>()

        assertNotSame(projectService.getRandomNumber(), projectService.getRandomNumber())
    }

    fun testGutterNavigation() {
        // Create the Java interface
        myFixture.configureByText(
            "UserMapper.java",
            """
            package com.example.mapper;
            
            public interface UserMapper {
                void selectByPrimaryKey();
            }
            """.trimIndent()
        )

        // Create the XML mapper
        val xmlFile = myFixture.configureByText(
            "UserMapper.xml",
            """
            <?xml version="1.0" encoding="UTF-8" ?>
            <mapper namespace="com.example.mapper.UserMapper">
                <select id="selectByPrimaryKey">
                    select * from user
                </select>
            </mapper>
            """.trimIndent()
        )

        // Find and check Java to XML navigation markers
        val javaClass = JavaPsiFacade.getInstance(project).findClass("com.example.mapper.UserMapper", GlobalSearchScope.projectScope(project))
        assertNotNull("Java mapper class should be found", javaClass)
        val javaFile = javaClass!!.containingFile
        myFixture.configureFromExistingVirtualFile(javaFile.virtualFile)
        myFixture.doHighlighting()
        var lineMarkers = myFixture.findAllGutters()
        
        assertNotEmpty(lineMarkers)
        val javaGutter = lineMarkers.firstOrNull { it.tooltipText == "Navigate to MyBatis XML statement" }
        assertNotNull("Java to XML gutter marker should be present", javaGutter)

        // Find and check XML to Java navigation markers
        myFixture.configureFromExistingVirtualFile(xmlFile.virtualFile)
        myFixture.doHighlighting()
        lineMarkers = myFixture.findAllGutters()
        
        assertNotEmpty(lineMarkers)
        val xmlGutter = lineMarkers.firstOrNull { it.tooltipText == "Navigate to Mapper interface method" }
        assertNotNull("XML to Java gutter marker should be present", xmlGutter)
    }

    override fun getTestDataPath() = "src/test/testData/rename"
}
