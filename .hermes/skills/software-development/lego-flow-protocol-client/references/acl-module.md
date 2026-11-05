# ACL Module & Test Utilities

Cross-cutting utilities for access control, certificates, SSH keys, and SASL authentication used across lego-flow protocol modules.

## Certificate Generation (JDK 25+)

`sun.security.x509` internals are sealed on JDK 25. Use BouncyCastle:

```xml
<!-- acl/pom.xml -->
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.80</version>
</dependency>
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcpkix-jdk18on</artifactId>
    <version>1.80</version>
</dependency>
```

```java
// CertificateFactory.java — uses BouncyCastle JcaX509v3CertBuilder
// NOT sun.security.x509.X509CertImpl (sealed, inaccessible)
X500Name issuer = new X500Name("CN=CA");
X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
    issuer, serial, notBefore, notAfter, issuer, pubKey);
X509Certificate cert = new JcaX509CertificateConverter()
    .setProvider(new BouncyCastleProvider())
    .getCertificate(builder.build(privateKey));
```

## JaCoCo + BouncyCastle SSL Conflict

JaCoCo's bytecode agent (`-javaagent:...`) corrupts BouncyCastle SSL internals, causing `SSLEngine.handshake()` to hang indefinitely (6m+).

**Gradle fix** (in `acl/build.gradle.kts`):
```kotlin
tasks.withType<Test> {
    jvmArgs.removeIf { it.startsWith("-javaagent:") }
}
```

**Maven fix** (in `pom.xml` surefire config) — do NOT set `argLine` for this module.

## SSLEngine Handshake Test Pattern

```java
private void handshake(SSLEngine server, SSLEngine client) throws Exception {
    var s2c = ByteBuffer.allocate(16384);
    var c2s = ByteBuffer.allocate(16384);
    server.beginHandshake();
    client.beginHandshake();

    for (int i = 0; i < 500; i++) {  // BOUNDED — never infinite loop
        if (!sDone) { /* wrap server */ }
        if (!cDone) { /* wrap client */ }
        // Feed s2c bytes to client.unwrap(), c2s bytes to server.unwrap()
        if (server.getHandshakeStatus() == FINISHED) sDone = true;
        if (client.getHandshakeStatus() == FINISHED) cDone = true;
        if (sDone && cDone) break;
    }
}
```

Key: bounded iteration (500), check `FINISHED` status after unwrap, no `FINISHED_AND_WRAP` (doesn't exist in JDK 9+).

## Maven/Gradle Parity

User demands identical test counts and results between build systems. When they differ:
1. Check JaCoCo agent interference (common cause of Gradle hangs)
2. Check `--add-opens` missing in Gradle (Maven surefire has it, Gradle Test doesn't)
3. Check stale class files — run `clean test`, not just `test`

## ACL Module File Locations

- Domain model: `acl/src/main/java/ssg/legoflow/acl/model/`
- Cert generation: `acl/src/main/java/ssg/legoflow/acl/cert/CertificateFactory.java`
- SSH keys: `acl/src/main/java/ssg/legoflow/acl/key/SshKeyGenerator.java`
- SASL: `acl/src/main/java/ssg/legoflow/acl/sasl/SaslUtilities.java`
- Config loaders: `acl/src/main/java/ssg/legoflow/acl/config/`
- SSL contexts: `acl/src/main/java/ssg/legoflow/acl/ssl/SslContexts.java`
- Test domain: `acl/src/main/java/ssg/legoflow/acl/TestDomain.java`
