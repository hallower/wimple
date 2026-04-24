package kr.blogspot.charlie0301.wimple

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import java.io.IOException

/**
 * Static attribution screen listing every third-party open-source library bundled in the APK
 * along with copyright and license info, followed by the full verbatim text of each referenced
 * license loaded from assets/licenses/.
 *
 * Attribution is required by the licenses themselves (Apache 2.0 §4(c,d), CDDL §3.3), by
 * Google Play policy, and gives users a traceable way to audit what ships inside the app.
 *
 * Two-column layout inside the TextView using monospace font so the fixed-width Apache 2.0
 * license art (indented legal clauses, the classic signature block at the top) renders
 * correctly.
 */
class OpenSourceLicensesActivity : AppCompatActivity() {

    private data class Library(
        val name: String,
        val version: String,
        val copyright: String,
        val licenseLabel: String,
        val licenseAssetFile: String
    )

    /**
     * Production dependencies from app/build.gradle. Test-only deps (JUnit) are excluded —
     * they don't ship in the APK so no attribution is required at runtime.
     */
    private val libraries = listOf(
        Library("AndroidX Core", "1.3.1",
            "Copyright (C) The Android Open Source Project",
            "Apache License 2.0", "licenses/apache-2.0.txt"),
        Library("AndroidX AppCompat", "1.0.2",
            "Copyright (C) The Android Open Source Project",
            "Apache License 2.0", "licenses/apache-2.0.txt"),
        Library("AndroidX Preference", "1.2.0",
            "Copyright (C) The Android Open Source Project",
            "Apache License 2.0", "licenses/apache-2.0.txt"),
        Library("AndroidX Biometric", "1.1.0",
            "Copyright (C) The Android Open Source Project",
            "Apache License 2.0", "licenses/apache-2.0.txt"),
        Library("Material Components for Android", "1.0.0",
            "Copyright (C) Google LLC",
            "Apache License 2.0", "licenses/apache-2.0.txt"),
        Library("Kotlin Standard Library", "2.2.10",
            "Copyright (C) JetBrains s.r.o. and Kotlin Programming Language contributors",
            "Apache License 2.0", "licenses/apache-2.0.txt"),
        Library("MPAndroidChart", "v3.0.1",
            "Copyright (C) Philipp Jahoda",
            "Apache License 2.0", "licenses/apache-2.0.txt"),
        Library("Jersey Client", "1.19.3",
            "Copyright (C) Oracle and/or its affiliates",
            "CDDL 1.1 + GPL 2 w/ Classpath Exception", "licenses/cddl-1.1-gplv2-classpath.txt")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_opensource_licenses)

        setSupportActionBar(findViewById<Toolbar>(R.id.toolbar))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setTitle(R.string.opensource_licenses_title)
        }

        findViewById<TextView>(R.id.licenses_text).text = buildLicensesText()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun buildLicensesText(): String {
        val sb = StringBuilder()
        sb.append(getString(R.string.opensource_licenses_intro)).append("\n\n")
        sb.append("=".repeat(60)).append('\n')
        sb.append(getString(R.string.opensource_licenses_libraries_header)).append('\n')
        sb.append("=".repeat(60)).append("\n\n")

        for ((i, lib) in libraries.withIndex()) {
            sb.append("${i + 1}. ${lib.name}  (v${lib.version})\n")
            sb.append("   ${lib.copyright}\n")
            sb.append("   Licensed under ${lib.licenseLabel}\n\n")
        }

        // Append each unique license text once. We de-dupe via asset filename so the same
        // Apache 2.0 body isn't printed seven times.
        val uniqueLicenses = libraries
            .map { it.licenseLabel to it.licenseAssetFile }
            .distinctBy { it.second }
        for ((label, assetFile) in uniqueLicenses) {
            sb.append("\n")
            sb.append("=".repeat(60)).append('\n')
            sb.append(label).append('\n')
            sb.append("=".repeat(60)).append("\n\n")
            sb.append(readAssetSafely(assetFile)).append("\n\n")
        }
        return sb.toString()
    }

    private fun readAssetSafely(path: String): String = try {
        assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (e: IOException) {
        "[failed to load $path: ${e.message}]"
    }
}
