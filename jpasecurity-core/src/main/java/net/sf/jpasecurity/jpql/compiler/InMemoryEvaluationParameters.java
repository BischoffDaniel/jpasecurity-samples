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
package net.sf.jpasecurity.jpql.compiler;

import java.util.Map;

import net.sf.jpasecurity.mapping.Alias;
import net.sf.jpasecurity.mapping.MappingInformation;

/**
 * @author Arne Limburg
 */
public class InMemoryEvaluationParameters extends QueryEvaluationParameters {

    public InMemoryEvaluationParameters(MappingInformation mappingInformation,
                                        Map<Alias, Object> aliases,
                                        Map<String, Object> namedParameters,
                                        Map<Integer, Object> positionalParameters,
                                        EvaluationType evaluationType) {
        super(mappingInformation, aliases, namedParameters, positionalParameters, true, evaluationType);
    }

    public InMemoryEvaluationParameters(QueryEvaluationParameters parameters) {
        this(parameters.getMappingInformation(),
             parameters.getAliasValues(),
             parameters.getNamedParameters(),
             parameters.getPositionalParameters(),
             parameters.getEvaluationType());
    }

}

