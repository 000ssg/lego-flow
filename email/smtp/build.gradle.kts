// Lego Flow SMTP — Simple Mail Transfer Protocol (RFC 5321)
val slf4jVersion: String by project

dependencies {
    api(project(":blocks"))
    api(project(":service"))
    api(project(":email:common"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
