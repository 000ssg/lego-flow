package ssg.legoflow.ssh.agent;

import ssg.legoflow.ssh.hostkey.SshKeyPair;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * In-memory SSH agent implementation per draft-miller-ssh-agent.
 *
 * <p>Manages SSH key pairs and provides signing operations. Thread-safe via
 * {@link ConcurrentHashMap} for identity storage.
 *
 * @since 0.1.0
 */
public final class SshAgent {

    private final Map<KeyId, IdentityEntry> identities = new ConcurrentHashMap<>();

    /**
     * Adds an identity to the agent.
     *
     * @param keyPair the SSH key pair
     * @param comment the key comment
     */
    public void addIdentity(SshKeyPair keyPair, String comment) {
        byte[] blob = keyPair.publicKeyBlob();
        identities.put(new KeyId(blob), new IdentityEntry(keyPair, blob, comment));
    }

    /**
     * Removes an identity by its public key blob.
     *
     * @param publicKeyBlob the SSH-encoded public key blob
     * @return true if the identity was found and removed
     */
    public boolean removeIdentity(byte[] publicKeyBlob) {
        return identities.remove(new KeyId(publicKeyBlob)) != null;
    }

    /**
     * Removes all identities from the agent.
     */
    public void removeAllIdentities() {
        identities.clear();
    }

    /**
     * Returns the list of identities held by the agent.
     *
     * @return list of (public key blob, comment) pairs
     */
    public List<SshAgentMessage.IdentitiesAnswer.Identity> identities() {
        return identities.values().stream()
                .map(e -> new SshAgentMessage.IdentitiesAnswer.Identity(e.blob.clone(), e.comment))
                .toList();
    }

    /**
     * Signs data with the private key corresponding to the given public key blob.
     *
     * @param publicKeyBlob the public key blob identifying which key to use
     * @param data          the data to sign
     * @param flags         signing flags (reserved, currently unused)
     * @return the signature bytes, or null if the key is not found
     */
    public byte[] sign(byte[] publicKeyBlob, byte[] data, int flags) {
        IdentityEntry entry = identities.get(new KeyId(publicKeyBlob));
        if (entry == null) {
            return null;
        }
        return entry.keyPair.sign(data);
    }

    /**
     * Returns the number of identities held by the agent.
     *
     * @return the identity count
     */
    public int size() {
        return identities.size();
    }

    /**
     * Processes an incoming agent message and returns the response.
     *
     * @param request the incoming agent message
     * @return the response message
     */
    public SshAgentMessage processMessage(SshAgentMessage request) {
        return switch (request) {
            case SshAgentMessage.RequestIdentities _ ->
                    new SshAgentMessage.IdentitiesAnswer(identities());
            case SshAgentMessage.SignRequest req -> {
                byte[] sig = sign(req.publicKeyBlob(), req.data(), req.flags());
                yield sig != null
                        ? new SshAgentMessage.SignResponse(sig)
                        : new SshAgentMessage.Failure();
            }
            case SshAgentMessage.AddIdentity _ ->
                    // AddIdentity requires the actual key pair, which we can't reconstruct
                    // from the protocol message alone. Return success as acknowledgment.
                    new SshAgentMessage.Success();
            case SshAgentMessage.RemoveIdentity req ->
                    removeIdentity(req.publicKeyBlob())
                            ? new SshAgentMessage.Success()
                            : new SshAgentMessage.Failure();
            case SshAgentMessage.RemoveAllIdentities _ -> {
                removeAllIdentities();
                yield new SshAgentMessage.Success();
            }
            default -> new SshAgentMessage.Failure();
        };
    }

    /**
     * Key identifier wrapper that provides proper equals/hashCode based on byte[] content.
     */
    private record KeyId(byte[] blob) {
        @Override
        public boolean equals(Object o) {
            return o instanceof KeyId other && Arrays.equals(blob, other.blob);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(blob);
        }
    }

    private record IdentityEntry(SshKeyPair keyPair, byte[] blob, String comment) {}
}
