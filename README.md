# tiny-mybatis-helper

A lightweight, high-performance IntelliJ IDEA plugin designed to simplify MyBatis development — from navigating between Mapper interfaces and XML files, to reconstructing and executing SQL directly from the Run/Debug console.

## Features

### 🗺️ Mapper Navigation
* 🚀 **Java Mapper ➔ XML SQL**: Jump from a Mapper Java interface method directly to its corresponding statement tag (`<select>`, `<insert>`, `<update>`, `<delete>`) in the Mapper XML file.
* ↩️ **XML SQL ➔ Java Mapper**: Jump back from XML statement tags to the Java interface method declarations.
* ⚡ **High Performance**: Optimized using IntelliJ index-based word search to ensure instant navigation even in large project codebases.

### 🪄 Console SQL Helper
When MyBatis logs SQL to the Run/Debug console, the plugin automatically:

1. **Inserts a `🪄` emoji** right after the `Parameters:` marker for quick visual identification.
2. **Annotates the `Parameters: 🪄` text** as a clickable blue hyperlink — click to reconstruct the full SQL (with parameters substituted) and open it in a Scratch editor / Query Console. The SQL is also copied to the clipboard.
3. **Annotates the parameter values** (in green bold) as a second clickable zone — click to reconstruct the SQL and attempt to execute it directly in a Database Query Console.

**Example console output:**
```
==>  Preparing: SELECT * FROM user WHERE id = ? AND name = ?
==>  Parameters: 🪄 1(Integer), tom(String)
                 ^^^^^^^^^^^^^^^^  ← blue link  → open SQL
                                   ^^^^^^^^^^^^^  ← green link → execute SQL
```

**Reconstructed SQL:**
```sql
SELECT * FROM user WHERE id = 1 AND name = 'tom'
```

**Supported parameter types:**

| Type | Output |
|---|---|
| `Integer`, `Long`, `Double`, `Float`, `BigDecimal` | bare number |
| `Boolean` | `true` / `false` |
| `String`, `Character` | `'value'` (single-quoted) |
| `Date`, `LocalDate`, `LocalDateTime`, `Timestamp` … | `'value'` |
| `null` | `NULL` |

## Gutter Navigation Icons

* **Interface Method → XML Statement**: Displayed next to Java Mapper interface methods. Click to jump to the XML definition.
* **XML Statement → Interface Method**: Displayed next to SQL statement tags inside MyBatis XML files. Click to jump to the Java interface method.

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
