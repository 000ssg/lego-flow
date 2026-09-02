package ssg.legoflow.service;

import ssg.legoflow.blocks.ProcessorStatistics;
import ssg.legoflow.service.scope.*;
import ssg.legoflow.service.user.AccessControl;
import ssg.legoflow.service.user.ServiceRole;
import ssg.legoflow.service.user.ServiceUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;
public class DefaultServiceContext implements ServiceContext {

    private final Logger logger;
    private final ProcessorStatistics statistics;
    private final ConcurrentHashMap<String, Object> attributes = new ConcurrentHashMap<>();
    private final SiteScope siteScope;
    private final ApplicationScope applicationScope;
    private final SessionScope sessionScope;
    private final RequestScope requestScope;
    private final ServiceUser user;
    private final AccessControl accessControl;

    public DefaultServiceContext(ServiceUser user) {
        this(user, new AccessControl());
    }

    public DefaultServiceContext(ServiceUser user, AccessControl accessControl) {
        this(LoggerFactory.getLogger(DefaultServiceContext.class),
                new ProcessorStatistics(),
                new SiteScope(), new ApplicationScope(), new SessionScope(), new RequestScope(),
                user, accessControl);
    }

    public DefaultServiceContext(Logger logger, ProcessorStatistics statistics,
                                 SiteScope siteScope, ApplicationScope applicationScope,
                                 SessionScope sessionScope, RequestScope requestScope,
                                 ServiceUser user, AccessControl accessControl) {
        this.logger = logger;
        this.statistics = statistics;
        this.siteScope = siteScope;
        this.applicationScope = applicationScope;
        this.sessionScope = sessionScope;
        this.requestScope = requestScope;
        this.user = user;
        this.accessControl = accessControl;
    }

    @Override
    public Logger getLogger() { return logger; }

    @Override
    public ProcessorStatistics getStatistics() { return statistics; }

    @Override
    public void handleError(Throwable error) {
        logger.error("Service error: {}", error.getMessage(), error);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getAttribute(String key) { return (T) attributes.get(key); }

    @Override
    public void setAttribute(String key, Object value) {
        if (value == null) attributes.remove(key);
        else attributes.put(key, value);
    }

    @Override
    public SiteScope getSiteScope() { return siteScope; }

    @Override
    public ApplicationScope getApplicationScope() { return applicationScope; }

    @Override
    public SessionScope getSessionScope() { return sessionScope; }

    @Override
    public RequestScope getRequestScope() { return requestScope; }

    @Override
    public ServiceUser getUser() { return user; }

    @Override
    public boolean hasRole(ServiceRole role) { return user.hasRole(role); }

    @Override
    public void checkPermission(String operation) throws AccessControl.AccessDeniedException {
        accessControl.checkPermission(user, operation);
    }
}
