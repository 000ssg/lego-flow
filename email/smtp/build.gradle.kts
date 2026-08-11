// Lego Flow SMTP — Simple Mail Transfer Protocol (RFC 5321)
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-blocks"))
    api(project(":lego-flow-service"))
    api(project(":lego-flow-email-common"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
