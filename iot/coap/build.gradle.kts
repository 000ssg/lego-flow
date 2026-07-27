// Lego Flow CoAP — Constrained Application Protocol for IoT
val slf4jVersion: String by project

dependencies {
    api(project(":blocks"))
    api(project(":service"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
