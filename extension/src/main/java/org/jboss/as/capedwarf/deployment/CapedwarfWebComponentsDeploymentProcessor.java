package org.jboss.as.capedwarf.deployment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.metadata.javaee.spec.EnvironmentRefsGroupMetaData;
import org.jboss.metadata.javaee.spec.ParamValueMetaData;
import org.jboss.metadata.javaee.spec.ResourceReferenceMetaData;
import org.jboss.metadata.javaee.spec.ResourceReferencesMetaData;
import org.jboss.metadata.javaee.spec.SecurityRoleMetaData;
import org.jboss.metadata.javaee.spec.SecurityRolesMetaData;
import org.jboss.metadata.web.spec.AuthConstraintMetaData;
import org.jboss.metadata.web.spec.DispatcherType;
import org.jboss.metadata.web.spec.FilterMappingMetaData;
import org.jboss.metadata.web.spec.FilterMetaData;
import org.jboss.metadata.web.spec.FiltersMetaData;
import org.jboss.metadata.web.spec.ListenerMetaData;
import org.jboss.metadata.web.spec.LoginConfigMetaData;
import org.jboss.metadata.web.spec.SecurityConstraintMetaData;
import org.jboss.metadata.web.spec.ServletMappingMetaData;
import org.jboss.metadata.web.spec.ServletMetaData;
import org.jboss.metadata.web.spec.ServletsMetaData;
import org.jboss.metadata.web.spec.TransportGuaranteeType;
import org.jboss.metadata.web.spec.UserDataConstraintMetaData;
import org.jboss.metadata.web.spec.WebMetaData;
import org.jboss.metadata.web.spec.WebResourceCollectionMetaData;
import org.jboss.metadata.web.spec.WebResourceCollectionsMetaData;

/**
 * Add GAE filter and auth servlet.
 * Enable Faces, if not yet configured.
 *
 * @author <a href="mailto:marko.luksa@gmail.com">Marko Luksa</a>
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author <a href="mailto:mlazar@redhat.com">Matej Lazar</a>
 */
public class CapedwarfWebComponentsDeploymentProcessor extends CapedwarfWebModificationDeploymentProcessor {

    private static final String GAE_REMOTE_API_SERVLET_NAME = "com.google.apphosting.utils.remoteapi.RemoteApiServlet";
    private static final String CAPEDWARF_REMOTE_API_SERVLET_NAME = "org.jboss.capedwarf.admin.remote.RemoteApiServlet";

    private static final String SINGLE_THREAD_FILTER_NAME = "SingleThreadFilter";
    private static final String GAE_FILTER_NAME = "GAEFilter";
    private static final String AUTH_SERVLET_NAME = "authservlet";
    private static final String ADMIN_SERVLET_NAME = "CapedwarfAdminServlet";
    private static final String CHANNEL_SERVLET_NAME = "ChannelServlet";
    private static final String[] ADMIN_SERVLET_URL_MAPPING = {"/_ah/admin/*", "/_ah/admin/"};

    private final ListenerMetaData GAE_LISTENER;
    private final ListenerMetaData CDI_LISTENER;
    private final ListenerMetaData CDAS_LISTENER;
    private final FilterMetaData SINGLE_THREAD_FILTER;
    private final FilterMetaData GAE_FILTER;
    private final FilterMappingMetaData SINGLE_THREAD_FILTER_MAPPING;
    private final FilterMappingMetaData GAE_FILTER_MAPPING;
    private final ServletMetaData GAE_SERVLET;
    private final ServletMetaData ADMIN_SERVLET;
    private final ServletMetaData CHANNEL_SERVLET;
    private final ServletMappingMetaData GAE_SERVLET_MAPPING;
    private final ServletMappingMetaData ADMIN_SERVLET_MAPPING;
    private final ServletMappingMetaData CHANNEL_SERVLET_MAPPING;
    private final ResourceReferenceMetaData INFINISPAN_REF;
    private SecurityConstraintMetaData ADMIN_SERVLET_CONSTRAINT;
    private LoginConfigMetaData ADMIN_SERVLET_CONFIG;
    private SecurityRoleMetaData ADMIN_SERVLET_ROLE;


    private final boolean adminAuth;

