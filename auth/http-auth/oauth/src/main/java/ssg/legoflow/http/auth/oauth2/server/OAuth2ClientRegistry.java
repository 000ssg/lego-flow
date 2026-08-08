package ssg.legoflow.http.auth.oauth2.server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of OAuth 2.0 registered clients for the authorization server.
 *
 * @since 0.1.0
 */
public class OAuth2ClientRegistry {

    private final Map<String, RegisteredClient> clients = new ConcurrentHashMap<>();

    /**
     * A registered OAuth 2.0 client.
     *
     * @param clientId     the client ID
     * @param clientSecret the client secret (null for public clients)
     * @param redirectUris allowed redirect URIs
     * @param scopes       allowed scopes
     * @param grantTypes   allowed grant types
     * @param confidential whether this is a confidential client
     * @since 0.1.0
     */
    public record RegisteredClient(
            String clientId,
            String clientSecret,
            Set<String> redirectUris,
            Set<String> scopes,
            Set<String> grantTypes,
            boolean confidential) {
    }

    /**
     * Registers a client.
     *
     * @param client the client to register
     * @return this registry for chaining
     * @since 0.1.0
     */
    public OAuth2ClientRegistry register(RegisteredClient client) {
        clients.put(client.clientId(), client);
        return this;
    }

    /**
     * Looks up a client by ID.
     *
     * @param clientId the client ID
     * @return the client, or empty
     * @since 0.1.0
     */
    public Optional<RegisteredClient> get(String clientId) {
        return Optional.ofNullable(clients.get(clientId));
    }

    /**
     * Validates client credentials.
     *
     * @param clientId     the client ID
     * @param clientSecret the client secret
     * @return the client if valid, empty otherwise
     * @since 0.1.0
     */
    public Optional<RegisteredClient> authenticate(String clientId, String clientSecret) {
        var client = clients.get(clientId);
        if (client == null) return Optional.empty();
        if (client.confidential() && !Objects.equals(client.clientSecret(), clientSecret)) {
            return Optional.empty();
        }
        return Optional.of(client);
    }

    /**
     * Checks if a redirect URI is allowed for a client.
     *
     * @param clientId    the client ID
     * @param redirectUri the redirect URI to check
     * @return true if allowed
     * @since 0.1.0
     */
    public boolean isRedirectUriAllowed(String clientId, String redirectUri) {
        var client = clients.get(clientId);
        return client != null && client.redirectUris().contains(redirectUri);
    }

    /**
     * Removes a client.
     *
     * @param clientId the client ID
     * @since 0.1.0
     */
    public void remove(String clientId) {
        clients.remove(clientId);
    }

    /**
     * Returns all registered clients.
     *
     * @return unmodifiable collection of clients
     * @since 0.1.0
     */
    public Collection<RegisteredClient> all() {
        return Collections.unmodifiableCollection(clients.values());
    }

    /**
     * Returns the number of registered clients.
     *
     * @return the client count
     * @since 0.1.0
     */
    public int size() {
        return clients.size();
    }
}
