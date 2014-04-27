/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.capedwarf.extension;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.jms.Connection;

import com.sun.media.imageioimpl.plugins.tiff.TIFFImageReaderSpi;
import org.apache.http.client.HttpClient;
import org.geotoolkit.image.io.plugin.RawTiffImageReader;
import org.jboss.as.capedwarf.deployment.*;
import org.jboss.as.capedwarf.services.ComponentRegistryService;
import org.jboss.as.capedwarf.services.HttpClientService;
import org.jboss.as.capedwarf.services.OptionalExecutorService;
import org.jboss.as.capedwarf.services.OptionalThreadFactoryService;
import org.jboss.as.capedwarf.services.ServletExecutorConsumerService;
import org.jboss.as.capedwarf.services.SimpleThreadsHandler;
import org.jboss.as.capedwarf.services.ThreadsHandler;
import org.jboss.as.capedwarf.utils.CapedwarfProperties;
import org.jboss.as.capedwarf.utils.Constants;
import org.jboss.as.clustering.infinispan.subsystem.EmbeddedCacheManagerService;
import org.jboss.as.clustering.jgroups.subsystem.ChannelService;
import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.Services;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.server.deployment.module.TempFileProviderService;
import org.jboss.as.threads.ThreadsServices;
import org.jboss.as.txn.service.TxnServices;
import org.jboss.capedwarf.shared.components.ComponentRegistry;
import org.jboss.capedwarf.shared.components.Key;
import org.jboss.capedwarf.shared.components.Keys;
import org.jboss.capedwarf.shared.socket.CapedwarfSocketFactory;
import org.jboss.capedwarf.shared.url.URLHack;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.ModuleLoader;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.vfs.TempDir;
import org.jboss.vfs.VFSUtils;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ARCHIVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ENABLED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PERSISTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RUNTIME_NAME;

/**
 * Handler responsible for adding the subsystem resource to the model
 *
 * @author <a href="mailto:marko.luksa@gmail.com">Marko Luksa</a>
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author <a href="mailto:mlazar@redhat.com">Matej Lazar</a>
 */
class CapedwarfSubsystemAdd extends AbstractBoottimeAddStepHandler {

    static final CapedwarfSubsystemAdd INSTANCE = new CapedwarfSubsystemAdd();

    private boolean initialized;

    private CapedwarfSubsystemAdd() {
    }

    @Override
    protected void populateModel(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        super.populateModel(context, operation, resource);

        if (context.getProcessType() == ProcessType.STANDALONE_SERVER) {
            String rootDeployment = System.getProperty("rootDeployment");
            if (rootDeployment != null) {
                if (requiresRuntime(context)) {  // only add the step if we are going to actually deploy the war
                    PathAddress deploymentAddress = PathAddress.pathAddress(PathElement.pathElement(DEPLOYMENT, rootDeployment));
                    ModelNode op = Util.createOperation(ADD, deploymentAddress);
                    op.get(ENABLED).set(true);
                    op.get(PERSISTENT).set(false);
                    op.get(RUNTIME_NAME).set("ROOT.war");
                    op.get(CONTENT).add(getContentItem(rootDeployment));

                    ImmutableManagementResourceRegistration rootResourceRegistration = context.getRootResourceRegistration();
                    OperationStepHandler handler = rootResourceRegistration.getOperationHandler(deploymentAddress, ADD);

                    context.addStep(op, handler, OperationContext.Stage.MODEL);
                }
            }
        }
    }

