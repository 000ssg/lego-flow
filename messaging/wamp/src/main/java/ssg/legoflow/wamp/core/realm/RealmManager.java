package ssg.legoflow.wamp.core.realm;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple WAMP realms, providing creation, lookup, and removal.
 *
 * @since 0.1.0
 */
public class RealmManager {

    private final Map<String, Realm> realms = new ConcurrentHashMap<>();

    /**
     * Creates a new realm with the given name. If a realm with the same name already exists,
     * the existing realm is returned.
     *
     * @param name the realm name
     * @return the created or existing realm
     */
    public Realm createRealm(String name) {
        return realms.computeIfAbsent(name, Realm::new);
    }

    /**
     * Returns the realm with the given name, if it exists.
     *
     * @param name the realm name
     * @return an optional containing the realm, or empty
     */
    public Optional<Realm> getRealm(String name) {
        return Optional.ofNullable(realms.get(name));
    }

    /**
     * Removes the realm with the given name.
     *
     * @param name the realm name to remove
     * @return {@code true} if a realm was removed
     */
    public boolean removeRealm(String name) {
        return realms.remove(name) != null;
    }

    /**
     * Returns the number of managed realms.
     *
     * @return the realm count
     */
    public int getRealmCount() {
        return realms.size();
    }
}