    public CapedwarfWebComponentsDeploymentProcessor(boolean adminAuth) {
        this.adminAuth = adminAuth;

        GAE_LISTENER = createGaeListener();
        CDI_LISTENER = createCdiListener();
        CDAS_LISTENER = createAsListener();
        GAE_FILTER = createGaeFilter();
        GAE_FILTER_MAPPING = createGaeFilterMapping();
        SINGLE_THREAD_FILTER = createSingleThreadFilter(1); // TODO - per deployment #
        SINGLE_THREAD_FILTER_MAPPING = createSingleThreadFilterMapping();
        GAE_SERVLET = createAuthServlet();
        GAE_SERVLET_MAPPING = createAuthServletMapping();
        ADMIN_SERVLET = createAdminServlet();
        ADMIN_SERVLET_MAPPING = createAdminServletMapping();
        CHANNEL_SERVLET = createChannelServlet();
        CHANNEL_SERVLET_MAPPING = createChannelServletMapping();

        INFINISPAN_REF = createInfinispanRef();

        ADMIN_SERVLET_CONSTRAINT = createAdminServletSecurityConstraint();
        ADMIN_SERVLET_CONFIG = createAdminServletLogin();
        ADMIN_SERVLET_ROLE = createAdminServletSecurityRole();
    }

    @Override
    protected void doDeploy(DeploymentUnit unit, WebMetaData webMetaData, Type type) {
        if (type == Type.SPEC) {
            getListeners(webMetaData).add(0, GAE_LISTENER);
            getListeners(webMetaData).add(CDI_LISTENER);
            getListeners(webMetaData).add(CDAS_LISTENER);

            getFilterMappings(webMetaData).add(0, GAE_FILTER_MAPPING);

            if (CapedwarfDeploymentMarker.isThreadsafe(unit) == false) {
                getFilters(webMetaData).add(SINGLE_THREAD_FILTER);
                getFilterMappings(webMetaData).add(0, SINGLE_THREAD_FILTER_MAPPING);
            }

            getFilters(webMetaData).add(GAE_FILTER);

            getServlets(webMetaData).add(GAE_SERVLET);
            getServletMappings(webMetaData).add(GAE_SERVLET_MAPPING);

            getServlets(webMetaData).add(ADMIN_SERVLET);
            getServletMappings(webMetaData).add(ADMIN_SERVLET_MAPPING);

            getServlets(webMetaData).add(CHANNEL_SERVLET);
            getServletMappings(webMetaData).add(CHANNEL_SERVLET_MAPPING);

            replaceRemoteApiServlet(webMetaData);

            addResourceReference(webMetaData);

            if (adminAuth) {
                getSecurityConstraints(webMetaData).add(ADMIN_SERVLET_CONSTRAINT);
                getSecurityRoles(webMetaData).add(ADMIN_SERVLET_ROLE);
                webMetaData.setLoginConfig(ADMIN_SERVLET_CONFIG);
            }
        }
    }

    private void replaceRemoteApiServlet(WebMetaData webMetaData) {
        for (ServletMetaData servletMetaData : getServlets(webMetaData)) {
            if (GAE_REMOTE_API_SERVLET_NAME.equals(servletMetaData.getServletClass())) {
                servletMetaData.setServletClass(CAPEDWARF_REMOTE_API_SERVLET_NAME);
            }
        }
    }

    protected void addContextParamsTo(WebMetaData webMetaData, ParamValueMetaData param) {
        List<ParamValueMetaData> contextParams = webMetaData.getContextParams();
        if (contextParams == null) {
            contextParams = new ArrayList<ParamValueMetaData>();
            webMetaData.setContextParams(contextParams);
        }
        contextParams.add(param);
    }

    private ListenerMetaData createCdiListener() {
        ListenerMetaData listener = new ListenerMetaData();
        listener.setListenerClass("org.jboss.capedwarf.appidentity.CDIListener");
        return listener;
    }

    private ListenerMetaData createGaeListener() {
        ListenerMetaData listener = new ListenerMetaData();
        listener.setListenerClass("org.jboss.capedwarf.appidentity.GAEListener");
        return listener;
    }

    private ListenerMetaData createAsListener() {
        ListenerMetaData listener = new ListenerMetaData();
        listener.setListenerClass("org.jboss.as.capedwarf.api.CapedwarfListener");
        return listener;
    }

    private List<ListenerMetaData> getListeners(WebMetaData webMetaData) {
        List<ListenerMetaData> listeners = webMetaData.getListeners();
        if (listeners == null) {
            listeners = new ArrayList<ListenerMetaData>();
            webMetaData.setListeners(listeners);
        }
        return listeners;
    }

