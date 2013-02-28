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
import org.jboss.metadata.web.jboss.JBoss70WebMetaData;
import org.jboss.metadata.web.jboss.JBossWebMetaData;
import org.jboss.metadata.web.jboss.ValveMetaData;
import org.jboss.metadata.web.spec.AuthConstraintMetaData;
import org.jboss.metadata.web.spec.DispatcherType;
import org.jboss.metadata.web.spec.FilterMappingMetaData;
import org.jboss.metadata.web.spec.FilterMetaData;
import org.jboss.metadata.web.spec.FiltersMetaData;
import org.jboss.metadata.web.spec.ListenerMetaData;
import org.jboss.metadata.web.spec.LoginConfigMetaData;
import org.jboss.metadata.web.spec.MultipartConfigMetaData;
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
    private static final String UPLOAD_SERVLET_NAME = "UploadServlet";
    private static final String IMAGE_SERVLET_NAME = "ImageServlet";
    private static final String DEFERRED_TASK_SERVLET_NAME = "DeferredTaskServlet";
    private static final String[] ADMIN_SERVLET_URL_MAPPING = {"/_ah/admin/*", "/_ah/admin/"};
    private static final String[] AUTH_SERVLET_URL_MAPPING = {"/_ah/auth/*"};
    private static final String[] CHANNEL_SERVLET_URL_MAPPING = {"/_ah/channel/*", "/_ah/channel/"};
    private static final String[] UPLOAD_SERVLET_URL_MAPPING = {"/_ah/blobstore/upload"};
    private static final String[] IMAGE_SERVLET_URL_MAPPING = {"/_ah/image/*"};
    private static final String[] DEFERRED_TASK_SERVLET_URL_MAPPING = {"/_ah/queue/__deferred__"};

    private final ListenerMetaData GAE_LISTENER;
    private final ListenerMetaData CDI_LISTENER;
    private final ListenerMetaData CDAS_LISTENER;
    private final FilterMetaData SINGLE_THREAD_FILTER;
    private final FilterMetaData GAE_FILTER;
    private final FilterMappingMetaData SINGLE_THREAD_FILTER_MAPPING;
    private final FilterMappingMetaData GAE_FILTER_MAPPING;
    private final ServletMetaData AUTH_SERVLET;
    private final ServletMetaData ADMIN_SERVLET;
    private final ServletMetaData CHANNEL_SERVLET;
    private final ServletMetaData UPLOAD_SERVLET;
    private final ServletMetaData IMAGE_SERVLET;
    private final ServletMetaData DEFERRED_TASK_SERVLET;
    private final ServletMappingMetaData AUTH_SERVLET_MAPPING;
    private final ServletMappingMetaData ADMIN_SERVLET_MAPPING;
    private final ServletMappingMetaData CHANNEL_SERVLET_MAPPING;
    private final ServletMappingMetaData UPLOAD_SERVLET_MAPPING;
    private final ServletMappingMetaData IMAGE_SERVLET_MAPPING;
    private final ServletMappingMetaData DEFERRED_TASK_SERVLET_MAPPING;
    private final ResourceReferenceMetaData INFINISPAN_REF;
    private final SecurityConstraintMetaData ADMIN_SERVLET_CONSTRAINT;
    private final LoginConfigMetaData ADMIN_SERVLET_CONFIG;
    private final SecurityRoleMetaData ADMIN_SERVLET_ROLE;

    private ValveMetaData AUTH_VALVE;

    private final boolean adminAuth;

    public CapedwarfWebComponentsDeploymentProcessor(boolean adminAuth) {
        this.adminAuth = adminAuth;

        GAE_LISTENER = createListener("org.jboss.capedwarf.appidentity.GAEListener");
        CDI_LISTENER = createListener("org.jboss.capedwarf.appidentity.CDIListener");
        CDAS_LISTENER = createListener("org.jboss.capedwarf.shared.servlet.CapedwarfListener");

        GAE_FILTER = createFilter(GAE_FILTER_NAME, "org.jboss.capedwarf.appidentity.GAEFilter");
        SINGLE_THREAD_FILTER = createSingleThreadFilter(1); // TODO - per deployment #
        GAE_FILTER_MAPPING = createFilterMapping(GAE_FILTER_NAME, "/*");
        SINGLE_THREAD_FILTER_MAPPING = createFilterMapping(SINGLE_THREAD_FILTER_NAME, "/*");

        AUTH_SERVLET = createServlet(AUTH_SERVLET_NAME, "org.jboss.capedwarf.users.AuthServlet");
        ADMIN_SERVLET = createServlet(ADMIN_SERVLET_NAME, "org.jboss.capedwarf.admin.AdminServlet");
        CHANNEL_SERVLET = createServlet(CHANNEL_SERVLET_NAME, "org.jboss.capedwarf.channel.servlet.ChannelServlet");
        UPLOAD_SERVLET = createUploadServlet();
        IMAGE_SERVLET = createServlet(IMAGE_SERVLET_NAME, "org.jboss.capedwarf.images.ImageServlet");
        DEFERRED_TASK_SERVLET = createServlet(DEFERRED_TASK_SERVLET_NAME, "com.google.apphosting.utils.servlet.DeferredTaskServlet");

        AUTH_SERVLET_MAPPING = createServletMapping(AUTH_SERVLET_NAME, AUTH_SERVLET_URL_MAPPING);
        ADMIN_SERVLET_MAPPING = createServletMapping(ADMIN_SERVLET_NAME, ADMIN_SERVLET_URL_MAPPING);
        CHANNEL_SERVLET_MAPPING = createServletMapping(CHANNEL_SERVLET_NAME, CHANNEL_SERVLET_URL_MAPPING);
        UPLOAD_SERVLET_MAPPING = createServletMapping(UPLOAD_SERVLET_NAME, UPLOAD_SERVLET_URL_MAPPING);
        IMAGE_SERVLET_MAPPING = createServletMapping(IMAGE_SERVLET_NAME, IMAGE_SERVLET_URL_MAPPING);
        DEFERRED_TASK_SERVLET_MAPPING = createServletMapping(DEFERRED_TASK_SERVLET_NAME, DEFERRED_TASK_SERVLET_URL_MAPPING);

        INFINISPAN_REF = createInfinispanRef();

        ADMIN_SERVLET_CONSTRAINT = createAdminServletSecurityConstraint();
        ADMIN_SERVLET_CONFIG = createAdminServletLogin();
        ADMIN_SERVLET_ROLE = createAdminServletSecurityRole();

        AUTH_VALVE = createAuthValve();
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

            addServletAndMapping(webMetaData, AUTH_SERVLET, AUTH_SERVLET_MAPPING);
            addServletAndMapping(webMetaData, ADMIN_SERVLET, ADMIN_SERVLET_MAPPING);
            addServletAndMapping(webMetaData, CHANNEL_SERVLET, CHANNEL_SERVLET_MAPPING);
            addServletAndMapping(webMetaData, UPLOAD_SERVLET, UPLOAD_SERVLET_MAPPING);
            addServletAndMapping(webMetaData, IMAGE_SERVLET, IMAGE_SERVLET_MAPPING);
            addServletAndMapping(webMetaData, DEFERRED_TASK_SERVLET, DEFERRED_TASK_SERVLET_MAPPING);

            replaceRemoteApiServlet(webMetaData);

            addResourceReference(webMetaData);

            if (adminAuth) {
                getSecurityConstraints(webMetaData).add(ADMIN_SERVLET_CONSTRAINT);
                getSecurityRoles(webMetaData).add(ADMIN_SERVLET_ROLE);
                webMetaData.setLoginConfig(ADMIN_SERVLET_CONFIG);
            }
        }
    }

    protected JBossWebMetaData createJBossWebMetaData(Type type) {
        return (type == Type.JBOSS) ? new JBoss70WebMetaData() : null;
    }

    @Override
    protected void doDeploy(DeploymentUnit unit, JBossWebMetaData webMetaData, Type type) {
        if (type == Type.JBOSS) {
            List<ValveMetaData> valves = getValves(webMetaData);
            valves.add(0, AUTH_VALVE);
        }
    }

    private void addServletAndMapping(WebMetaData webMetaData, ServletMetaData servletMetaData, ServletMappingMetaData servletMappingMetaData) {
        getServlets(webMetaData).add(servletMetaData);
        getServletMappings(webMetaData).add(servletMappingMetaData);
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

    private ListenerMetaData createListener(String listenerClass) {
        ListenerMetaData listener = new ListenerMetaData();
        listener.setListenerClass(listenerClass);
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

    private FilterMetaData createFilter(String filterName, String filterClass) {
        FilterMetaData filter = new FilterMetaData();
        filter.setFilterName(filterName);
        filter.setFilterClass(filterClass);
        return filter;
    }

    private FilterMetaData createSingleThreadFilter(int maxConcurrentRequests) {
        FilterMetaData filter = createFilter(SINGLE_THREAD_FILTER_NAME, "org.jboss.capedwarf.common.singlethread.SingleThreadFilter");
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

    private FilterMappingMetaData createFilterMapping(String filterName, String urlPattern) {
        FilterMappingMetaData filterMapping = new FilterMappingMetaData();
        filterMapping.setFilterName(filterName);
        filterMapping.setUrlPatterns(Collections.singletonList(urlPattern));
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

    private ServletMetaData createServlet(String servletName, String servletClass) {
        ServletMetaData servlet = new ServletMetaData();
        servlet.setServletName(servletName);
        servlet.setServletClass(servletClass);
        servlet.setEnabled(true);
        return servlet;
    }

    private ServletMetaData createUploadServlet() {
        ServletMetaData servlet = createServlet(UPLOAD_SERVLET_NAME, "org.jboss.capedwarf.blobstore.UploadServlet");
        servlet.setMultipartConfig(new MultipartConfigMetaData());
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

    private ServletMappingMetaData createServletMapping(String servletName, String[] urlPatterns) {
        ServletMappingMetaData servletMapping = new ServletMappingMetaData();
        servletMapping.setServletName(servletName);
        servletMapping.setUrlPatterns(Arrays.asList(urlPatterns));
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

    private List<ValveMetaData> getValves(JBossWebMetaData webMetaData) {
        List<ValveMetaData> valves = webMetaData.getValves();
        if (valves == null) {
            valves = new ArrayList<ValveMetaData>();
            webMetaData.setValves(valves);
        }
        return valves;
    }

    private ValveMetaData createAuthValve() {
        ValveMetaData vmd = new ValveMetaData();
        vmd.setValveClass("org.jboss.capedwarf.users.CapedwarfAuthenticator");
        return vmd;
    }
}
