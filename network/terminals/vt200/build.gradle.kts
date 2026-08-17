// Lego Flow VT200 — VT200 terminal emulation
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-terminals-base"))
    api(project(":lego-flow-vt100"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
