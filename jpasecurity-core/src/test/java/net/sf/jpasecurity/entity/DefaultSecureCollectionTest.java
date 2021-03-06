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
import java.util.List;

import net.sf.jpasecurity.SecureEntity;

/**
 * @author Arne Limburg
 */
public class DefaultSecureCollectionTest
    extends AbstractSecureCollectionTestCase<DefaultSecureCollection<Object, Collection<Object>>> {

    public DefaultSecureCollection<Object, Collection<Object>> createSecureCollection(
                    AbstractSecureObjectManager objectManager,
                    SecureEntity... secureEntities) {
        List<Object> original = new ArrayList<Object>();
        List<Object> filtered = new ArrayList<Object>();
        for (SecureEntity secureEntity: secureEntities) {
            original.add(objectManager.getUnsecureObject(secureEntity));
            filtered.add(secureEntity);
        }
        return new DefaultSecureCollection<Object, Collection<Object>>(original, filtered, objectManager);
    }
}
