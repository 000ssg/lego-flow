import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.api.plugins.JavaPluginExtension

group = "ssg"
version = property("legoFlowVersion") as String

// Centralize version constants from gradle.properties (using non-deprecated API for Gradle 9.x)
val javaRelease = project.property("javaRelease") as String
val slf4jVersion = project.property("slf4jVersion") as String
val slf4jSimpleVersion = project.property("slf4jSimpleVersion") as String
val junitVersion = project.property("junitVersion") as String
val mockitoVersion = project.property("mockitoVersion") as String
val assertjVersion = project.property("assertjVersion") as String

// Parent-only projects (no Java sources — equivalent to Maven pom-packaging)
val parentProjects = setOf("auth", "http-auth", "web", "iot", "messaging", "rpc", "database", "email", "network", "media")

repositories {
    mavenCentral()
}

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

    // ── Test configuration ─────────────────────────────────────────────
    // Mirrors Maven surefire configuration.

    tasks.withType<Test> {
        useJUnitPlatform()

        // ── Disable parallel test execution within each module.
        //    Many tests use Thread.sleep() and shared network resources
        //    (servers, sockets) that aren't safe for parallel execution.
        //    Re-enable only after all timing tests are made parallel-safe
        //    with proper synchronization or resource isolation.
        //    Mirrors Maven surefire <parallel>none</parallel>. ──────────
        maxParallelForks = 1

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

// ── JaCoCo coverage configuration ─────────────────────────────────────
// Gradle 9.x removed the built-in jacoco plugin.  This section provides
// equivalent functionality using the org.jacoco:org.jacoco.agent artifact.
//
// Enable with: ./gradlew test -PenableCoverage=true
// After tests, .exec files will be in each module's build/jacoco/ directory.
// For full XML/HTML reports, Maven is still needed (jacoco-coverage profile).

val enableCoverage = project.findProperty("enableCoverage")?.toString() == "true"

if (enableCoverage) {
    val jacocoAgentCfg = configurations.create("jacocoAgent")
    dependencies.add("jacocoAgent", "org.jacoco:org.jacoco.agent:0.8.14:runtime")
    
    // Resolve agent during configuration phase (safe — no parallel execution at this point)
    val agentJar = jacocoAgentCfg.files.firstOrNull()
        ?: run {
            logger.warn("JaCoCo agent not found; coverage instrumentation will be skipped")
            null
        }

    if (agentJar != null) {
        subprojects.forEach { subproject ->
            if (subproject.name !in parentProjects) {
                subproject.tasks.withType<Test> {
                    val destFile = "${subproject.layout.buildDirectory.get().asFile}/jacoco/test.exec"
                    jvmArgs("-javaagent:${agentJar.absolutePath}=includes=ssg.legoflow.**,output=file,destfile=${destFile}")
                }
            }
        }
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
// Skip by default — only run when Docker services are available (-PskipInteropTests=false mirrors Maven)
fun Project.setupInteropTestsModule() {
    // Protocol modules under test (mirrors interop-tests/pom.xml dependencies)
    dependencies {
        "testImplementation"(project(":lego-flow-http"))
        "testImplementation"(project(":lego-flow-mqtt"))
        "testImplementation"(project(":lego-flow-redis"))
        "testImplementation"(project(":lego-flow-postgresql"))
    }

    // Skip tests by default — mirrors Maven skipInteropTests=true property
    // Override: ./gradlew :interop-tests:test -PskipInteropTests=false
    tasks.withType<Test> {
        // Check both Gradle project properties (-PskipInteropTests=false) and JVM system properties
        // (-DskipInteropTests=false) for Maven command-line parity
        val skipValue = findProperty("skipInteropTests") 
            ?: System.getProperty("skipInteropTests")
            ?: "true"
        enabled = skipValue != "true"
        
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

// ── JaCoCo Aggregate Report (Phase 3: replace Maven jacoco-maven-plugin) ───
// Registers JacocoAggregateReportTask from buildSrc to produce HTML + XML reports.
// Run: ./gradlew jacocoAggregateReport --info for verbose output

tasks.register("jacocoAggregateReport", JacocoAggregateReportTask::class) {
    val execDirPaths = subprojects
        .filter { it.plugins.hasPlugin("java-library") || it.plugins.hasPlugin("java") }
        .map { it.buildDir.resolve("jacoco").absolutePath }
    
    setExecDirPaths(execDirPaths.toList())
    outputDir = layout.buildDirectory.dir("jacoco/aggregate").get().asFile
    
    // Always run - input dirs are stable but .exec file contents change per build
    outputs.upToDateWhen { false }
    
    // Depend on all test tasks so .exec files are generated first
    subprojects.forEach { subproject ->
        if (subproject.plugins.hasPlugin("java-library") || subproject.plugins.hasPlugin("java")) {
            dependsOn(subproject.tasks.named("test"))
        }
    }
}

// Copy reports to Maven-compatible path for CI artifact upload compatibility
tasks.register<Copy>("copyJacocoForCI") {
    description = "Copy JaCoCo reports to target/site/jacoco/jacoco-aggregate/ for CI compatibility"
    group = "verification"
    
    from(layout.buildDirectory.dir("jacoco/aggregate/html"))
    into(projectDir.resolve("target/site/jacoco/jacoco-aggregate"))
    dependsOn("jacocoAggregateReport")
}

// ── JaCoCo Agent Configuration for Test Instrumentation ────────────────────────
// Adds -javaagent to all test tasks so .exec files are generated during testing.
// Mirrors Maven jacoco-maven-plugin prepare-agent goal.

val jacocoAgentVersion = "0.8.14"
val jacocoAgent by configurations.creating {
    isTransitive = false
}
dependencies.add(jacocoAgent.name, "org.jacoco:org.jacoco.agent:$jacocoAgentVersion:runtime")

subprojects.forEach { subproject ->
    if (subproject.plugins.hasPlugin("java-library") || subproject.plugins.hasPlugin("java")) {
        subproject.tasks.withType<Test> {
            val agentJar = jacocoAgent.files.firstOrNull()
            if (agentJar != null) {
                jvmArgs("-javaagent:${agentJar.absolutePath}=includes=ssg.legoflow.**,output=file,destfile=${subproject.layout.buildDirectory.get().asFile}/jacoco/test.exec")
            }
        }
    }
}
