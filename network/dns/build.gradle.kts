// Lego Flow DNS — Domain Name System (RFC 1034/1035)
val slf4jVersion: String by project

dependencies {
    api(project(":blocks"))
    api(project(":service"))
    api(project(":web:http"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
