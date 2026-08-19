package ssg.legoflow.ftp.server;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FtpAuthenticatorTest {

    @Nested
    class AcceptAllTests {

        @Test
        void acceptsAnyCredentials() {
            FtpAuthenticator auth = FtpAuthenticator.acceptAll();
            assertThat(auth.authenticate("user", "pass")).isTrue();
        }

        @Test
        void acceptsEmptyCredentials() {
            FtpAuthenticator auth = FtpAuthenticator.acceptAll();
            assertThat(auth.authenticate("", "")).isTrue();
        }

        @Test
        void acceptsNullPassword() {
            FtpAuthenticator auth = FtpAuthenticator.acceptAll();
            assertThat(auth.authenticate("user", null)).isTrue();
        }

        @Test
        void acceptsNullUsername() {
            FtpAuthenticator auth = FtpAuthenticator.acceptAll();
            assertThat(auth.authenticate(null, "pass")).isTrue();
        }

        @Test
        void acceptsBothNull() {
            FtpAuthenticator auth = FtpAuthenticator.acceptAll();
            assertThat(auth.authenticate(null, null)).isTrue();
        }
    }

    @Nested
    class SingleUserTests {

        @Test
        void acceptsCorrectCredentials() {
            FtpAuthenticator auth = FtpAuthenticator.singleUser("admin", "secret");
            assertThat(auth.authenticate("admin", "secret")).isTrue();
        }

        @Test
        void rejectsWrongPassword() {
            FtpAuthenticator auth = FtpAuthenticator.singleUser("admin", "secret");
            assertThat(auth.authenticate("admin", "wrong")).isFalse();
        }

        @Test
        void rejectsWrongUsername() {
            FtpAuthenticator auth = FtpAuthenticator.singleUser("admin", "secret");
            assertThat(auth.authenticate("other", "secret")).isFalse();
        }

        @Test
        void rejectsBothWrong() {
            FtpAuthenticator auth = FtpAuthenticator.singleUser("admin", "secret");
            assertThat(auth.authenticate("other", "wrong")).isFalse();
        }

        @Test
        void rejectsNullUsername() {
            FtpAuthenticator auth = FtpAuthenticator.singleUser("admin", "secret");
            assertThat(auth.authenticate(null, "secret")).isFalse();
        }

        @Test
        void rejectsNullPassword() {
            FtpAuthenticator auth = FtpAuthenticator.singleUser("admin", "secret");
            assertThat(auth.authenticate("admin", null)).isFalse();
        }

        @Test
        void rejectsEmptyCredentials() {
            FtpAuthenticator auth = FtpAuthenticator.singleUser("admin", "secret");
            assertThat(auth.authenticate("", "")).isFalse();
        }

        @Test
        void caseSensitivePassword() {
            FtpAuthenticator auth = FtpAuthenticator.singleUser("admin", "Secret123");
            assertThat(auth.authenticate("admin", "secret123")).isFalse();
        }

        @Test
        void caseSensitiveUsername() {
            FtpAuthenticator auth = FtpAuthenticator.singleUser("Admin", "secret");
            assertThat(auth.authenticate("admin", "secret")).isFalse();
        }
    }

    @Nested
    class AnonymousTests {

        @Test
        void acceptsAnonymousLowercase() {
            FtpAuthenticator auth = FtpAuthenticator.anonymous();
            assertThat(auth.authenticate("anonymous", "anything")).isTrue();
        }

        @Test
        void acceptsAnonymousUppercase() {
            FtpAuthenticator auth = FtpAuthenticator.anonymous();
            assertThat(auth.authenticate("ANONYMOUS", "")).isTrue();
        }

        @Test
        void acceptsAnonymousMixedCase() {
            FtpAuthenticator auth = FtpAuthenticator.anonymous();
            assertThat(auth.authenticate("Anonymous", null)).isTrue();
        }

        @Test
        void rejectsNonAnonymousUser() {
            FtpAuthenticator auth = FtpAuthenticator.anonymous();
            assertThat(auth.authenticate("user", "pass")).isFalse();
        }

        @Test
        void rejectsNullUsername() {
            FtpAuthenticator auth = FtpAuthenticator.anonymous();
            assertThat(auth.authenticate(null, null)).isFalse();
        }

        @Test
        void rejectsEmptyUsername() {
            FtpAuthenticator auth = FtpAuthenticator.anonymous();
            assertThat(auth.authenticate("", "pass")).isFalse();
        }

        @Test
        void anonymousPasswordIrrelevant() {
            FtpAuthenticator auth = FtpAuthenticator.anonymous();
            assertThat(auth.authenticate("anonymous", "")).isTrue();
            assertThat(auth.authenticate("anonymous", null)).isTrue();
            assertThat(auth.authenticate("anonymous", "wrong_password")).isTrue();
            assertThat(auth.authenticate("anonymous", "email@example.com")).isTrue();
        }
    }

    @Nested
    class LambdaImplementationTests {

        @Test
        void canUseAsLambda() {
            FtpAuthenticator auth = (u, p) -> u.equals("lambda") && p.equals("test");
            assertThat(auth.authenticate("lambda", "test")).isTrue();
            assertThat(auth.authenticate("other", "test")).isFalse();
        }

        @Test
        void canUseAsMethodReference() {
            FtpAuthenticator auth = FtpAuthenticator.acceptAll();
            assertThat(auth.authenticate("x", "y")).isTrue();
        }
    }
}
