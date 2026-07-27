package ssg.legoflow.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.service.scope.*;
import ssg.legoflow.service.user.AccessControl;
import ssg.legoflow.service.user.ServiceRole;
import ssg.legoflow.service.user.ServiceUser;

public interface ServiceContext extends Context {

    SiteScope getSiteScope();

    ApplicationScope getApplicationScope();

    SessionScope getSessionScope();

    RequestScope getRequestScope();

    ServiceUser getUser();

    boolean hasRole(ServiceRole role);

    void checkPermission(String operation) throws AccessControl.AccessDeniedException;
}
