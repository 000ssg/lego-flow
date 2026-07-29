// Lego Flow HTTP Auth OAuth — OAuth 2.0 and OpenID Connect
val slf4jVersion: String by project
val mockitoVersion: String by project

dependencies {
    api(project(":lego-flow-http-auth-core"))
    api(project(":lego-flow-http"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
}
