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
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;

import javax.jms.Connection;

import org.jboss.as.capedwarf.api.Constants;
import org.jboss.as.capedwarf.api.Logger;
import org.jboss.as.capedwarf.deployment.CapedwarfAppIdParseProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfCDIExtensionProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfCacheProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfCleanupProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfDependenciesProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfDeploymentProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfEntityProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfInitializationProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfJPAProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfLoggingParseProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfMuxIdProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfPersistenceModificationProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfPostModuleJPAProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfWebCleanupProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfWebComponentsDeploymentProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfWeldParseProcessor;
import org.jboss.as.capedwarf.deployment.CapedwarfWeldProcessor;
import org.jboss.as.capedwarf.services.OptionalExecutorService;
import org.jboss.as.capedwarf.services.OptionalThreadFactoryService;
import org.jboss.as.capedwarf.services.ServletExecutorConsumerService;
import org.jboss.as.capedwarf.services.SimpleThreadsHandler;
import org.jboss.as.capedwarf.services.ThreadsHandler;
import org.jboss.as.clustering.jgroups.subsystem.ChannelService;
import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.logmanager.ConfigurationPersistence;
import org.jboss.as.naming.ManagedReferenceFactory;
import org.jboss.as.naming.ManagedReferenceInjector;
import org.jboss.as.naming.ServiceBasedNamingStore;
import org.jboss.as.naming.deployment.ContextNames;
import org.jboss.as.naming.service.BinderService;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.Services;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.server.deployment.module.TempFileProviderService;
import org.jboss.as.threads.ThreadsServices;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.config.FormatterConfiguration;
import org.jboss.logmanager.config.HandlerConfiguration;
import org.jboss.logmanager.config.LogContextConfiguration;
import org.jboss.logmanager.config.LoggerConfiguration;
import org.jboss.logmanager.formatters.PatternFormatter;
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
import org.jgroups.JChannel;

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

        context.addStep(new AbstractDeploymentChainStep() {
            @Override
            public void execute(DeploymentProcessorTarget processorTarget) {
                final ServiceTarget serviceTarget = context.getServiceTarget();

                final ServletExecutorConsumerService consumerService = addQueueConsumer(serviceTarget, newControllers);
                putChannelToJndi(serviceTarget, newControllers);
                final ThreadsHandler handler = new SimpleThreadsHandler();
                putExecutorToJndi(serviceTarget, newControllers, handler);
                putThreadFactoryToJndi(serviceTarget, newControllers, handler);

                final TempDir tempDir = createTempDir(serviceTarget, newControllers);

                addLogger();

                final int initialPhaseOrder = Math.min(Phase.PARSE_WEB_DEPLOYMENT, Phase.PARSE_PERSISTENCE_UNIT);
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, initialPhaseOrder - 20, new CapedwarfInitializationProcessor());
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, initialPhaseOrder - 15, new CapedwarfAppIdParseProcessor()); // adjust order as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, initialPhaseOrder - 10, new CapedwarfPersistenceModificationProcessor(tempDir)); // before persistence.xml parsing
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, Phase.PARSE_WEB_DEPLOYMENT + 1, new CapedwarfWebCleanupProcessor()); // right after web.xml parsing
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, Phase.PARSE_WEB_COMPONENTS - 1, new CapedwarfWebComponentsDeploymentProcessor(adminAuth));
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.PARSE, Phase.PARSE_WELD_DEPLOYMENT + 10, new CapedwarfWeldParseProcessor()); // before Weld web integration
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.DEPENDENCIES, Phase.DEPENDENCIES_WELD - 10, new CapedwarfWeldProcessor()); // before Weld
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.DEPENDENCIES, Phase.DEPENDENCIES_JPA - 10, new CapedwarfJPAProcessor()); // before default JPA processor
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.DEPENDENCIES, Phase.DEPENDENCIES_JPA - 5, new CapedwarfDeploymentProcessor(appengineAPI));
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_LOGGING_CONFIG - 1, new CapedwarfLoggingParseProcessor()); // just before AS logging configuration
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_WELD_PORTABLE_EXTENSIONS + 10, new CapedwarfCDIExtensionProcessor()); // after Weld portable extensions lookup
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_WELD_PORTABLE_EXTENSIONS + 20, new CapedwarfEntityProcessor()); // adjust as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.POST_MODULE, Phase.POST_MODULE_WELD_PORTABLE_EXTENSIONS + 30, new CapedwarfPostModuleJPAProcessor()); // after entity processor
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.INSTALL, Phase.INSTALL_MODULE_JNDI_BINDINGS - 3, new CapedwarfCacheProcessor()); // after module
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.INSTALL, Phase.INSTALL_MODULE_JNDI_BINDINGS - 2, new CapedwarfDependenciesProcessor()); // after logging
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.INSTALL, Phase.INSTALL_MODULE_JNDI_BINDINGS - 1, new CapedwarfMuxIdProcessor()); // adjust order as needed
                processorTarget.addDeploymentProcessor(Constants.CAPEDWARF, Phase.INSTALL, Phase.INSTALL_MODULE_JNDI_BINDINGS + 1, new CapedwarfCleanupProcessor(consumerService)); // adjust order as needed
            }
        }, OperationContext.Stage.RUNTIME);

    }

    protected static ServletExecutorConsumerService addQueueConsumer(final ServiceTarget serviceTarget, final List<ServiceController<?>> newControllers) {
        final ServletExecutorConsumerService consumerService = new ServletExecutorConsumerService();
        final ServiceBuilder<Connection> builder = serviceTarget.addService(ServletExecutorConsumerService.NAME, consumerService);
        builder.addDependency(ContextNames.bindInfoFor("java:/ConnectionFactory").getBinderServiceName(), ManagedReferenceFactory.class, consumerService.getFactory());
        builder.addDependency(ContextNames.bindInfoFor("java:/queue/" + Constants.CAPEDWARF).getBinderServiceName(), ManagedReferenceFactory.class, consumerService.getQueue());
        builder.addDependency(Services.JBOSS_SERVICE_MODULE_LOADER, ModuleLoader.class, consumerService.getLoader());
        builder.addDependency(ServiceName.JBOSS.append("messaging").append("default")); // depending on messaging sub-system impl details ...
        newControllers.add(builder.setInitialMode(ServiceController.Mode.ON_DEMAND).install());
        return consumerService;
    }

    protected void putChannelToJndi(ServiceTarget serviceTarget, List<ServiceController<?>> newControllers) {
        final ServiceName serviceName = ChannelService.getServiceName(Constants.CAPEDWARF);
        final String jndiName = Constants.CHANNEL_JNDI;
        final ContextNames.BindInfo bindInfo = Constants.CHANNEL_BIND_INFO;
        final BinderService binder = new BinderService(bindInfo.getBindName());
        final ServiceBuilder<ManagedReferenceFactory> binderBuilder = serviceTarget.addService(bindInfo.getBinderServiceName(), binder)
                .addAliases(ContextNames.JAVA_CONTEXT_SERVICE_NAME.append(jndiName))
                .addDependency(serviceName, JChannel.class, new ManagedReferenceInjector<JChannel>(binder.getManagedObjectInjector()))
                .addDependency(bindInfo.getParentContextServiceName(), ServiceBasedNamingStore.class, binder.getNamingStoreInjector())
                .setInitialMode(ServiceController.Mode.ON_DEMAND);
        newControllers.add(binderBuilder.install());
    }

    protected void putExecutorToJndi(ServiceTarget serviceTarget, List<ServiceController<?>> newControllers, ThreadsHandler handler) {
        final ServiceName realExecutor = ThreadsServices.executorName(Constants.CAPEDWARF);
        final ServiceName optionalExecutor = ServiceName.JBOSS.append(Constants.CAPEDWARF).append("OptionalExecutor");
        final OptionalExecutorService oes = new OptionalExecutorService(handler);
        final ServiceBuilder<Executor> executorServiceBuilder = serviceTarget.addService(optionalExecutor, oes);
        executorServiceBuilder.addDependency(ServiceBuilder.DependencyType.OPTIONAL, realExecutor, Executor.class, oes.getExecutorInjectedValue());
        executorServiceBuilder.setInitialMode(ServiceController.Mode.ON_DEMAND);
        newControllers.add(executorServiceBuilder.install());

        final String jndiName = Constants.EXECUTOR_JNDI;
        final ContextNames.BindInfo bindInfo = Constants.EXECUTOR_BIND_INFO;
        final BinderService binder = new BinderService(bindInfo.getBindName());
        final ServiceBuilder<ManagedReferenceFactory> binderBuilder = serviceTarget.addService(bindInfo.getBinderServiceName(), binder)
                .addAliases(ContextNames.JAVA_CONTEXT_SERVICE_NAME.append(jndiName))
                .addDependency(optionalExecutor, Executor.class, new ManagedReferenceInjector<Executor>(binder.getManagedObjectInjector()))
                .addDependency(bindInfo.getParentContextServiceName(), ServiceBasedNamingStore.class, binder.getNamingStoreInjector())
                .setInitialMode(ServiceController.Mode.ON_DEMAND);
        newControllers.add(binderBuilder.install());
    }

    protected void putThreadFactoryToJndi(ServiceTarget serviceTarget, List<ServiceController<?>> newControllers, ThreadsHandler handler) {
        final ServiceName realTF = ThreadsServices.threadFactoryName(Constants.CAPEDWARF);
        final ServiceName optionalTF = ServiceName.JBOSS.append(Constants.CAPEDWARF).append("OptionalThreadFactory");
        final OptionalThreadFactoryService otfs = new OptionalThreadFactoryService(handler);
        final ServiceBuilder<ThreadFactory> tfServiceBuilder = serviceTarget.addService(optionalTF, otfs);
        tfServiceBuilder.addDependency(ServiceBuilder.DependencyType.OPTIONAL, realTF, ThreadFactory.class, otfs.getThreadFactoryInjectedValue());
        tfServiceBuilder.setInitialMode(ServiceController.Mode.ON_DEMAND);
        newControllers.add(tfServiceBuilder.install());

        final String jndiName = Constants.TF_JNDI;
        final ContextNames.BindInfo bindInfo = Constants.TF_BIND_INFO;
        final BinderService binder = new BinderService(bindInfo.getBindName());
        final ServiceBuilder<ManagedReferenceFactory> binderBuilder = serviceTarget.addService(bindInfo.getBinderServiceName(), binder)
                .addAliases(ContextNames.JAVA_CONTEXT_SERVICE_NAME.append(jndiName))
                .addDependency(optionalTF, ThreadFactory.class, new ManagedReferenceInjector<ThreadFactory>(binder.getManagedObjectInjector()))
                .addDependency(bindInfo.getParentContextServiceName(), ServiceBasedNamingStore.class, binder.getNamingStoreInjector())
                .setInitialMode(ServiceController.Mode.ON_DEMAND);
        newControllers.add(binderBuilder.install());
    }

    protected static TempDir createTempDir(final ServiceTarget serviceTarget, final List<ServiceController<?>> newControllers) {
        final TempDir tempDir;
        try {
            tempDir = TempFileProviderService.provider().createTempDir(Constants.CAPEDWARF);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot create temp dir for CapeDwarf sub-system!", e);
        }

        final ServiceBuilder<TempDir> builder = serviceTarget.addService(ServiceName.JBOSS.append(Constants.CAPEDWARF).append("tempDir"), new Service<TempDir>() {
            @Override
            public void start(StartContext context) throws StartException {
            }

            @Override
            public void stop(StopContext context) {
                VFSUtils.safeClose(tempDir);
            }

            @Override
            public TempDir getValue() throws IllegalStateException, IllegalArgumentException {
                return tempDir;
            }
        });
        newControllers.add(builder.setInitialMode(ServiceController.Mode.ACTIVE).install());
        return tempDir;
    }

    protected static void addLogger() {
        final ConfigurationPersistence configPersistence = ConfigurationPersistence.getOrCreateConfigurationPersistence();
        final LogContextConfiguration config = configPersistence.getLogContextConfiguration();
        final String capedwarfLogger = Constants.CAPEDWARF.toUpperCase();
        if (!config.getHandlerNames().contains(capedwarfLogger)) {
            try {
                final HandlerConfiguration handlerConfig = config.addHandlerConfiguration("org.jboss.as.capedwarf", Logger.class.getName(), capedwarfLogger);
                // Formatter name doesn't seem to be needed
                final FormatterConfiguration fmtConfig;
                if (config.getFormatterNames().contains(capedwarfLogger)) {
                    fmtConfig = config.getFormatterConfiguration(capedwarfLogger);
                } else {
                    fmtConfig = config.addFormatterConfiguration(null, PatternFormatter.class.getName(), capedwarfLogger, "pattern");
                }
                fmtConfig.setPropertyValueString("pattern", CommonAttributes.FORMATTER.getDefaultValue().asString());
                handlerConfig.setFormatterName(capedwarfLogger);

                // Get the root logger, should always be created.
                final LoggerConfiguration loggerConfig = config.getLoggerConfiguration("");
                loggerConfig.addHandlerName(capedwarfLogger);
                config.commit();
            } finally {
                config.forget();
            }
        }
    }
}
