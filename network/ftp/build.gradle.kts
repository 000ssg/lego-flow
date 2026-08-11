// Lego Flow FTP — File Transfer Protocol (RFC 959 / RFC 4217)
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-blocks"))
    api(project(":lego-flow-service"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
