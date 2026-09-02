// ACL module: access control, certificates, SSH keys, SASL utilities
// Parent build.gradle.kts handles toolchain, test config, and common deps

dependencies {
    // SLF4J (from parent, but explicit for clarity)
    implementation("org.slf4j:slf4j-api:1.7.36")

    // BouncyCastle for certificate generation (required on JDK 25+ where sun.security internals are sealed)
    implementation("org.bouncycastle:bcprov-jdk18on:1.80")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.80")

    // Config loaders
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")
    implementation("org.yaml:snakeyaml:2.3")

    // XML support (JAXB)
    implementation("jakarta.xml.bind:jakarta.xml.bind-api:4.0.2")
    implementation("org.glassfish.jaxb:jaxb-runtime:4.0.5")

    // Test dependencies
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.9")
}

tasks.withType<Test> {
    // Allow reflection into sun.security for CertificateFactory (JDK 25 sealed modules)
    jvmArgs(
        "--add-opens=java.base/sun.security.x509=ALL-UNNAMED",
        "--add-opens=java.base/sun.security=ALL-UNNAMED",
        "--add-opens=java.base/java.security=ALL-UNNAMED"
    )
}
