// Lego Flow WAMP — Web Application Messaging Protocol
val slf4jVersion: String by project
val mockitoVersion: String by project

dependencies {
    api(project(":blocks"))
    api(project(":service"))
    api(project(":web:http"))
    api(project(":web:web-services"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
}
