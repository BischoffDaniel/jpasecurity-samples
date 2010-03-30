/*
 * Copyright 2010 Arne Limburg
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
package net.sf.jpasecurity.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.EntityManager;
import javax.persistence.LockModeType;
import javax.persistence.Query;

import net.sf.jpasecurity.AccessManager;
import net.sf.jpasecurity.AccessType;
import net.sf.jpasecurity.SecureEntity;
import net.sf.jpasecurity.mapping.ClassMappingInformation;
import net.sf.jpasecurity.mapping.CollectionValuedRelationshipMappingInformation;
import net.sf.jpasecurity.mapping.MappingInformation;
import net.sf.jpasecurity.mapping.PropertyMappingInformation;
import net.sf.jpasecurity.proxy.EntityProxy;
import net.sf.jpasecurity.proxy.SecureEntityProxyFactory;
import net.sf.jpasecurity.util.SystemMapKey;

/**
 * @author Arne Limburg
 */
public class EntityPersister extends AbstractSecureObjectManager {

    protected final EntityManager entityManager;
    private Map<SystemMapKey, Object> secureEntities = new HashMap<SystemMapKey, Object>();
    private Map<SystemMapKey, Object> unsecureEntities = new HashMap<SystemMapKey, Object>();

    public EntityPersister(MappingInformation mappingInformation,
                           EntityManager entityManager,
                           AccessManager accessManager,
                           SecureEntityProxyFactory proxyFactory) {
        super(mappingInformation, accessManager, proxyFactory);
        this.entityManager = entityManager;
    }

    public void persist(Object secureEntity) {
        Object unsecureEntity = getUnsecureObject(secureEntity);
        cascade(secureEntity, unsecureEntity, CascadeType.PERSIST, new HashSet<SystemMapKey>());
        preFlush();
        entityManager.persist(unsecureEntity);
        postFlush();
    }

    public <T> T merge(T entity) {
        boolean isNew = isNew(entity);
        preFlush();
        T unsecureEntity = getUnsecureObject(entity);
        if (isNew) {
            cascade(entity, unsecureEntity, CascadeType.MERGE, new HashSet<SystemMapKey>());
        }
        unsecureEntity = entityManager.merge(unsecureEntity);
        postFlush();
        if (!isNew) {
            cascade(entity, unsecureEntity, CascadeType.MERGE, new HashSet<SystemMapKey>());
        }
        T secureEntity = getSecureObject(unsecureEntity);
        initialize(secureEntity, unsecureEntity, CascadeType.MERGE, new HashSet<Object>());
        if (isNew) {
            unsecureEntities.put(new SystemMapKey(secureEntity), unsecureEntity);
        }
        return secureEntity;
    }

    public boolean contains(Object entity) {
        return entityManager.contains(getUnsecureObject(entity));
    }

    public void refresh(Object entity) {
        checkAccess(AccessType.READ, entity);
        Object unsecureEntity = getUnsecureObject(entity);
        preFlush();
        entityManager.refresh(unsecureEntity);
        postFlush();
        if (entity instanceof SecureEntity) {
            initialize((SecureEntity)entity);
        }
        cascadeRefresh(entity, unsecureEntity, new HashSet<SystemMapKey>());
    }

    public void lock(Object entity, LockModeType lockMode) {
        if (lockMode == LockModeType.READ && !isAccessible(AccessType.READ, entity)) {
            throw new SecurityException("specified entity is not readable for locking");
        } else if (lockMode == LockModeType.WRITE && !isAccessible(AccessType.UPDATE, entity)) {
            throw new SecurityException("specified entity is not updateable for locking");
        }
        entityManager.lock(getUnsecureObject(entity), lockMode);
    }

    public void remove(Object entity) {
        checkAccess(AccessType.DELETE, entity);
        Object unsecureEntity = getUnsecureObject(entity);
        cascadeRemove(entity, unsecureEntity, new HashSet<SystemMapKey>());
        if (entity instanceof SecureEntity) {
            setRemoved((SecureEntity)entity);
        }
        entityManager.remove(unsecureEntity);
    }

    public Query setParameter(Query query, int index, Object value) {
        return query.setParameter(index, getUnsecureObject(value));
    }

    public Query setParameter(Query query, String name, Object value) {
        return query.setParameter(name, getUnsecureObject(value));
    }

    public void preFlush() {
        Collection<Map.Entry<SystemMapKey, Object>> entities = unsecureEntities.entrySet();
        for (Map.Entry<SystemMapKey, Object> unsecureEntity: entities.toArray(new Map.Entry[entities.size()])) {
            unsecureCopy(AccessType.UPDATE, unsecureEntity.getKey().getObject(), unsecureEntity.getValue());
        }
    }

