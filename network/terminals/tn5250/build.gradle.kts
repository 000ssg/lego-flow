// Lego Flow TN5250 — TN5250 (IBM 5250) terminal emulation
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-terminals-base"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
