// Lego Flow VT400 — VT400 terminal emulation
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-terminals-base"))
    api(project(":lego-flow-vt200"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
