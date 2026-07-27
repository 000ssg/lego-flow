// Lego Flow HTTP Auth Core — Authentication framework
val slf4jVersion: String by project
val mockitoVersion: String by project

dependencies {
    api(project(":blocks"))
    api(project(":web:http"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
}