    public void postFlush() {
        //copy over ids and version ids
        for (Map.Entry<SystemMapKey, Object> secureEntity: secureEntities.entrySet()) {
            copyIdAndVersion(secureEntity.getKey().getObject(), secureEntity.getValue());
        }
        super.postFlush();
    }

    public void clear() {
        secureEntities.clear();
        unsecureEntities.clear();
    }

    public boolean isSecureObject(Object object) {
        return super.isSecureObject(object) || unsecureEntities.containsKey(new SystemMapKey(object));
    }

    public <E> Collection<E> getSecureObjects(Class<E> type) {
        List<E> result = new ArrayList<E>();
        for (Object secureEntity: secureEntities.values()) {
            if (type.isInstance(secureEntity)) {
                result.add((E)secureEntity);
            }
        }
        return Collections.unmodifiableCollection(result);
    }

    public <T> T getReference(Class<T> type, Object id) {
        return getSecureObject(entityManager.getReference(type, id));
    }

    public <T> T getSecureObject(T unsecureObject) {
        if (unsecureObject == null) {
            return null;
        }
        T secureEntity = (T)secureEntities.get(new SystemMapKey(unsecureObject));
        if (secureEntity != null) {
            return secureEntity;
        }
        return (T)super.getSecureObject(unsecureObject);
    }

    <T> T getUnsecureObject(T secureObject) {
        if (secureObject == null) {
            return null;
        }
        //TODO bigsteff review
        Object unsecureEntity = unsecureEntities.get(
           new SystemMapKey(secureObject instanceof EntityProxy ? ((EntityProxy)secureObject).getEntity() :secureObject)
        );
        if (unsecureEntity != null) {
            return (T)unsecureEntity;
        }
        return super.getUnsecureObject(secureObject);
    }

    <T> T createUnsecureObject(final T secureEntity) {
        AccessType accessType = isNew(secureEntity)? AccessType.CREATE: AccessType.UPDATE;
        final ClassMappingInformation classMapping = getClassMapping(secureEntity.getClass());
        checkAccess(accessType, secureEntity);
        Object unsecureEntity = classMapping.newInstance();
        secureEntities.put(new SystemMapKey(unsecureEntity), secureEntity);
        unsecureEntities.put(new SystemMapKey(secureEntity), unsecureEntity);
        unsecureCopy(accessType, secureEntity, unsecureEntity);
        copyIdAndVersion(secureEntity, unsecureEntity);
        return (T)unsecureEntity;
    }

    boolean isNew(Object entity) {
        if (entity instanceof SecureEntity) {
            return false;
        }
        final ClassMappingInformation classMapping = getClassMapping(entity.getClass());
        Object id = classMapping.getId(entity);
        if (id == null) {
            return true;
        }
        return entityManager.find(classMapping.getEntityType(), id) == null;
    }

    void cascade(Object secureEntity,
                 Object unsecureEntity,
                 CascadeType cascadeType,
                 Set<SystemMapKey> alreadyCascadedEntities) {
        if (cascadeType == CascadeType.REMOVE) {
            cascadeRemove(secureEntity, unsecureEntity, alreadyCascadedEntities);
            return;
        }
        if (secureEntity == null || alreadyCascadedEntities.contains(new SystemMapKey(secureEntity))) {
            return;
        }
        alreadyCascadedEntities.add(new SystemMapKey(secureEntity));
        AccessType accessType = isNew(secureEntity)? AccessType.CREATE: AccessType.UPDATE;
        unsecureCopy(accessType, secureEntity, unsecureEntity);
        ClassMappingInformation classMapping = getClassMapping(secureEntity.getClass());
        for (PropertyMappingInformation propertyMapping: classMapping.getPropertyMappings()) {
           if (propertyMapping.isRelationshipMapping()
               && (propertyMapping.getCascadeTypes().contains(cascadeType)
                   || propertyMapping.getCascadeTypes().contains(CascadeType.ALL))) {
               Object secureValue = propertyMapping.getPropertyValue(secureEntity);
               if (secureValue != null) {
                  if (propertyMapping.isSingleValued()) {
                      cascade(secureValue, getUnsecureObject(secureValue), cascadeType, alreadyCascadedEntities);
                  } else {
                      CollectionValuedRelationshipMappingInformation collectionMapping
                          = (CollectionValuedRelationshipMappingInformation)propertyMapping;
                      if (Collection.class.isAssignableFrom(collectionMapping.getCollectionType())) {
                          for (Object secureEntry: ((Collection<Object>)secureValue)) {
                              Object unsecureEntry = getUnsecureObject(secureEntry);
                              cascade(secureEntry, unsecureEntry, cascadeType, alreadyCascadedEntities);
                          }
                      } else if (Map.class.isAssignableFrom(collectionMapping.getCollectionType())) {
                          for (Object secureEntry: ((Map<Object, Object>)secureValue).values()) {
                              Object unsecureEntry = getUnsecureObject(secureEntry);
                              cascade(secureEntry, unsecureEntry, cascadeType, alreadyCascadedEntities);
                          }
                      }
                  }
               }
           }
        }
    }

