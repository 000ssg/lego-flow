package ssg.legoflow.service.filter;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.service.DefaultServiceContext;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.user.ServiceUser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ContextPropagationFilterTest {

    @Test
    void testDoFilterSetsPropagatedContextAttribute() {
        ServiceContext source = new DefaultServiceContext(ServiceUser.anonymous());
        source.getRequestScope().setAttribute("key1", "value1");
        source.getRequestScope().setAttribute("key2", "value2");
        
        ContextPropagationFilter<String> filter = new ContextPropagationFilter<>(String.class, source);
        ServiceContext ctx = new DefaultServiceContext(ServiceUser.anonymous());
        
        String[] data = {"hello", "world"};
        String[] result = filter.doFilter(ctx, data);
        
        assertThat(result).isSameAs(data);
        ServiceContext propagated = ContextPropagationFilter.getPropagatedContext(ctx);
        assertThat(propagated).isSameAs(source);
    }

    @Test
    void testDoFilterPropagatesScopesToServiceContext() {
        ServiceContext source = new DefaultServiceContext(ServiceUser.anonymous());
        source.getRequestScope().setAttribute("reqAttr", "reqValue");
        source.getSessionScope().setAttribute("sessAttr", "sessValue");
        
        ContextPropagationFilter<String> filter = new ContextPropagationFilter<>(String.class, source);
        ServiceContext target = new DefaultServiceContext(ServiceUser.anonymous());
        
        filter.doFilter(target, "data");
        
        assertThat((Object)target.getRequestScope().getAttribute("reqAttr")).isEqualTo("reqValue");
        assertThat((Object)target.getSessionScope().getAttribute("sessAttr")).isEqualTo("sessValue");
    }

    @Test
    void testDoFilterReturnsOriginalData() {
        ServiceContext source = new DefaultServiceContext(ServiceUser.anonymous());
        ContextPropagationFilter<String> filter = new ContextPropagationFilter<>(String.class, source);
        
        String[] input = {"a", "b", "c"};
        String[] result = filter.doFilter(new DefaultServiceContext(ServiceUser.anonymous()), input);
        
        assertThat(result).isSameAs(input);
    }

    @Test
    void testGetPropagatedContextReturnsNullWhenNotSet() {
        ServiceContext ctx = new DefaultServiceContext(ServiceUser.anonymous());
        ServiceContext propagated = ContextPropagationFilter.getPropagatedContext(ctx);
        assertThat(propagated).isNull();
    }

    @Test
    void testGetPropagatedContextAfterFilter() {
        ServiceContext source = new DefaultServiceContext(ServiceUser.anonymous());
        source.getRequestScope().setAttribute("test", "value");
        
        ContextPropagationFilter<Integer> filter = new ContextPropagationFilter<>(Integer.class, source);
        ServiceContext target = new DefaultServiceContext(ServiceUser.anonymous());
        filter.doFilter(target, 42);
        
        ServiceContext propagated = ContextPropagationFilter.getPropagatedContext(target);
        assertThat(propagated).isSameAs(source);
    }

    @Test
    void testPropagationPreservesMultipleAttributes() {
        ServiceContext source = new DefaultServiceContext(ServiceUser.anonymous());
        
        for (int i = 0; i < 10; i++) {
            source.getRequestScope().setAttribute("attr" + i, "val" + i);
            source.getSessionScope().setAttribute("sess" + i, "sval" + i);
        }
        
        ContextPropagationFilter<String> filter = new ContextPropagationFilter<>(String.class, source);
        ServiceContext target = new DefaultServiceContext(ServiceUser.anonymous());
        filter.doFilter(target, "data");
        
        for (int i = 0; i < 10; i++) {
            assertThat((Object)target.getRequestScope().getAttribute("attr" + i)).isEqualTo("val" + i);
            assertThat((Object)target.getSessionScope().getAttribute("sess" + i)).isEqualTo("sval" + i);
        }
    }

    @Test
    void testPropagationDoesNotAffectSiteOrApplicationScope() {
        ServiceContext source = new DefaultServiceContext(ServiceUser.anonymous());
        source.getSiteScope().setAttribute("siteKey", "siteValue");
        source.getApplicationScope().setAttribute("appKey", "appValue");
        
        ContextPropagationFilter<String> filter = new ContextPropagationFilter<>(String.class, source);
        ServiceContext target = new DefaultServiceContext(ServiceUser.anonymous());
        filter.doFilter(target, "data");
        
        assertThat((Object)target.getSiteScope().getAttribute("siteKey")).isNull();
        assertThat((Object)target.getApplicationScope().getAttribute("appKey")).isNull();
    }

    @Test
    void testMultipleFiltersChain() {
        ServiceContext source1 = new DefaultServiceContext(ServiceUser.anonymous());
        source1.getRequestScope().setAttribute("source1", "value1");
        
        ServiceContext source2 = new DefaultServiceContext(ServiceUser.anonymous());
        source2.getRequestScope().setAttribute("source2", "value2");
        
        ContextPropagationFilter<String> filter1 = new ContextPropagationFilter<>(String.class, source1);
        ContextPropagationFilter<String> filter2 = new ContextPropagationFilter<>(String.class, source2);
        
        ServiceContext target = new DefaultServiceContext(ServiceUser.anonymous());
        filter1.doFilter(target, "data");
        filter2.doFilter(target, "data");
        
        ServiceContext propagated = ContextPropagationFilter.getPropagatedContext(target);
        assertThat(propagated).isSameAs(source2);
    }

    @Test
    void testPropagationWithEmptySourceScopes() {
        ServiceContext source = new DefaultServiceContext(ServiceUser.anonymous());
        
        ContextPropagationFilter<String> filter = new ContextPropagationFilter<>(String.class, source);
        ServiceContext target = new DefaultServiceContext(ServiceUser.anonymous());
        
        String[] result = filter.doFilter(target, "data");
        assertThat(result).containsExactly("data");
    }

    @Test
    void testFilterViaFilterMethod() {
        ServiceContext source = new DefaultServiceContext(ServiceUser.anonymous());
        source.getRequestScope().setAttribute("testKey", "testValue");
        
        ContextPropagationFilter<String> filter = new ContextPropagationFilter<>(String.class, source);
        ServiceContext ctx = new DefaultServiceContext(ServiceUser.anonymous());
        
        String[] result = filter.filter(ctx, "hello", "world");
        
        assertThat(result).containsExactly("hello", "world");
        ServiceContext propagated = ContextPropagationFilter.getPropagatedContext(ctx);
        assertThat(propagated).isSameAs(source);
    }

    @Test
    void testFilterStateIsIdleInitially() {
        ServiceContext source = new DefaultServiceContext(ServiceUser.anonymous());
        ContextPropagationFilter<String> filter = new ContextPropagationFilter<>(String.class, source);
        
        assertThat(filter.getState()).isEqualTo(ssg.legoflow.blocks.ProcessorState.IDLE);
    }
}