    private ModelNode getContentItem(String explodedWar) {
        ModelNode contentItem = new ModelNode();
        File war = new File(explodedWar);
        contentItem.get(PATH).set(war.getAbsolutePath());
        contentItem.get(ARCHIVE).set(war.isFile());
        return contentItem;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
        CapedwarfDefinition.APPENGINE_API.validateAndSet(operation, model);
        CapedwarfDefinition.ADMIN_TGT.validateAndSet(operation, model);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void performBoottime(final OperationContext context, ModelNode operation, ModelNode model, ServiceVerificationHandler verificationHandler, final List<ServiceController<?>> newControllers)
        throws OperationFailedException {

        final ModelNode appEngineModel = CapedwarfDefinition.APPENGINE_API.resolveModelAttribute(context, model);
        final String appengineAPI = appEngineModel.isDefined() ? appEngineModel.asString() : null;

        final ModelNode adminTGTModel = CapedwarfDefinition.ADMIN_TGT.resolveModelAttribute(context, model);
        final String adminTGT = adminTGTModel.isDefined() ? adminTGTModel.asString() : null;

        final CapedwarfProperties properties = new CapedwarfProperties(System.getProperties());
        System.setProperties(properties); // override global properties, w/o synched code ...

        init();

        context.addStep(new AbstractDeploymentChainStep() {
            public void execute(DeploymentProcessorTarget processorTarget) {
                final ServiceTarget serviceTarget = context.getServiceTarget();

                addQueueConsumer(serviceTarget, newControllers);

                final ThreadsHandler handler = new SimpleThreadsHandler();
                putExecutorServiceToRegistry(serviceTarget, newControllers, handler);
                putThreadFactoryToRegistry(serviceTarget, newControllers, handler);
                addHttpClient(serviceTarget, newControllers);

                addServicesToRegistry(serviceTarget, newControllers);

                final TempDir tempDir = createTempDir(serviceTarget, newControllers);

                final int initialStructureOrder = Math.max(Math.max(Phase.STRUCTURE_WAR, Phase.STRUCTURE_WAR_DEPLOYMENT_INIT), Phase.STRUCTURE_EAR);
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.STRUCTURE, initialStructureOrder + 10, new CapedwarfInitializationProcessor());
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.STRUCTURE, initialStructureOrder + 20, new CapedwarfSubsystemProcessor());
                final int initialParseOrder = Math.min(Phase.PARSE_CREATE_COMPONENT_DESCRIPTIONS, Math.min(Phase.PARSE_WEB_DEPLOYMENT, Phase.PARSE_PERSISTENCE_UNIT));
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, initialParseOrder - 22, new CapedwarfCleanupProcessor()); // adjust order as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, initialParseOrder - 20, new CapedwarfEarAppInfoParseProcessor()); // adjust order as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, initialParseOrder - 18, new CapedwarfWebAppInfoParseProcessor()); // adjust order as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, initialParseOrder - 16, new CapedwarfAppIdProcessor()); // adjust order as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, initialParseOrder - 14, new CapedwarfXmlsParserProcessor()); // adjust order as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, initialParseOrder - 12, new CapedwarfCompatibilityParseProcessor()); // adjust order as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, initialParseOrder - 10, new CapedwarfPersistenceModificationProcessor(tempDir)); // before persistence.xml parsing
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, initialParseOrder - 8, new CapedwarfInboundMailProcessor()); // adjust order as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, Phase.PARSE_WEB_DEPLOYMENT + 1, new CapedwarfWebCleanupProcessor()); // right after web.xml parsing
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, Phase.PARSE_WEB_COMPONENTS - 1, new CapedwarfWebComponentsDeploymentProcessor(adminTGT));
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, Phase.PARSE_WELD_DEPLOYMENT + 10, new CapedwarfWeldParseProcessor()); // before Weld web integration
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.REGISTER, Phase.REGISTER_BUNDLE_INSTALL - 10, new CapedwarfJPAProcessor()); // before default JPA processor
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.DEPENDENCIES, Phase.DEPENDENCIES_WELD - 10, new CapedwarfWeldProcessor()); // before Weld
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.DEPENDENCIES, Phase.DEPENDENCIES_JPA - 10, new CapedwarfExcludeGaeApiProcessor(appengineAPI)); // before CapedwarfDeploymentProcessor
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.DEPENDENCIES, Phase.DEPENDENCIES_JPA - 7, new CapedwarfEarDeploymentProcessor()); // ear CL deps
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.DEPENDENCIES, Phase.DEPENDENCIES_JPA - 5, new CapedwarfDeploymentProcessor(appengineAPI)); // web CL deps
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.DEPENDENCIES, Phase.DEPENDENCIES_JPA - 1, new CapedwarfCacheEntriesTopProcessor()); // gather cache configs
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_INJECTION_ANNOTATION - 9, new CapedwarfModuleProcessor()); // right after module
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_INJECTION_ANNOTATION - 1, new CapedwarfEnvironmentProcessor(properties)); // after module
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_LOGGING_CONFIG - 1, new CapedwarfLoggingParseProcessor()); // just before AS logging configuration
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_WELD_PORTABLE_EXTENSIONS + 10, new CapedwarfCDIExtensionProcessor()); // after Weld portable extensions lookup
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_WELD_PORTABLE_EXTENSIONS + 20, new CapedwarfEntityProcessor()); // adjust as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_WELD_PORTABLE_EXTENSIONS + 30, new CapedwarfPostModuleJPAProcessor()); // after entity processor
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_WELD_PORTABLE_EXTENSIONS + 40, new CapedwarfSynchHackProcessor()); // after module, adjust as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_SAR_SERVICE_COMPONENT + 2, new CapedwarfCacheEntriesWebProcessor()); // gather cache configs
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_SAR_SERVICE_COMPONENT + 4, new CapedwarfShutdownProcessor()); // we still need module/CL
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_SAR_SERVICE_COMPONENT + 5, new CapedwarfSubCleanupProcessor()); // we still need module/CL
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_SAR_SERVICE_COMPONENT + 6, new CapedwarfInstanceInfoProcessor()); // web context processor
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_SAR_SERVICE_COMPONENT + 7, new CapedwarfWebContextProcessor()); // before web context lifecycle
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.INSTALL, Phase.INSTALL_WAR_DEPLOYMENT - 3, new CapedwarfCacheProcessor()); // after module
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.INSTALL, Phase.INSTALL_WAR_DEPLOYMENT - 1, new CapedwarfMuxIdProcessor()); // adjust order as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.INSTALL, Phase.INSTALL_MODULE_JNDI_BINDINGS - 2, new CapedwarfDependenciesProcessor()); // after logging
            }
        }, OperationContext.Stage.RUNTIME);

    }

    protected synchronized void init() throws OperationFailedException {
        if (initialized == false) {
            // register custom URLStreamHandlerFactory
            registerURLStreamHandlerFactory();
            // register custom Socket Factory
            registerSocketFactory();
            // handle .tif
            addTiffSupport();
            // read bin/capedwarf.conf
            readCapedwarfConf();
            // we're done
            initialized = true;
        }
    }

    protected static void registerURLStreamHandlerFactory() throws OperationFailedException {
        try {
            URLHack.setupHandler();
        } catch (Exception e) {
            throw new OperationFailedException(e.getMessage(), e);
        }
    }

    protected static void registerSocketFactory() throws OperationFailedException {
        try {
            Socket.setSocketImplFactory(CapedwarfSocketFactory.INSTANCE);
            DatagramSocket.setDatagramSocketImplFactory(CapedwarfSocketFactory.INSTANCE);
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage(), e);
        }
    }

    protected static void readCapedwarfConf() {
        final Properties properties = new Properties();

        try {
            File bin = new File(System.getProperty("jboss.home.dir"), "bin");
            File cdConf = new File(bin, "capedwarf.conf");
            if (cdConf.exists()) {
                try (InputStream is = new FileInputStream(cdConf)) {
                    properties.load(is);
                }
            }
        } catch (IOException e) {
            Logger.getLogger(CapedwarfSubsystemAdd.class.getName()).log(Level.WARNING, "Cannot read capedwarf.conf.", e);
        }

        ComponentRegistry.getInstance().setComponent(Keys.CONFIGURATION, properties);
    }

    protected static <T> void addComponentRegistryService(final ServiceTarget serviceTarget, final List<ServiceController<?>> newControllers, final Key<T> key, final ServiceName dependencyName) {
        final ComponentRegistryService<T> service = new ComponentRegistryService<>(key);
        final ServiceBuilder<T> builder = serviceTarget.addService(Constants.CAPEDWARF_NAME.append(String.valueOf(key.getSlot())), service);
        builder.addDependency(dependencyName, key.getType(), service.getInjectedValue());
        builder.setInitialMode(ServiceController.Mode.ON_DEMAND);
        newControllers.add(builder.install());
    }

    protected static void addQueueConsumer(final ServiceTarget serviceTarget, final List<ServiceController<?>> newControllers) {
        final ServletExecutorConsumerService consumerService = new ServletExecutorConsumerService();
        final ServiceBuilder<Connection> builder = serviceTarget.addService(ServletExecutorConsumerService.NAME, consumerService);
        builder.addDependency(ContextNames.bindInfoFor("java:/ConnectionFactory").getBinderServiceName(), ManagedReferenceFactory.class, consumerService.getFactory());
        builder.addDependency(ContextNames.bindInfoFor("java:/queue/" + Constants.CAPEDWARF).getBinderServiceName(), ManagedReferenceFactory.class, consumerService.getQueue());
        builder.addDependency(Services.JBOSS_SERVICE_MODULE_LOADER, ModuleLoader.class, consumerService.getLoader());
        builder.addDependency(ServiceName.JBOSS.append("messaging").append("default")); // depending on messaging sub-system impl details ...
        newControllers.add(builder.setInitialMode(ServiceController.Mode.ON_DEMAND).install());
    }

    protected static void putExecutorServiceToRegistry(ServiceTarget serviceTarget, List<ServiceController<?>> newControllers, ThreadsHandler handler) {
        final ServiceName realExecutor = ThreadsServices.executorName(Constants.CAPEDWARF);
        final ServiceName optionalExecutor = Constants.CAPEDWARF_NAME.append("OptionalExecutorService");
        final OptionalExecutorService oes = new OptionalExecutorService(handler);
        final ServiceBuilder<Executor> executorServiceBuilder = serviceTarget.addService(optionalExecutor, oes);
        executorServiceBuilder.addDependency(ServiceBuilder.DependencyType.OPTIONAL, realExecutor, ExecutorService.class, oes.getExecutorInjectedValue());
        executorServiceBuilder.setInitialMode(ServiceController.Mode.ON_DEMAND);
        newControllers.add(executorServiceBuilder.install());

        addComponentRegistryService(serviceTarget, newControllers, Keys.EXECUTOR_SERVICE, optionalExecutor);
    }

    protected static void putThreadFactoryToRegistry(ServiceTarget serviceTarget, List<ServiceController<?>> newControllers, ThreadsHandler handler) {
        final ServiceName realTF = ThreadsServices.threadFactoryName(Constants.CAPEDWARF);
        final ServiceName optionalTF = Constants.CAPEDWARF_NAME.append("OptionalThreadFactory");
        final OptionalThreadFactoryService otfs = new OptionalThreadFactoryService(handler);
        final ServiceBuilder<ThreadFactory> tfServiceBuilder = serviceTarget.addService(optionalTF, otfs);
        tfServiceBuilder.addDependency(ServiceBuilder.DependencyType.OPTIONAL, realTF, ThreadFactory.class, otfs.getThreadFactoryInjectedValue());
        tfServiceBuilder.setInitialMode(ServiceController.Mode.ON_DEMAND);
        newControllers.add(tfServiceBuilder.install());

        addComponentRegistryService(serviceTarget, newControllers, Keys.THREAD_FACTORY, optionalTF);
    }

    protected static void addHttpClient(final ServiceTarget serviceTarget, final List<ServiceController<?>> newControllers) {
        final HttpClientService service = new HttpClientService();
        final ServiceBuilder<HttpClient> builder = serviceTarget.addService(Constants.CAPEDWARF_NAME.append(String.valueOf(Keys.HTTP_CLIENT.getSlot())), service);
        builder.setInitialMode(ServiceController.Mode.ON_DEMAND);
        newControllers.add(builder.install());
    }

    protected static void addTiffSupport() {
        IIORegistry registry = IIORegistry.getDefaultInstance();
        registry.registerServiceProvider(new TIFFImageReaderSpi(), ImageReaderSpi.class);
        registry.registerServiceProvider(new RawTiffImageReader.Spi(), ImageReaderSpi.class);
    }

    protected static void addServicesToRegistry(ServiceTarget serviceTarget, List<ServiceController<?>> newControllers) {
        ServiceName mlServiceName = Services.JBOSS_SERVICE_MODULE_LOADER;
        addComponentRegistryService(serviceTarget, newControllers, Keys.MODULE_LOADER, mlServiceName);

        ServiceName chServiceName = ChannelService.getServiceName(Constants.CAPEDWARF);
        addComponentRegistryService(serviceTarget, newControllers, Keys.CHANNEL, chServiceName);

        ServiceName tmServiceName = TxnServices.JBOSS_TXN_TRANSACTION_MANAGER;
        addComponentRegistryService(serviceTarget, newControllers, Keys.TM, tmServiceName);

        ServiceName utServiceName = TxnServices.JBOSS_TXN_USER_TRANSACTION;
        addComponentRegistryService(serviceTarget, newControllers, Keys.USER_TX, utServiceName);

        ServiceName cmServiceName = EmbeddedCacheManagerService.getServiceName(Constants.CAPEDWARF);
        addComponentRegistryService(serviceTarget, newControllers, Keys.CACHE_MANAGER, cmServiceName);

        ServiceName mailServiceName = ServiceName.JBOSS.append("mail-session").append("default");
        addComponentRegistryService(serviceTarget, newControllers, Keys.MAIL_SESSION, mailServiceName);
    }

    protected static TempDir createTempDir(final ServiceTarget serviceTarget, final List<ServiceController<?>> newControllers) {
        final TempDir tempDir;
        try {
            tempDir = TempFileProviderService.provider().createTempDir(Constants.CAPEDWARF);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot create temp dir for CapeDwarf sub-system!", e);
        }

        final ServiceBuilder<TempDir> builder = serviceTarget.addService(Constants.CAPEDWARF_NAME.append("tempDir"), new Service<TempDir>() {
            public void start(StartContext context) throws StartException {
            }

            public void stop(StopContext context) {
                VFSUtils.safeClose(tempDir);
            }

            public TempDir getValue() throws IllegalStateException, IllegalArgumentException {
                return tempDir;
            }
        });
        newControllers.add(builder.setInitialMode(ServiceController.Mode.ACTIVE).install());
        return tempDir;
    }
}
