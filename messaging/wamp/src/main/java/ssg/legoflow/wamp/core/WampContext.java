package ssg.legoflow.wamp.core;

import ssg.legoflow.service.ServiceContext;

/**
 * Context for WAMP operations, extending {@link ServiceContext} with session and realm information.
 *
 * @since 0.1.0
 */
public interface WampContext extends ServiceContext {

    /**
     * Returns the WAMP session associated with this context.
     *
     * @return the current WAMP session
     */
    WampSession getSession();

    /**
     * Returns the realm name for this context.
     *
     * @return the realm name
     */
    String getRealm();
}
