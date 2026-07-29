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

    // ── Infrastructure module special handling (benchmarks, interop-tests) ──

    when (name) {
        "benchmarks" -> setupBenchmarkModule()
        "interop-tests" -> setupInteropTestsModule()
    }
}

// Configure benchmarks module: JMH + dependencies (mirrors benchmarks/pom.xml)
fun Project.setupBenchmarkModule() {
    val jmhVersion = "1.37"
    
    dependencies {
        "implementation"("org.openjdk.jmh:jmh-core:$jmhVersion")
        "annotationProcessor"("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")
        // Protocol modules under test (mirrors benchmarks/pom.xml dependencies)
        "implementation"(project(":lego-flow-http"))
        "implementation"(project(":lego-flow-mqtt"))
        "implementation"(project(":lego-flow-http-auth-core"))
        "implementation"(project(":lego-flow-http-auth-basic-digest"))
        "implementation"(project(":lego-flow-redis"))
        "implementation"("org.slf4j:slf4j-simple:" + property("slf4jSimpleVersion"))
    }

    // Exclude from publish lifecycle (mirrors Maven install/deploy skip config)
    tasks.withType<Jar>().configureEach { enabled = false }
    
    // Run benchmarks: ./gradlew :benchmarks:runBenchmarks --args=".*HttpThroughputBenchmark.*"
    // Note: Uses runtimeClasspath directly — mirrors Maven shade plugin execution
    tasks.register<JavaExec>("runBenchmarks") {
        group = "verification"
        description = "Run JMH performance benchmarks (mirrors Maven shade plugin execution)"
        dependsOn("classes")
        mainClass.set("org.openjdk.jmh.Main")
        classpath = configurations["runtimeClasspath"]
    }
}

// Configure interop-tests module: protocol deps + Docker system properties (mirrors interop-tests/pom.xml)
// Skip by default — only run when Docker services are available (-DskipInteropTests=false mirrors Maven)
fun Project.setupInteropTestsModule() {
    // Protocol modules under test (mirrors interop-tests/pom.xml dependencies)
    dependencies {
        "testImplementation"(project(":lego-flow-http"))
        "testImplementation"(project(":lego-flow-mqtt"))
        "testImplementation"(project(":lego-flow-redis"))
        "testImplementation"(project(":lego-flow-postgresql"))
    }

    // Skip tests by default — mirrors Maven skipInteropTests=true property
    // Override: ./gradlew :interop-tests:test -DskipInteropTests=false
    tasks.withType<Test> {
        enabled = (findProperty("skipInteropTests") ?: "true") != "true"
        
        // System properties for Docker targets (mirrors Maven failsafe plugin config)
        systemProperty("interop.nginx.host", findProperty("interop.nginx.host") ?: "localhost")
        systemProperty("interop.nginx.port", findProperty("interop.nginx.port") ?: "8080")
        systemProperty("interop.mosquitto.host", findProperty("interop.mosquitto.host") ?: "localhost")
        systemProperty("interop.mosquitto.port", findProperty("interop.mosquitto.port") ?: "1883")
        systemProperty("interop.redis.host", findProperty("interop.redis.host") ?: "localhost")
        systemProperty("interop.redis.port", findProperty("interop.redis.port") ?: "6379")
        systemProperty("interop.pg.host", findProperty("interop.pg.host") ?: "localhost")
        systemProperty("interop.pg.port", findProperty("interop.pg.port") ?: "5432")
        systemProperty("interop.pg.user", findProperty("interop.pg.user") ?: "legoflow")
        systemProperty("interop.pg.password", findProperty("interop.pg.password") ?: "legoflow")
        systemProperty("interop.pg.db", findProperty("interop.pg.db") ?: "legoflow_test")
    }

    // Exclude from publish lifecycle (mirrors Maven install/deploy skip config)
    tasks.withType<Jar>().configureEach { enabled = false }
}
