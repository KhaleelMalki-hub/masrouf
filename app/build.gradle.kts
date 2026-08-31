import java.util.Properties

/**
 * Values that belong to the person, not to the program.
 *
 * The owner's name and his cards' credit limits are what let the app tell a
 * transfer to himself from a transfer to a relative, and what a card will still
 * let through from money he has. They are also facts about a named individual, and
 * this repository is public - CLAUDE.md's Privacy section already forbids a full
 * account number and a real name in a fixture, and a limit is the same kind of
 * thing with no carve-out.
 *
 * So they live in `local.properties`, which is gitignored, and reach the code as
 * BuildConfig fields. Absent - on a fresh clone, or anyone else's machine - they
 * are empty, the owner matcher matches nobody, and a card shows no ceiling. The
 * app works; it just knows less, which is the correct behaviour for a stranger's
 * checkout.
 */
private fun localValue(key: String): String {
    val file = rootProject.file("local.properties")
    if (!file.exists()) return ""
    // Read as UTF-8. java.util.Properties.load(InputStream) is defined to decode
    // ISO-8859-1, which turns every Arabic name in this file into mojibake that
    // then matches nothing - silently, because an owner matcher that matches
    // nobody looks exactly like an owner who sent no transfers.
    return Properties()
        .apply { file.reader(Charsets.UTF_8).use(::load) }
        .getProperty(key)
        .orEmpty()
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "sa.masrouf.app"
    compileSdk = 35

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        applicationId = "sa.masrouf.app"
        // 26 is the first release with java.time. :core is built on java.time
        // throughout, so this floor is what lets it be consumed unchanged instead
        // of through desugaring.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        // The app ships Arabic and English only. Without this, every other
        // translation the AndroidX libraries carry is packaged too.
        resourceConfigurations += setOf("ar", "en")

        buildConfigField("String", "OWNER_NAMES", "\"${localValue("owner.names")}\"")
        buildConfigField("String", "CARD_LIMITS", "\"${localValue("card.limits")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")
    sourceSets["test"].kotlin.srcDir("src/test/kotlin")
    sourceSets["androidTest"].kotlin.srcDir("src/androidTest/kotlin")
    // The exported schemas, so MigrationTestHelper can open a database at any
    // past version and run the real migrations forward over it.
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}

ksp {
    // Emits the schema JSON so a future migration is written against the real
    // previous schema rather than against a memory of it.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    // For AppCompatDelegate.setApplicationLocales, the per-app language API.
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    // Material Symbols, one per category. Official glyphs rather than hand-drawn
    // ones, because the app is Material 3 as Google specifies it, and R8 keeps
    // only the seventeen that are referenced.
    implementation(libs.compose.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.junit4)

    testImplementation(testFixtures(project(":core")))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
