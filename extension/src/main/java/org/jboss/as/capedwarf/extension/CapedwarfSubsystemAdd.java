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

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import javax.jms.Connection;

import org.apache.http.client.HttpClient;
import org.jboss.as.capedwarf.deployment.CapedwarfAppInfoParseProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfCDIExtensionProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfCacheProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfCleanupProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfDependenciesProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfDeploymentProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfEntityProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfEnvironmentProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfInitializationProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfJPAProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfLoggingParseProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfMuxIdProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfPersistenceModificationProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfPostModuleJPAProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfSubsystemProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfSynchHackProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfWebCleanupProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfWebComponentsDeploymentProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfWebContextProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfWeldParseProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfWeldProcessor;
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
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.Services;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.server.deployment.module.TempFileProviderService;
import org.jboss.as.threads.ThreadsServices;
import org.jboss.as.txn.service.TxnServices;
import org.jboss.capedwarf.shared.components.Key;
import org.jboss.capedwarf.shared.components.Keys;
import org.jboss.capedwarf.shared.url.URLHack;
import org.jboss.dmr.ModelNode;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
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

/**
 * Handler responsible for adding the subsystem resource to the model
 *
 * @author <a href="mailto:marko.luksa@gmail.com">Marko Luksa</a>
 * @author <a href="mailto:ales.justin@jboss.org">Ales Justin</a>
 * @author <a href="mailto:mlazar@redhat.com">Matej Lazar</a>
 */
class CapedwarfSubsystemAdd extends AbstractBoottimeAddStepHandler {

    static final CapedwarfSubsystemAdd INSTANCE = new CapedwarfSubsystemAdd();

