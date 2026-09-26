import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.gms.google-services")
}

// Google Maps key from local secrets (never committed):
// android/secrets.properties -> MAPS_API_KEY=...
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Release signing config, also local-only (android/keystore.properties).
// Without it, `assembleDebug` still works — only release builds need it.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.farmsy.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.farmsy.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 32
        versionName = "1.0.5"

        manifestPlaceholders["MAPS_API_KEY"] = secrets.getProperty("MAPS_API_KEY", "")
        buildConfigField("String", "REVENUECAT_KEY", "\"${secrets.getProperty("REVENUECAT_KEY", "")}\"")
        buildConfigField("String", "SENTRY_DSN", "\"${secrets.getProperty("SENTRY_DSN", "")}\"")
        buildConfigField("String", "POSTHOG_KEY", "\"${secrets.getProperty("POSTHOG_KEY", "")}\"")
        buildConfigField("String", "POSTHOG_HOST", "\"${secrets.getProperty("POSTHOG_HOST", "https://eu.i.posthog.com")}\"")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }

            // Play warns that the bundle ships native code without debug symbols.
            // Deliberately left alone: every .so in here belongs to someone else
            // (libsentry, androidx.graphics.path, datastore) — we write no native
            // code — so symbolicating them would only give us readable stack traces
            // inside libraries we can't fix. Real crashes come through Sentry with a
            // proper trace already. Turning this on means installing the NDK just to
            // strip symbols from other people's binaries.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Supabase (auth + database) — same backend as iOS
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.3"))
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-okhttp:3.0.3")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Images
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Maps + location
    implementation("com.google.maps.android:maps-compose:6.2.1")
    implementation("com.google.android.gms:play-services-maps:19.0.0")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    // (S19 trip-origin search uses Photon over the shared Ktor client — no SDK/key.
    //  The Google Places SDK was removed: never referenced or initialised, it shipped
    //  unused drawables, pulled Volley transitively, and injected four autocomplete
    //  activities into the merged manifest. Google Places Autocomplete (New) remains
    //  the exact-iOS-parity path — DEFERRED, see PORT_NOTES.)

    // In-app purchases (Google Play Billing via RevenueCat).
    // Must stay on a version that bundles Play Billing 8+: Play's "one-time product
    // with purchase options" model (which our lifetime uses) is invisible to Billing 7,
    // so an older SDK silently drops the lifetime package from the offering.
    implementation("com.revenuecat.purchases:purchases:10.13.0")

    // Push (alerts about products and followed farms). The token goes to
    // POST /api/profile/push-token; the server sends through FCM HTTP v1.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Crash reporting + product analytics
    implementation("io.sentry:sentry-android:7.20.0")
    implementation("com.posthog:posthog-android:3.11.1")

    // Local JVM unit tests. FarmFilters is pure Kotlin (java.util.Calendar/TimeZone,
    // no Android framework), so its parser runs on the JVM with no emulator — which is
    // the whole point: the en-dash time bug shipped because Android had no such target.
    testImplementation("junit:junit:4.13.2")
}

// ── Mojibake guard ───────────────────────────────────────────────────────────
// Fails the build if any locale strings file contains double-encoded UTF-8
// (e.g. "café" pasted through a Latin-1-assuming step becomes "cafÃ©"). Exactly
// this garbled 123 lines of values-fr/strings_l10n.xml and shipped French users
// unreadable text until it was repaired; the paste path is still in use, so this
// catches a re-introduction at build time.
//
// Signature (raw bytes): a byte C3 followed by 82..B4 — a UTF-8 char U+00C2..U+00F4,
// which is precisely a real UTF-8 LEAD byte (0xC2..0xF4) reinterpreted as Latin-1
// and re-encoded (Ã Â Å â ð …) — IMMEDIATELY followed by C2 then 80..BF, the
// re-encoded CONTINUATION byte. Correct UTF-8 never places those two adjacent
// (an accented letter is followed by a space or another letter, not by a raw
// Latin-1 punctuation byte), so this flags every mangled glyph — accents, the
// Œ/œ ligatures, ellipsis, curly quotes, em-dashes and emoji (which mangle to
// Å/â/ð, not Ã, and a marker-only grep misses) — with zero false positives on
// legitimate à/è/é/ê/î/À, MIDDLE DOT ·, ©, em-dashes or 🌱/🧺. Verified: 100%
// recall on the pre-repair content, 0 hits on all nine current string files.
val mojibakeResDir = file("src/main/res")
val mojibakeRepoRoot = rootDir
val checkStringEncoding =
    tasks.register("checkStringEncoding") {
        group = "verification"
        description =
            "Fails on double-encoded UTF-8 (mojibake) in res/values*/strings*.xml."
        inputs.dir(mojibakeResDir)
        doLast {
            val nameRe = Regex("""strings.*\.xml""")
            val problems = mutableListOf<String>()
            mojibakeResDir.walkTopDown()
                .filter {
                    it.isFile &&
                        it.parentFile.name.startsWith("values") &&
                        nameRe.matches(it.name)
                }
                .sortedBy { it.path }
                .forEach { f ->
                    val bytes = f.readBytes()
                    val n = bytes.size
                    var line = 1
                    var lastFlaggedLine = -1
                    var i = 0
                    while (i < n) {
                        val b = bytes[i].toInt() and 0xFF
                        if (b == 0x0A) line++
                        if (b == 0xC3 && i + 3 < n) {
                            val b1 = bytes[i + 1].toInt() and 0xFF
                            val b2 = bytes[i + 2].toInt() and 0xFF
                            val b3 = bytes[i + 3].toInt() and 0xFF
                            if (b1 in 0x82..0xB4 && b2 == 0xC2 && b3 in 0x80..0xBF &&
                                line != lastFlaggedLine
                            ) {
                                problems += "${f.relativeTo(mojibakeRepoRoot)}:$line"
                                lastFlaggedLine = line
                            }
                        }
                        i++
                    }
                }
            if (problems.isNotEmpty()) {
                throw GradleException(
                    buildString {
                        appendLine(
                            "Double-encoded UTF-8 (mojibake) in locale strings — " +
                                "these ship garbled text to users.",
                        )
                        appendLine(
                            "A UTF-8 string was pasted through a Latin-1-assuming step. " +
                                "Re-encode the offending file(s) as UTF-8.",
                        )
                        appendLine("Offending lines:")
                        problems.forEach { appendLine("  $it") }
                    },
                )
            }
        }
    }

// Wire into preBuild so every build (assembleDebug / bundleRelease / CI) runs it
// before compilation — the guard cannot be skipped by forgetting a separate step.
tasks.named("preBuild") { dependsOn(checkStringEncoding) }