    private FilterMetaData createGaeFilter() {
        FilterMetaData filter = new FilterMetaData();
        filter.setFilterName(GAE_FILTER_NAME);
        filter.setFilterClass("org.jboss.capedwarf.appidentity.GAEFilter");
        return filter;
    }

    private FilterMetaData createSingleThreadFilter(int maxConcurrentRequests) {
        FilterMetaData filter = new FilterMetaData();
        filter.setFilterName(SINGLE_THREAD_FILTER_NAME);
        filter.setFilterClass("org.jboss.capedwarf.common.singlethread.SingleThreadFilter");
        ParamValueMetaData initParam = new ParamValueMetaData();
        initParam.setParamName("max-concurrent-requests");
        initParam.setParamValue(String.valueOf(maxConcurrentRequests));
        List<ParamValueMetaData> initParams = Collections.singletonList(initParam);
        filter.setInitParam(initParams);
        return filter;
    }

    private FiltersMetaData getFilters(WebMetaData webMetaData) {
        FiltersMetaData filters = webMetaData.getFilters();
        if (filters == null) {
            filters = new FiltersMetaData();
            webMetaData.setFilters(filters);
        }
        return filters;
    }

    private FilterMappingMetaData createGaeFilterMapping() {
        FilterMappingMetaData filterMapping = new FilterMappingMetaData();
        filterMapping.setFilterName(GAE_FILTER_NAME);
        filterMapping.setUrlPatterns(Collections.singletonList("/*"));
        filterMapping.setDispatchers(Arrays.asList(DispatcherType.REQUEST, DispatcherType.FORWARD));
        return filterMapping;
    }

    private FilterMappingMetaData createSingleThreadFilterMapping() {
        FilterMappingMetaData filterMapping = new FilterMappingMetaData();
        filterMapping.setFilterName(SINGLE_THREAD_FILTER_NAME);
        filterMapping.setUrlPatterns(Collections.singletonList("/*"));
        filterMapping.setDispatchers(Arrays.asList(DispatcherType.REQUEST, DispatcherType.FORWARD));
        return filterMapping;
    }

    private List<FilterMappingMetaData> getFilterMappings(WebMetaData webMetaData) {
        List<FilterMappingMetaData> filterMappings = webMetaData.getFilterMappings();
        if (filterMappings == null) {
            filterMappings = new ArrayList<FilterMappingMetaData>();
            webMetaData.setFilterMappings(filterMappings);
        }
        return filterMappings;
    }

    private ServletsMetaData getServlets(WebMetaData webMetaData) {
        ServletsMetaData servletsMetaData = webMetaData.getServlets();
        if (servletsMetaData == null) {
            servletsMetaData = new ServletsMetaData();
            webMetaData.setServlets(servletsMetaData);
        }
        return servletsMetaData;
    }

    private ServletMetaData createAuthServlet() {
        ServletMetaData servlet = new ServletMetaData();
        servlet.setServletName(AUTH_SERVLET_NAME);
        servlet.setServletClass("org.jboss.capedwarf.users.AuthServlet");
        servlet.setEnabled(true);
        return servlet;
    }

    private ServletMetaData createAdminServlet() {
        ServletMetaData servlet = new ServletMetaData();
        servlet.setServletName(ADMIN_SERVLET_NAME);
        servlet.setServletClass("org.jboss.capedwarf.admin.AdminServlet");
        servlet.setEnabled(true);
        return servlet;
    }

    private ServletMetaData createChannelServlet() {
        ServletMetaData servlet = new ServletMetaData();
        servlet.setServletName(CHANNEL_SERVLET_NAME);
        servlet.setServletClass("org.jboss.capedwarf.channel.servlet.ChannelServlet");
        servlet.setEnabled(true);
        return servlet;
    }

    private List<ServletMappingMetaData> getServletMappings(WebMetaData webMetaData) {
        List<ServletMappingMetaData> servletMappings = webMetaData.getServletMappings();
        if (servletMappings == null) {
            servletMappings = new ArrayList<ServletMappingMetaData>();
            webMetaData.setServletMappings(servletMappings);
        }
        return servletMappings;
    }