    private void cascadeRemove(Object secureEntity, Object unsecureEntity, Set<SystemMapKey> alreadyCascadedEntities) {
        if (secureEntity == null || alreadyCascadedEntities.contains(new SystemMapKey(secureEntity))) {
            return;
        }
        alreadyCascadedEntities.add(new SystemMapKey(secureEntity));
        checkAccess(AccessType.DELETE, secureEntity);
        fireRemove(getClassMapping(secureEntity.getClass()), secureEntity);
        secureEntities.remove(new SystemMapKey(unsecureEntity));
        unsecureEntities.remove(new SystemMapKey(secureEntity));
        ClassMappingInformation classMapping = getClassMapping(secureEntity.getClass());
        for (PropertyMappingInformation propertyMapping: classMapping.getPropertyMappings()) {
            if (propertyMapping.isRelationshipMapping()
                && (propertyMapping.getCascadeTypes().contains(CascadeType.REMOVE)
                    || propertyMapping.getCascadeTypes().contains(CascadeType.ALL))) {
                Object secureValue = propertyMapping.getPropertyValue(secureEntity);
                if (secureValue != null) {
                    if (propertyMapping.isSingleValued()) {
                        cascadeRemove(secureValue, getUnsecureObject(secureValue), alreadyCascadedEntities);
                        if (secureValue instanceof SecureEntity) {
                            setRemoved((SecureEntity)secureValue);
                        }
                    } else {
                        //use the unsecure collection here since the secure may be filtered
                        Collection<Object> unsecureCollection
                            = (Collection<Object>)propertyMapping.getPropertyValue(unsecureEntity);
                        for (Object unsecureEntry: unsecureCollection) {
                            cascadeRemove(getSecureObject(unsecureEntry), unsecureEntry, alreadyCascadedEntities);
                        }
                    }
                }
            }
        }
    }

    private void cascadeRefresh(Object secureEntity, Object unsecureEntity, Set<SystemMapKey> alreadyCascadedEntities) {
        if (secureEntity == null || alreadyCascadedEntities.contains(new SystemMapKey(secureEntity))) {
            return;
        }
        alreadyCascadedEntities.add(new SystemMapKey(secureEntity));
        ClassMappingInformation classMapping = getClassMapping(secureEntity.getClass());
        for (PropertyMappingInformation propertyMapping: classMapping.getPropertyMappings()) {
            if (propertyMapping.isRelationshipMapping()
                && (propertyMapping.getCascadeTypes().contains(CascadeType.REFRESH)
                    || propertyMapping.getCascadeTypes().contains(CascadeType.ALL))) {
                Object secureValue = propertyMapping.getPropertyValue(secureEntity);
                if (secureValue != null) {
                    if (propertyMapping.isSingleValued()) {
                        Object unsecureValue = getUnsecureObject(secureValue);
                        if (secureValue instanceof SecureEntity) {
                            initialize((SecureEntity)secureValue);
                        } else {
                            secureCopy(unsecureValue, secureValue);
                        }
                        cascadeRefresh(secureValue, unsecureValue, alreadyCascadedEntities);
                    } else {
                        //use the unsecure collection here since the secure may be filtered
                        Collection<Object> unsecureCollection
                            = (Collection<Object>)propertyMapping.getPropertyValue(unsecureEntity);
                        for (Object unsecureEntry: unsecureCollection) {
                            cascadeRemove(getSecureObject(unsecureEntry), unsecureEntry, alreadyCascadedEntities);
                        }
                    }
                }
            }
        }
    }

    private void initialize(Object secureEntity,
                            Object unsecureEntity,
                            CascadeType cascadeType,
                            Set<Object> alreadyInitializedEntities) {
        if (alreadyInitializedEntities.contains(secureEntity)) {
            return;
        }
        alreadyInitializedEntities.add(secureEntity);
        ClassMappingInformation classMapping = getClassMapping(secureEntity.getClass());
        for (PropertyMappingInformation propertyMapping: classMapping.getPropertyMappings()) {
            if (propertyMapping.isRelationshipMapping()
                && (propertyMapping.getCascadeTypes().contains(cascadeType)
                    || propertyMapping.getCascadeTypes().contains(CascadeType.ALL))) {
                Object secureValue = propertyMapping.getPropertyValue(secureEntity);
                if (propertyMapping.isSingleValued()) {
                    initialize(secureValue, getUnsecureObject(secureValue), cascadeType, alreadyInitializedEntities);
                } else {
                    for (Object secureEntry: ((Collection<Object>)secureValue)) {
                        initialize(secureEntry,
                                   getUnsecureObject(secureEntry),
                                   cascadeType,
                                   alreadyInitializedEntities);
                    }
                }
            }
        }
    }
}
