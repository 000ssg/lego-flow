// Lego Flow HTTP Auth Basic/Digest — RFC 7617 and RFC 7616
val slf4jVersion: String by project
val mockitoVersion: String by project

dependencies {
    api(project(":auth:http-auth:core"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
}
