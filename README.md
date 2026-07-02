# mybatis-helper

A lightweight, high-performance IntelliJ IDEA plugin designed to simplify navigating between MyBatis Mapper Java interfaces and their corresponding XML mapping files.

## Features

* 🚀 **Java Mapper ➔ XML SQL**: Jump from a Mapper Java interface method directly to its corresponding statement tag (`<select>`, `<insert>`, `<update>`, `<delete>`) in the Mapper XML file.
* ↩️ **XML SQL ➔ Java Mapper**: Jump back from XML statement tags to the Java interface method declarations.
* ⚡ **High Performance**: Optimized using IntelliJ index-based word search to ensure instant navigation even in large project codebases.
* 🛠️ **Seamless Integration**: Standard gutter icons integrated directly into the editor.

## Gutter Navigation Icons

* **Interface Method to XML Statement**: Displayed next to Java Mapper interface methods. Click to jump to the XML definition.
* **XML Statement to Interface Method**: Displayed next to SQL statement tags inside MyBatis XML files. Click to jump to the Java interface method.

## Installation

### Method 1: Install from Disk
1. Download the latest release package (ZIP format) from the [releases](https://github.com/pop1213/mybatis-helper/releases).
2. Open IntelliJ IDEA.
3. Go to **Settings/Preferences** ➔ **Plugins**.
4. Click the gear icon (**⚙️**) on the top right and select **Install Plugin from Disk...**.
5. Select the downloaded ZIP file and click OK.
6. Restart your IDE.

---
Plugin based on the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template).
