// Lego Flow Terminals — Core terminal emulation abstractions
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-blocks"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
