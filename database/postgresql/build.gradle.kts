// Lego Flow PostgreSQL — PostgreSQL v3 Wire Protocol
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-blocks"))
    api(project(":lego-flow-service"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
