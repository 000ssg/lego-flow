// Lego Flow XTERM — XTERM terminal emulation
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-terminals-base"))
    api(project(":lego-flow-ansi"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
