package ssg.legoflow.service;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.service.channel.DataChannel;
import ssg.legoflow.service.channel.ServerDataChannel;
import ssg.legoflow.service.manager.SelectableChannelManager;
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

    default void registerChannel(Service<?, ?> service, DataChannel channel) {
        getChannelManager().registerChannel(service, channel);
    }

    default void registerServerChannel(Service<?, ?> service, ServerDataChannel channel) {
        getChannelManager().registerServerChannel(service, channel);
    }

    default SelectableChannelManager getChannelManager() {
        return getAttribute("channelManager");
    }
}
