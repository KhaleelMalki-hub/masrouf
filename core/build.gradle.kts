import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    // The redacted real bank messages are shared with :app's tests. Exposing them
    // as test fixtures rather than retyping them there is the point: a second copy
    // of a captured sample drifts from the original, and a parser tested against a
    // drifted copy is tested against a guess.
    `java-test-fixtures`
}

// Bytecode target is 17 because the Android module consumes this artifact and the
// Android Gradle Plugin expects 17. The JDK that *runs* the build may be newer;
// no toolchain is pinned so that CI images and developer machines with a later
// JDK build this module without provisioning a second one.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

dependencies {
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }

    // BuildGatesTest reads two files that are not Kotlin sources, so Gradle would
    // otherwise call this task up to date after either of them changed - and the
    // test that exists to catch a disarmed CI check would itself go stale, passing
    // against the version it last saw. Found by breaking the build message on
    // purpose and watching the suite stay green.
    //
    // A clean CI checkout always runs it; this is for the machine where the file is
    // actually edited.
    inputs.files(
        rootProject.file(".github/workflows/ci.yml"),
        rootProject.file("settings.gradle.kts"),
    ).withPropertyName("repositoryGates").withPathSensitivity(PathSensitivity.RELATIVE)
}
