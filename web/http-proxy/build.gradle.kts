// Lego Flow HTTP Proxy — Forward and reverse HTTP proxy
val slf4jVersion: String by project
val mockitoVersion: String by project

dependencies {
    api(project(":lego-flow-blocks"))
    api(project(":lego-flow-service"))
    api(project(":lego-flow-http"))
    api(project(":lego-flow-cluster-core"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
}
