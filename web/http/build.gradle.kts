// Lego Flow HTTP — HTTP/HTTPS protocol implementation
val slf4jVersion: String by project
val mockitoVersion: String by project

dependencies {
    api(project(":blocks"))
    api(project(":service"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
}
