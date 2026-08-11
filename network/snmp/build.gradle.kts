// Lego Flow SNMP — Simple Network Management Protocol v3 (RFC 3411-3418)
val slf4jVersion: String by project

dependencies {
    api(project(":lego-flow-blocks"))
    api(project(":lego-flow-service"))
    api(project(":lego-flow-network-common"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
