// Lego Flow VT500 — VT500 terminal emulation
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-terminals-base"))
    api(project(":lego-flow-vt400"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
