rootProject.name = "masrouf"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// :core is pure Kotlin/JVM — it builds and tests anywhere a JDK exists,
// including CI containers with no Android SDK. That is deliberate: the
// parsing and money logic is the part that must be proven correct.
include(":core")

// :app needs the Android SDK. Including it unconditionally makes the whole
// build fail to CONFIGURE on a machine without one, which would take :core
// down with it. So it is included only when an SDK is actually present.
val hasAndroidSdk =
    file("local.properties").takeIf { it.exists() }
        ?.readLines()
        ?.any { it.trimStart().startsWith("sdk.dir=") } == true ||
        System.getenv("ANDROID_HOME") != null ||
        System.getenv("ANDROID_SDK_ROOT") != null

if (hasAndroidSdk) {
    include(":app")
} else {
    logger.lifecycle("[masrouf] No Android SDK found - skipping :app. Building :core only.")
}
