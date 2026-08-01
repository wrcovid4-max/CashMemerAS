plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    // Applied by :app only when a google-services.json is actually present.
    alias(libs.plugins.google.services) apply false
}

/**
 * Stops real credentials reaching GitHub.
 *
 * `local.properties.example` is a tracked template and `local.properties` is
 * gitignored. The two names sit next to each other in the project tree and are
 * very easy to mix up — putting keys in the template means they get committed
 * AND the build never reads them, so nothing works either. Fail loudly instead.
 *
 * Must sit after the plugins block: Kotlin DSL rejects statements before it.
 */
run {
    val template = rootProject.file("local.properties.example")
    if (template.exists()) {
        val offenders = template.readLines()
            .map(String::trim)
            .filter { line ->
                if (line.startsWith("#") || !line.contains("=")) return@filter false
                val value = line.substringAfter("=").trim()
                // Google API keys, and the 24-hex exchangerate-api format.
                value.startsWith("AIza") || value.matches(Regex("[0-9a-f]{24}"))
            }
            .map { it.substringBefore("=") }

        require(offenders.isEmpty()) {
            """
            |
            |  Real API keys found in local.properties.example
            |  ----------------------------------------------
            |  Keys detected: ${offenders.joinToString(", ")}
            |
            |  That file is TRACKED BY GIT — committing it publishes your keys,
            |  and the build does not read it, so the keys do nothing anyway.
            |
            |  Fix:
            |    1. Move those lines into  local.properties  (gitignored)
            |    2. Restore the placeholders in local.properties.example
            |
            """.trimMargin()
        }
    }
}
