// Lego Flow gRPC — Google Remote Procedure Call Protocol
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-blocks"))
    api(project(":lego-flow-service"))
    api(project(":lego-flow-http2"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
