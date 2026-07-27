// Lego Flow SNMP — Simple Network Management Protocol v3 (RFC 3411-3418)
val slf4jVersion: String by project

dependencies {
    api(project(":blocks"))
    api(project(":service"))
    api(project(":network:common"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
