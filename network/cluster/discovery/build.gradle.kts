// Lego Flow Cluster Discovery — DNS-SD/mDNS service discovery
val slf4jVersion: String by project
val slf4jSimpleVersion: String by project
val junitVersion: String by project
val mockitoVersion: String by project
val assertjVersion: String by project

dependencies {
    api(project(":lego-flow-cluster-core"))
    api(project(":lego-flow-dns"))
    api(project(":lego-flow-service"))
    api(project(":lego-flow-network-common"))
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    testImplementation("org.slf4j:slf4j-simple:$slf4jSimpleVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.mockito:mockito-core:$mockitoVersion")
    testImplementation("org.assertj:assertj-core:$assertjVersion")
}
