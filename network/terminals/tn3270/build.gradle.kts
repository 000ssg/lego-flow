// Lego Flow TN3270 — TN3270 (IBM 3270) terminal emulation
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-terminals-base"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
