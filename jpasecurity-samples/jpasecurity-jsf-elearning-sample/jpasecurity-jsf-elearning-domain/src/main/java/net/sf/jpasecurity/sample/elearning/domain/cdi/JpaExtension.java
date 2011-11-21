/*
 * Copyright 2011 Arne Limburg
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
package net.sf.jpasecurity.sample.elearning.domain.cdi;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.Extension;
import javax.enterprise.inject.spi.ProcessAnnotatedType;
import javax.persistence.Embeddable;
import javax.persistence.Entity;
import javax.persistence.MappedSuperclass;

/**
 * @author Arne Limburg
 */
public class JpaExtension implements Extension {

    public void vetoJpaEntities(@Observes ProcessAnnotatedType<?> processAnnotatedType) {
        if (processAnnotatedType.getAnnotatedType().isAnnotationPresent(Entity.class)) {
            processAnnotatedType.veto();
        } else if (processAnnotatedType.getAnnotatedType().isAnnotationPresent(Embeddable.class)) {
            processAnnotatedType.veto();
        } else if (processAnnotatedType.getAnnotatedType().isAnnotationPresent(MappedSuperclass.class)) {
            processAnnotatedType.veto();
        }
    }
}
