// Lego Flow Web Services — Web service components for HTTP
val slf4jVersion: String by project
val mockitoVersion: String by project

dependencies {
    api(project(":blocks"))
    api(project(":service"))
    api(project(":web:http"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
}