    private CapedwarfSubsystemAdd() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
        CapedwarfDefinition.APPENGINE_API.validateAndSet(operation, model);
        CapedwarfDefinition.ADMIN_AUTH.validateAndSet(operation, model);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void performBoottime(final OperationContext context, ModelNode operation, ModelNode model,
                                ServiceVerificationHandler verificationHandler, final List<ServiceController<?>> newControllers)
            throws OperationFailedException {

        final ModelNode appEngineModel = CapedwarfDefinition.APPENGINE_API.resolveModelAttribute(context, model);
        final String appengineAPI = appEngineModel.isDefined() ? appEngineModel.asString() : null;

        final ModelNode adminAuthModel = CapedwarfDefinition.ADMIN_AUTH.resolveModelAttribute(context, model);
        final boolean adminAuth = adminAuthModel.isDefined() && adminAuthModel.asBoolean();

        final CapedwarfProperties properties = new CapedwarfProperties(System.getProperties());
        System.setProperties(properties); // override global properties, w/o synched code ...

        // register custom URLStreamHandlerFactory
        registerURLStreamHandlerFactory();

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

                final int initialStructureOrder = Math.max(Phase.STRUCTURE_WAR, Phase.STRUCTURE_WAR_DEPLOYMENT_INIT);
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.STRUCTURE, initialStructureOrder + 10, new CapedwarfInitializationProcessor());
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.STRUCTURE, initialStructureOrder + 20, new CapedwarfSubsystemProcessor());
                final int initialParseOrder = Math.min(Phase.PARSE_WEB_DEPLOYMENT, Phase.PARSE_PERSISTENCE_UNIT);
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, initialParseOrder - 15, new CapedwarfAppInfoParseProcessor()); // adjust order as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, initialParseOrder - 10, new CapedwarfPersistenceModificationProcessor(tempDir)); // before persistence.xml parsing
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, Phase.PARSE_WEB_DEPLOYMENT + 1, new CapedwarfWebCleanupProcessor()); // right after web.xml parsing
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, Phase.PARSE_WEB_COMPONENTS - 1, new CapedwarfWebComponentsDeploymentProcessor(adminAuth));
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, Phase.PARSE_WELD_DEPLOYMENT + 10, new CapedwarfWeldParseProcessor()); // before Weld web integration
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.DEPENDENCIES, Phase.DEPENDENCIES_WELD - 10, new CapedwarfWeldProcessor()); // before Weld
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.DEPENDENCIES, Phase.DEPENDENCIES_JPA - 10, new CapedwarfJPAProcessor()); // before default JPA processor
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.DEPENDENCIES, Phase.DEPENDENCIES_JPA - 5, new CapedwarfDeploymentProcessor(appengineAPI));
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_INJECTION_ANNOTATION - 1, new CapedwarfEnvironmentProcessor(properties)); // after module
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_LOGGING_CONFIG - 1, new CapedwarfLoggingParseProcessor()); // just before AS logging configuration
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_WELD_PORTABLE_EXTENSIONS + 10, new CapedwarfCDIExtensionProcessor()); // after Weld portable extensions lookup
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_WELD_PORTABLE_EXTENSIONS + 20, new CapedwarfEntityProcessor()); // adjust as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_WELD_PORTABLE_EXTENSIONS + 30, new CapedwarfPostModuleJPAProcessor()); // after entity processor
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_WELD_PORTABLE_EXTENSIONS + 40, new CapedwarfSynchHackProcessor()); // after module, adjust as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_SAR_SERVICE_COMPONENT + 1, new CapedwarfCleanupProcessor()); // we still need module/CL
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.INSTALL, Phase.INSTALL_WAR_DEPLOYMENT - 3, new CapedwarfCacheProcessor()); // after module
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.INSTALL, Phase.INSTALL_WAR_DEPLOYMENT - 2, new CapedwarfMuxIdProcessor()); // adjust order as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.INSTALL, Phase.INSTALL_WAR_DEPLOYMENT - 1, new CapedwarfWebContextProcessor()); // before web context lifecycle
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.INSTALL, Phase.INSTALL_MODULE_JNDI_BINDINGS - 2, new CapedwarfDependenciesProcessor()); // after logging
            }
        }, OperationContext.Stage.RUNTIME);

    }

    protected static void registerURLStreamHandlerFactory() throws OperationFailedException {
        try {
            URLHack.inLock(new Callable<Void>() {
                public Void call() throws Exception {
                    // make sure we clear these protocols
                    URLHack.removeHandlerNoLock("http");
                    URLHack.removeHandlerNoLock("https");
                    // register our custom url stream handler factory
                    ModuleLoader loader = Module.getBootModuleLoader();
                    Module capedwarf = loader.loadModule(ModuleIdentifier.create("org.jboss.capedwarf"));
                    Module.registerURLStreamHandlerFactoryModule(capedwarf);
                    return null;
                }
            });
        } catch (Exception e) {
            throw new OperationFailedException(e.getMessage());
        }
    }

    protected static <T> void addComponentRegistryService(final ServiceTarget serviceTarget, final List<ServiceController<?>> newControllers, final Key<T> key, final ServiceName dependencyName) {
        final ComponentRegistryService<T> service = new ComponentRegistryService<T>(key);
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

    protected static void addServicesToRegistry(ServiceTarget serviceTarget, List<ServiceController<?>> newControllers) {
        ServiceName chServiceName = ChannelService.getServiceName(Constants.CAPEDWARF);
        addComponentRegistryService(serviceTarget, newControllers, Keys.CHANNEL, chServiceName);

        ServiceName tmServiceName = TxnServices.JBOSS_TXN_TRANSACTION_MANAGER;
        addComponentRegistryService(serviceTarget, newControllers, Keys.TM, tmServiceName);

        ServiceName utServiceName = TxnServices.JBOSS_TXN_USER_TRANSACTION;
        addComponentRegistryService(serviceTarget, newControllers, Keys.USER_TX, utServiceName);

        ServiceName cmServiceName = EmbeddedCacheManagerService.getServiceName(Constants.CAPEDWARF);
        addComponentRegistryService(serviceTarget, newControllers, Keys.CACHE_MANAGER, cmServiceName);

        ServiceName mailServiceName = ServiceName.JBOSS.append("mail-session").append("java:jboss/mail/Default"); // TODO
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
