package ssg.legoflow.http.security;

import java.util.List;

public class SslConfig {

    private String keystorePath;
    private String keystorePassword;
    private String truststorePath;
    private String truststorePassword;
    private List<String> protocols = List.of("TLSv1.3", "TLSv1.2");
    private List<String> cipherSuites = List.of();

    public String getKeystorePath() { return keystorePath; }
    public void setKeystorePath(String path) { this.keystorePath = path; }
    public String getKeystorePassword() { return keystorePassword; }
    public void setKeystorePassword(String password) { this.keystorePassword = password; }
    public String getTruststorePath() { return truststorePath; }
    public void setTruststorePath(String path) { this.truststorePath = path; }
    public String getTruststorePassword() { return truststorePassword; }
    public void setTruststorePassword(String password) { this.truststorePassword = password; }
    public List<String> getProtocols() { return protocols; }
    public void setProtocols(List<String> protocols) { this.protocols = protocols; }
    public List<String> getCipherSuites() { return cipherSuites; }
    public void setCipherSuites(List<String> cipherSuites) { this.cipherSuites = cipherSuites; }
}
