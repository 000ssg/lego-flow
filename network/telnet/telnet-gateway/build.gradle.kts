// Lego Flow Telnet — Terminal gateway (protocol + emulation)
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-telnet-base"))
    api(project(":lego-flow-telnet-negotiation"))
    api(project(":lego-flow-terminals-base"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation(project(":lego-flow-vt100"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
}
