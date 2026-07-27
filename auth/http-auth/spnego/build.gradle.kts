// Lego Flow HTTP Auth SPNEGO — HTTP Negotiate (SPNEGO) authentication
val slf4jVersion: String by project
val mockitoVersion: String by project

dependencies {
    api(project(":auth:http-auth:core"))
    api(project(":auth:gssapi"))
    api(project(":web:http"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
}
