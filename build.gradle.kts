import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.19.0"
}
group = "dev.slidev"
version = "0.1.17"
// Keep the older API baseline; allow building/running against a specific 263 build.
val platformVersion = providers.gradleProperty("platformVersion").orElse("2026.2.3")
val verificationVersion = providers.gradleProperty("verificationVersion").orElse("263.7307.112")
repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}
dependencies {
    intellijPlatform {
        intellijIdea(platformVersion)
        bundledPlugin("org.intellij.plugins.markdown")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("com.intellij.modules.jcef")
        bundledModule("intellij.platform.ui.jcef")
        // Low-level org.cef API, separate from IntelliJ's JBCef wrappers.
        bundledModule("intellij.libraries.jcef")
        pluginVerifier()
    }
}
java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }
intellijPlatform {
    pluginConfiguration {
        name = "Slidev for IntelliJ"
        ideaVersion { sinceBuild = "262"; untilBuild = "263.*" }
    }
    pluginVerification {
        ides {
            current()
            create(IntelliJPlatformType.IntellijIdea, verificationVersion.get())
        }
    }
}
tasks.register<JavaExec>("coreTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.slidev.intellij.core.CoreTest"
    enableAssertions = true
}
tasks.register<JavaExec>("viewTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.slidev.intellij.SlidevViewTest"
    systemProperty("java.awt.headless", "true")
    enableAssertions = true
}
tasks.register<JavaExec>("syntaxTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.slidev.intellij.core.SyntaxTest"
    enableAssertions = true
}
tasks.register<JavaExec>("completionTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.slidev.intellij.core.CompletionTest"
    enableAssertions = true
}
tasks.register<JavaExec>("expressionTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.slidev.intellij.core.ExpressionTest"
    enableAssertions = true
}
tasks.register<JavaExec>("referencesTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.slidev.intellij.core.ReferencesTest"
    enableAssertions = true
}
tasks.register<JavaExec>("combinedFeaturesTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.slidev.intellij.core.CombinedFeaturesTest"
    enableAssertions = true
}
tasks.register<JavaExec>("previewDiagnosticsTest") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass = "dev.slidev.intellij.core.PreviewDiagnosticsTest"
    enableAssertions = true
}
tasks.check { dependsOn("coreTest", "viewTest", "syntaxTest", "completionTest", "expressionTest", "referencesTest", "combinedFeaturesTest", "previewDiagnosticsTest") }
tasks.test {
    // CoreTest is a standalone main-based harness, not a JUnit test class.
    // Keep its checks mandatory even when invoking the test task directly.
    dependsOn("coreTest", "viewTest", "syntaxTest", "completionTest", "expressionTest", "referencesTest", "combinedFeaturesTest", "previewDiagnosticsTest")
    failOnNoDiscoveredTests.set(false)
}
