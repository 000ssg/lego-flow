// Lego Flow IMAP — IMAP4rev2 (RFC 9051)
val slf4jVersion: String by project

dependencies {
    api(project(":blocks"))
    api(project(":service"))
    api(project(":email:common"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
}
