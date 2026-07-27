package ssg.legoflow.service.filter;

import ssg.legoflow.blocks.AbstractDataFilter;
import ssg.legoflow.blocks.Context;
import ssg.legoflow.service.ServiceContext;

public class ContextPropagationFilter<T> extends AbstractDataFilter<T> {

    private static final String PROPAGATED_CONTEXT_KEY = "service.propagated.context";

    private final ServiceContext sourceContext;

    public ContextPropagationFilter(Class<T> dataType, ServiceContext sourceContext) {
        super(dataType);
        this.sourceContext = sourceContext;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected T[] doFilter(Context ctx, T... data) {
        ctx.setAttribute(PROPAGATED_CONTEXT_KEY, sourceContext);

        if (ctx instanceof ServiceContext targetCtx) {
            propagateScopes(targetCtx);
        }

        return data;
    }

    private void propagateScopes(ServiceContext targetCtx) {
        var sourceRequest = sourceContext.getRequestScope();
        var targetRequest = targetCtx.getRequestScope();
        sourceRequest.getAttributes().forEach(targetRequest::setAttribute);

        var sourceSession = sourceContext.getSessionScope();
        var targetSession = targetCtx.getSessionScope();
        sourceSession.getAttributes().forEach(targetSession::setAttribute);
    }

    public static ServiceContext getPropagatedContext(Context ctx) {
        return ctx.getAttribute(PROPAGATED_CONTEXT_KEY);
    }
}
