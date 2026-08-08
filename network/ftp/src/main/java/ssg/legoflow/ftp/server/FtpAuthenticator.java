package ssg.legoflow.ftp.server;

/**
 * Functional interface for FTP user authentication.
 *
 * <p>Implementations validate user credentials and return whether access is granted.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface FtpAuthenticator {

    /**
     * Authenticates a user.
     *
     * @param username the username
     * @param password the password
     * @return {@code true} if the credentials are valid
     */
    boolean authenticate(String username, String password);

    /**
     * Returns an authenticator that accepts any credentials.
     *
     * @return a permissive authenticator
     */
    static FtpAuthenticator acceptAll() {
        return (u, p) -> true;
    }

    /**
     * Returns an authenticator that checks a single username/password pair.
     *
     * @param expectedUser the expected username
     * @param expectedPass the expected password
     * @return a single-user authenticator
     */
    static FtpAuthenticator singleUser(String expectedUser, String expectedPass) {
        return (u, p) -> expectedUser.equals(u) && expectedPass.equals(p);
    }

    /**
     * Returns an authenticator that allows anonymous access (any password for user "anonymous").
     *
     * @return an anonymous authenticator
     */
    static FtpAuthenticator anonymous() {
        return (u, p) -> "anonymous".equalsIgnoreCase(u);
    }
}
