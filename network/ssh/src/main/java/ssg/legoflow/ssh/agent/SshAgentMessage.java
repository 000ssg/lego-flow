package ssg.legoflow.ssh.agent;

import java.util.List;

/**
 * Sealed interface for SSH agent protocol messages per draft-miller-ssh-agent.
 *
 * <p>Defines the message types exchanged between an SSH client and agent,
 * including identity management and signing operations.
 *
 * @since 1.0.0
 */
public sealed interface SshAgentMessage {

    /** Agent failure response (type 5). */
    int SSH_AGENT_FAILURE = 5;
    /** Agent success response (type 6). */
    int SSH_AGENT_SUCCESS = 6;
    /** Request identities (type 11). */
    int SSH_AGENTC_REQUEST_IDENTITIES = 11;
    /** Identities answer (type 12). */
    int SSH_AGENT_IDENTITIES_ANSWER = 12;
    /** Sign request (type 13). */
    int SSH_AGENTC_SIGN_REQUEST = 13;
    /** Sign response (type 14). */
    int SSH_AGENT_SIGN_RESPONSE = 14;
    /** Add identity (type 17). */
    int SSH_AGENTC_ADD_IDENTITY = 17;
    /** Remove identity (type 18). */
    int SSH_AGENTC_REMOVE_IDENTITY = 18;
    /** Remove all identities (type 19). */
    int SSH_AGENTC_REMOVE_ALL_IDENTITIES = 19;

    /**
     * Returns the message type code.
     *
     * @return the agent protocol message type
     */
    int type();

    /**
     * Agent failure response.
     *
     * @since 1.0.0
     */
    record Failure() implements SshAgentMessage {
        @Override public int type() { return SSH_AGENT_FAILURE; }
    }

    /**
     * Agent success response.
     *
     * @since 1.0.0
     */
    record Success() implements SshAgentMessage {
        @Override public int type() { return SSH_AGENT_SUCCESS; }
    }

    /**
     * Request for the list of identities held by the agent.
     *
     * @since 1.0.0
     */
    record RequestIdentities() implements SshAgentMessage {
        @Override public int type() { return SSH_AGENTC_REQUEST_IDENTITIES; }
    }

    /**
     * Response containing the list of identities.
     *
     * @param identities list of (public key blob, comment) pairs
     * @since 1.0.0
     */
    record IdentitiesAnswer(List<Identity> identities) implements SshAgentMessage {
        @Override public int type() { return SSH_AGENT_IDENTITIES_ANSWER; }

        /**
         * A single identity entry: public key blob and comment.
         *
         * @param publicKeyBlob the SSH-encoded public key
         * @param comment       the key comment
         */
        public record Identity(byte[] publicKeyBlob, String comment) {}
    }

    /**
     * Request to sign data with a specific key.
     *
     * @param publicKeyBlob the key to sign with
     * @param data          the data to sign
     * @param flags         signing flags (0 for default)
     * @since 1.0.0
     */
    record SignRequest(byte[] publicKeyBlob, byte[] data, int flags) implements SshAgentMessage {
        @Override public int type() { return SSH_AGENTC_SIGN_REQUEST; }
    }

    /**
     * Response containing a signature.
     *
     * @param signature the signature bytes
     * @since 1.0.0
     */
    record SignResponse(byte[] signature) implements SshAgentMessage {
        @Override public int type() { return SSH_AGENT_SIGN_RESPONSE; }
    }

    /**
     * Request to add an identity to the agent.
     *
     * @param keyType    the key type string
     * @param keyBlob    the public key blob
     * @param privateKey the private key bytes
     * @param comment    the key comment
     * @since 1.0.0
     */
    record AddIdentity(String keyType, byte[] keyBlob, byte[] privateKey, String comment) implements SshAgentMessage {
        @Override public int type() { return SSH_AGENTC_ADD_IDENTITY; }
    }

    /**
     * Request to remove a specific identity.
     *
     * @param publicKeyBlob the public key blob of the identity to remove
     * @since 1.0.0
     */
    record RemoveIdentity(byte[] publicKeyBlob) implements SshAgentMessage {
        @Override public int type() { return SSH_AGENTC_REMOVE_IDENTITY; }
    }

    /**
     * Request to remove all identities from the agent.
     *
     * @since 1.0.0
     */
    record RemoveAllIdentities() implements SshAgentMessage {
        @Override public int type() { return SSH_AGENTC_REMOVE_ALL_IDENTITIES; }
    }
}
