// Lego Flow ANSI — ANSI X3.64 terminal emulation
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-terminals-base"))
    api(project(":lego-flow-vt100"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
