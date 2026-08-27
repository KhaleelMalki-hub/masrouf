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
}