    private ServletMappingMetaData createAuthServletMapping() {
        ServletMappingMetaData servletMapping = new ServletMappingMetaData();
        servletMapping.setServletName(AUTH_SERVLET_NAME);
        servletMapping.setUrlPatterns(Collections.singletonList("/_ah/auth/*"));   // TODO: introduce AuthServlet.URL_PATTERN
        return servletMapping;
    }

    private ServletMappingMetaData createAdminServletMapping() {
        ServletMappingMetaData servletMapping = new ServletMappingMetaData();
        servletMapping.setServletName(ADMIN_SERVLET_NAME);
        servletMapping.setUrlPatterns(Arrays.asList(ADMIN_SERVLET_URL_MAPPING));
        return servletMapping;
    }

    private ServletMappingMetaData createChannelServletMapping() {
        ServletMappingMetaData servletMapping = new ServletMappingMetaData();
        servletMapping.setServletName(CHANNEL_SERVLET_NAME);
        servletMapping.setUrlPatterns(Arrays.asList("/_ah/channel/*", "/_ah/channel/"));
        return servletMapping;
    }

    private ResourceReferenceMetaData createInfinispanRef() {
        ResourceReferenceMetaData ref = new ResourceReferenceMetaData();
        ref.setResourceRefName("infinispan/container/capedwarf");
        ref.setJndiName("java:jboss/infinispan/container/capedwarf");
        ref.setType("org.infinispan.manager.EmbeddedCacheManager");
        return ref;
    }

    private void addResourceReference(WebMetaData webMetaData) {
        ResourceReferencesMetaData references = webMetaData.getResourceReferences();
        if (references == null) {
            references = new ResourceReferencesMetaData();
            EnvironmentRefsGroupMetaData env = webMetaData.getJndiEnvironmentRefsGroup();
            if (env == null) {
                env = new EnvironmentRefsGroupMetaData();
                webMetaData.setJndiEnvironmentRefsGroup(env);
            }
            env.setResourceReferences(references);
        }
        references.add(INFINISPAN_REF);
    }

    private SecurityConstraintMetaData createAdminServletSecurityConstraint() {
        SecurityConstraintMetaData scMetaData = new SecurityConstraintMetaData();
        scMetaData.setDisplayName("CapeDwarf admin console.");
        WebResourceCollectionsMetaData resourceCollections = new WebResourceCollectionsMetaData();
        WebResourceCollectionMetaData resourcePath = new WebResourceCollectionMetaData();
        resourcePath.setUrlPatterns(Arrays.asList(ADMIN_SERVLET_URL_MAPPING));
        resourceCollections.add(resourcePath);
        scMetaData.setResourceCollections(resourceCollections);

        AuthConstraintMetaData authConstraint = new AuthConstraintMetaData();
        authConstraint.setRoleNames(Arrays.asList("admin"));
        scMetaData.setAuthConstraint(authConstraint);

        UserDataConstraintMetaData userDataConstraint = new UserDataConstraintMetaData();
        userDataConstraint.setTransportGuarantee(TransportGuaranteeType.NONE);
        scMetaData.setUserDataConstraint(userDataConstraint);

        return scMetaData;
    }

    private List<SecurityConstraintMetaData> getSecurityConstraints(WebMetaData webMetaData) {
        List<SecurityConstraintMetaData> securityConstraints = webMetaData.getSecurityConstraints();
        if (securityConstraints == null) {
            securityConstraints = new ArrayList<SecurityConstraintMetaData>();
            webMetaData.setSecurityConstraints(securityConstraints);
        }
        return securityConstraints;
    }

    private SecurityRoleMetaData createAdminServletSecurityRole() {
        SecurityRoleMetaData roleMetaData = new SecurityRoleMetaData();
        roleMetaData.setName("admin");
        return roleMetaData;
    }

    private SecurityRolesMetaData getSecurityRoles(WebMetaData webMetaData) {
        SecurityRolesMetaData securityRoles = webMetaData.getSecurityRoles();
        if (securityRoles == null) {
            securityRoles = new SecurityRolesMetaData();
            webMetaData.setSecurityRoles(securityRoles);
        }
        return securityRoles;
    }

    private LoginConfigMetaData createAdminServletLogin() {
        LoginConfigMetaData configMetaData = new LoginConfigMetaData();
        configMetaData.setAuthMethod("BASIC");
        configMetaData.setRealmName("ApplicationRealm");
        return configMetaData;
    }

}
