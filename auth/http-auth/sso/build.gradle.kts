// Lego Flow HTTP Auth SSO — Single Sign-On and SAML
val slf4jVersion: String by project
val mockitoVersion: String by project

dependencies {
    api(project(":lego-flow-http-auth-core"))
    api(project(":lego-flow-http-auth-oauth"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
}
