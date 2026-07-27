group = "ssg"
version = property("legoFlowVersion") as String

// Centralize version constants from gradle.properties
val javaRelease: String by project
val slf4jVersion: String by project
val slf4jSimpleVersion: String by project
val junitVersion: String by project
val mockitoVersion: String by project
val assertjVersion: String by project

// Parent-only projects (no Java sources — equivalent to Maven pom-packaging)
val parentProjects = setOf("auth", "http-auth", "web", "iot", "messaging", "rpc", "database", "email", "network", "media")

subprojects {
    // Skip Java plugin for parent-only (pom-packaging) projects
    if (name in parentProjects) return@subprojects

    apply(plugin = "java-library")

    group = "ssg"
    version = rootProject.version

    // Java toolchain — tells Gradle (and IntelliJ) which JDK to use
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(javaRelease.toInt()))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(javaRelease.toInt())
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        // Parallel test execution within each module (mirrors Maven surefire config)
        maxParallelForks = 4
        // JVM args for test forks
        jvmArgs("-XX:+UseG1GC")
    }

    repositories {
        mavenCentral()
    }

    // Common test dependencies for all modules
    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:$junitVersion")
        "testImplementation"("org.assertj:assertj-core:$assertjVersion")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        "testRuntimeOnly"("org.slf4j:slf4j-simple:$slf4jSimpleVersion")
    }
}
