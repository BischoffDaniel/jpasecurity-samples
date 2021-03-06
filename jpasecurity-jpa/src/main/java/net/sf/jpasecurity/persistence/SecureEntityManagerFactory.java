/*
 * Copyright 2008 - 2011 Arne Limburg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 */
package net.sf.jpasecurity.persistence;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnitUtil;
import javax.persistence.spi.PersistenceUnitInfo;

import net.sf.jpasecurity.AlwaysPermittingAccessManager;
import net.sf.jpasecurity.BeanLoader;
import net.sf.jpasecurity.SecurityUnit;
import net.sf.jpasecurity.configuration.Configuration;
import net.sf.jpasecurity.configuration.ConfigurationReceiver;
import net.sf.jpasecurity.configuration.SecurityContextReceiver;
import net.sf.jpasecurity.entity.AbstractSecureObjectManager;
import net.sf.jpasecurity.entity.DefaultBeanStore;
import net.sf.jpasecurity.entity.DefaultSecureObjectLoader;
import net.sf.jpasecurity.entity.DefaultSecureObjectManager;
import net.sf.jpasecurity.entity.SecureObjectLoader;
import net.sf.jpasecurity.jpa.JpaBeanLoader;
import net.sf.jpasecurity.jpa.JpaSecurityUnit;
import net.sf.jpasecurity.mapping.MappingInformation;
import net.sf.jpasecurity.mapping.MappingInformationReceiver;
import net.sf.jpasecurity.persistence.mapping.OrmXmlParser;

/**
 * This class is a factory that creates {@link net.sf.jpasecurity.persistence.SecureEntityManager}s.
 * @author Arne Limburg
 */
public class SecureEntityManagerFactory extends DelegatingEntityManagerFactory implements EntityManagerFactory {

    private MappingInformation mappingInformation;
    private Configuration configuration;
    private boolean configurationInitialized;
    private PersistenceUnitUtil securePersistenceUnitUtil;

    protected SecureEntityManagerFactory(EntityManagerFactory entityManagerFactory,
                                         PersistenceUnitInfo persistenceUnitInfo,
                                         Map<String, Object> properties,
                                         Configuration configuration) {
        super(entityManagerFactory);
        if (entityManagerFactory == null) {
            throw new IllegalArgumentException("entityManagerFactory may not be null");
        }
        if (persistenceUnitInfo == null) {
            throw new IllegalArgumentException("persistenceUnitInfo may not be null");
        }
        if (configuration == null) {
            throw new IllegalArgumentException("configuration may not be null");
        }
        this.configuration = configuration;
        SecurityUnit securityUnitInformation = new JpaSecurityUnit(persistenceUnitInfo);
        OrmXmlParser jpaParser = new OrmXmlParser(securityUnitInformation,
                                                  configuration.getPropertyAccessStrategyFactory(),
                                                  configuration.getExceptionFactory());
        mappingInformation = jpaParser.parse();
        Map<String, Object> persistenceProperties
            = new HashMap<String, Object>((Map<String, Object>)(Map<?, Object>)persistenceUnitInfo.getProperties());
        if (properties != null) {
            persistenceProperties.putAll(properties);
        }
        BeanLoader beanLoader = new JpaBeanLoader(entityManagerFactory.getPersistenceUnitUtil());
        AbstractSecureObjectManager objectManager
            = new DefaultSecureObjectManager(mappingInformation,
                                             new DefaultBeanStore(),
                                             new AlwaysPermittingAccessManager(),
                                             configuration);
        SecureObjectLoader secureObjectLoader = new DefaultSecureObjectLoader(beanLoader, objectManager);
        securePersistenceUnitUtil = new SecurePersistenceUnitUtil(secureObjectLoader);
    }

    protected Configuration getConfiguration(Map<String, Object> persistenceProperties) {
        if (!configurationInitialized) {
            injectPersistenceInformation(persistenceProperties);
            configurationInitialized = true;
        }
        return configuration;
    }

    public EntityManager createEntityManager() {
        return createSecureEntityManager(super.createEntityManager(), Collections.<String, Object>emptyMap());
    }

    public EntityManager createEntityManager(@SuppressWarnings("rawtypes") Map map) {
        return createSecureEntityManager(super.createEntityManager(map), map);
    }

    public void close() {
        configuration = null;
        super.close();
    }

    public PersistenceUnitUtil getPersistenceUnitUtil() {
        return securePersistenceUnitUtil;
    }

    protected EntityManager createSecureEntityManager(EntityManager entityManager, Map<String, Object> properties) {
        return new DefaultSecureEntityManager(this,
                                              entityManager,
                                              new Configuration(getConfiguration(properties), properties),
                                              mappingInformation);
    }

    private void injectPersistenceInformation(Map<String, Object> persistenceProperties) {
        if (persistenceProperties != null) {
            persistenceProperties = Collections.unmodifiableMap(persistenceProperties);
        }
        injectPersistenceInformation(configuration.getSecurityContext(), persistenceProperties);
        injectPersistenceInformation(configuration.getAccessRulesProvider(), persistenceProperties);
    }

    private void injectPersistenceInformation(Object injectionTarget, Map<String, Object> persistenceProperties) {
        if (injectionTarget instanceof MappingInformationReceiver) {
            MappingInformationReceiver persistenceInformationReceiver
                = (MappingInformationReceiver)injectionTarget;
            persistenceInformationReceiver.setMappingProperties(persistenceProperties);
            persistenceInformationReceiver.setMappingInformation(mappingInformation);
        }
        if (injectionTarget instanceof SecurityContextReceiver) {
            SecurityContextReceiver securityContextReceiver = (SecurityContextReceiver)injectionTarget;
            securityContextReceiver.setSecurityContext(configuration.getSecurityContext());
        }
        if (injectionTarget instanceof ConfigurationReceiver) {
            ConfigurationReceiver configurationReceiver = (ConfigurationReceiver)injectionTarget;
            configurationReceiver.setConfiguration(configuration);
        }
    }

    protected MappingInformation getMappingInformation() {
        return mappingInformation;
    }
}
