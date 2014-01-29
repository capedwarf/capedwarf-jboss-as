/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
package org.jboss.as.capedwarf.deployment;

import java.util.List;
import java.util.logging.Logger;

import org.jboss.as.ejb3.deployment.EjbDeploymentAttachmentKeys;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.capedwarf.shared.config.AppEngineWebXml;
import org.jboss.capedwarf.shared.config.CapedwarfConfiguration;
import org.jboss.capedwarf.shared.config.InboundMailAccount;
import org.jboss.capedwarf.shared.config.InboundServices;
import org.jboss.metadata.ejb.jboss.ejb3.JBossGenericBeanMetaData;
import org.jboss.metadata.ejb.spec.ActivationConfigMetaData;
import org.jboss.metadata.ejb.spec.ActivationConfigPropertiesMetaData;
import org.jboss.metadata.ejb.spec.ActivationConfigPropertyMetaData;
import org.jboss.metadata.ejb.spec.EjbJarMetaData;
import org.jboss.metadata.ejb.spec.EjbJarVersion;
import org.jboss.metadata.ejb.spec.EjbType;
import org.jboss.metadata.ejb.spec.EnterpriseBeansMetaData;

/**
 * @author <a href="mailto:mluksa@redhat.com">Marko Luksa</a>
 */
public class CapedwarfInboundMailProcessor extends CapedwarfWebDeploymentUnitProcessor {
    private final Logger log = Logger.getLogger(getClass().getName());

    protected void doDeploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();

        AppEngineWebXml appEngineWebXml = deploymentUnit.getAttachment(CapedwarfAttachments.APP_ENGINE_WEB_XML);
        CapedwarfConfiguration config = deploymentUnit.getAttachment(CapedwarfAttachments.CAPEDWARF_WEB_XML);
        List<InboundMailAccount> inboundMailAccounts = config.getInboundMailAccounts();

        if (appEngineWebXml.isInboundServiceEnabled(InboundServices.Service.mail) || appEngineWebXml.isInboundServiceEnabled(InboundServices.Service.mail_bounce)) {
            if (inboundMailAccounts.isEmpty()) {
                log.warning("The inbound mail service is enabled in appengine-web.xml, but there are no inbound mail " +
                    "accounts defined in capedwarf-web.xml. Inbound mail service will not be activated.");
            } else {
                for (InboundMailAccount account : inboundMailAccounts) {
                    configureAccount(deploymentUnit, account);
                }
            }
        } else {
            if (!inboundMailAccounts.isEmpty()) {
                log.warning("There are inbound mail accounts defined in capedwarf-web.xml, but the inbound mail service " +
                    "is not enabled in appengine-web.xml. Inbound mail service will not be activated.");
            }
        }
    }

    private void configureAccount(DeploymentUnit deploymentUnit, InboundMailAccount account) {
        EjbJarMetaData ejbJarMetaData = deploymentUnit.getAttachment(EjbDeploymentAttachmentKeys.EJB_JAR_METADATA);
        if (ejbJarMetaData == null) {
            ejbJarMetaData = new EjbJarMetaData(EjbJarVersion.EJB_3_2);
            deploymentUnit.putAttachment(EjbDeploymentAttachmentKeys.EJB_JAR_METADATA, ejbJarMetaData);
        }
        EnterpriseBeansMetaData enterpriseBeans = ejbJarMetaData.getEnterpriseBeans();
        if (enterpriseBeans == null) {
            enterpriseBeans = new EnterpriseBeansMetaData();
            ejbJarMetaData.setEnterpriseBeans(enterpriseBeans);
        }
        enterpriseBeans.add(createMdbMetaData(account));
    }

    private JBossGenericBeanMetaData createMdbMetaData(InboundMailAccount account) {
        JBossGenericBeanMetaData mdb = new JBossGenericBeanMetaData();
        mdb.setName("InboundMailMDB_" + account.getHost() + "_" + account.getUser());
        mdb.setEjbType(EjbType.MESSAGE_DRIVEN);
        mdb.setEjbClass("org.jboss.capedwarf.mail.CapedwarfInboundMailMDB");
        mdb.setMessagingType("org.wildfly.mail.ra.MailListener");

        ActivationConfigPropertiesMetaData properties = new ActivationConfigPropertiesMetaData();
        properties.add(createConfigProperty("mailServer", account.getHost()));
        properties.add(createConfigProperty("userName", account.getUser()));
        properties.add(createConfigProperty("password", account.getPassword()));
        properties.add(createConfigProperty("storeProtocol", account.getProtocol()));
        properties.add(createConfigProperty("mailFolder", account.getFolder()));
        properties.add(createConfigProperty("pollingInterval", String.valueOf(account.getPollingInterval())));

        ActivationConfigMetaData activationConfig = new ActivationConfigMetaData();
        activationConfig.setActivationConfigProperties(properties);
        mdb.setActivationConfig(activationConfig);
        return mdb;
    }

    private ActivationConfigPropertyMetaData createConfigProperty(String name, String value) {
        ActivationConfigPropertyMetaData configProperty = new ActivationConfigPropertyMetaData();
        configProperty.setActivationConfigPropertyName(name);
        configProperty.setValue(value);
        return configProperty;
    }
}
