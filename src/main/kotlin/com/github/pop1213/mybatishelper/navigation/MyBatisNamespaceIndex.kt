package com.github.pop1213.mybatishelper.navigation

import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.io.EnumeratorStringDescriptor
import com.intellij.util.io.KeyDescriptor

/**
 * High-performance file-based index to map MyBatis XML namespace values to their containing files.
 * This avoids expensive word searches and full scans inside line marker providers.
 */
class MyBatisNamespaceIndex : ScalarIndexExtension<String>() {

    companion object {
        val INDEX_ID: ID<String, Void> = ID.create("com.github.pop1213.mybatishelper.namespaceIndex")
        
        // Regex pattern to extract the namespace attribute from the root mapper element
        private val NAMESPACE_PATTERN = Regex("""<mapper\s+namespace=["']([^"']+)["']""")
    }

    override fun getName(): ID<String, Void> = INDEX_ID

    override fun getIndexer(): DataIndexer<String, Void, FileContent> {
        return DataIndexer { fileContent ->
            val text = fileContent.contentAsText
            val match = NAMESPACE_PATTERN.find(text)
            if (match != null) {
                mapOf(match.groupValues[1] to null)
            } else {
                emptyMap()
            }
        }
    }

    override fun getKeyDescriptor(): KeyDescriptor<String> = EnumeratorStringDescriptor.INSTANCE

    override fun getInputFilter(): FileBasedIndex.InputFilter {
        return FileBasedIndex.InputFilter { file ->
            file.name.endsWith(".xml", ignoreCase = true)
        }
    }

    override fun getVersion(): Int = 2

    override fun dependsOnFileContent(): Boolean = true
}
