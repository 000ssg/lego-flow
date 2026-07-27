// Lego Flow HTTP/2 — HTTP/2 protocol implementation
val slf4jVersion: String by project

dependencies {
    api(project(":blocks"))
    api(project(":service"))
    api(project(":web:http"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
