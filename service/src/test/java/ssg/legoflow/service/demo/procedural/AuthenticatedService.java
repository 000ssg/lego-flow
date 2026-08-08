package ssg.legoflow.service.demo.procedural;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import ssg.legoflow.service.user.AccessControl;
import ssg.legoflow.service.user.ServiceRole;
import ssg.legoflow.service.user.UserType;

import java.util.Set;

public class AuthenticatedService extends AbstractService<String, String> {

    private final AccessControl accessControl;

    public AuthenticatedService() {
        this(createDefaultAccessControl());
    }

    public AuthenticatedService(AccessControl accessControl) {
        super(String.class, String.class, new ServiceDescriptor("authenticated", "Role-based access service"));
        this.accessControl = accessControl;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected String[] convertToOutput(Context ctx, String... input) {
        if (ctx instanceof ServiceContext sctx) {
            var user = sctx.getUser();
            var results = new String[input.length];
            for (int i = 0; i < input.length; i++) {
                var operation = input[i];
                if (accessControl.isAllowed(user, operation)) {
                    results[i] = "OK:" + operation + " by " + user.getName() + " (" + user.getType() + ")";
                } else {
                    results[i] = "DENIED:" + operation + " for " + user.getName();
                }
            }
            return results;
        }
        return new String[]{"ERROR:no service context"};
    }

    @SuppressWarnings("unchecked")
    @Override
    protected String[] convertToInput(Context ctx, String... output) {
        return output;
    }

    private static AccessControl createDefaultAccessControl() {
        var ac = new AccessControl();
        ac.requireRole("read", ServiceRole.GUEST);
        ac.requireRole("write", ServiceRole.USER);
        ac.requireRole("delete", ServiceRole.ADMIN);
        ac.requireRoles("admin", Set.of(ServiceRole.ADMIN));
        return ac;
    }
}
