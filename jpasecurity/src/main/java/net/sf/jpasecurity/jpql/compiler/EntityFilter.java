/*
 * Copyright 2008 Arne Limburg
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

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceException;

import net.sf.jpasecurity.jpql.JpqlVisitorAdapter;
import net.sf.jpasecurity.jpql.parser.JpqlBrackets;
import net.sf.jpasecurity.jpql.parser.JpqlCurrentUser;
import net.sf.jpasecurity.jpql.parser.JpqlParser;
import net.sf.jpasecurity.jpql.parser.JpqlStatement;
import net.sf.jpasecurity.jpql.parser.JpqlWhere;
import net.sf.jpasecurity.jpql.parser.Node;
import net.sf.jpasecurity.jpql.parser.ParseException;
import net.sf.jpasecurity.persistence.mapping.ClassMappingInformation;
import net.sf.jpasecurity.persistence.mapping.MappingInformation;
import net.sf.jpasecurity.security.rules.AccessRule;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author Arne Limburg
 */
public class EntityFilter {

    private static final Log LOG = LogFactory.getLog(EntityFilter.class);

    private final MappingInformation mappingInformation;
    private final JpqlParser parser;
    private final JpqlCompiler compiler;
    private final Map<String, JpqlCompiledStatement> statementCache = new HashMap<String, JpqlCompiledStatement>();
    private final InMemoryEvaluator queryEvaluator = new InMemoryEvaluator();
    private final EntityManagerEvaluator entityManagerEvaluator;
    private final QueryPreparator queryPreparator = new QueryPreparator();
    private final CurrentUserReplacer currentUserReplacer = new CurrentUserReplacer();
    private final List<AccessRule> accessRules;

    public EntityFilter(EntityManager entityManager,
                        MappingInformation mappingInformation,
                        List<AccessRule> accessRules) {
        this.mappingInformation = mappingInformation;
        this.parser = new JpqlParser();
        this.compiler = new JpqlCompiler(mappingInformation);
        this.entityManagerEvaluator = new EntityManagerEvaluator(entityManager, compiler);
        this.accessRules = accessRules;
    }
    
    public boolean isAccessible(Object entity, Object user, Set<Object> roles) throws NotEvaluatableException {
        ClassMappingInformation mapping = mappingInformation.getClassMapping(entity.getClass());
        String alias = Character.toLowerCase(mapping.getEntityName().charAt(0)) + mapping.getEntityName().substring(1);
        Node accessRulesNode = createAccessRuleNode(alias, mapping.getEntityType(), roles.size());
        if (accessRulesNode == null) {
            return true;
        }
        String userParameterName = createUserParameterName(Collections.EMPTY_SET, accessRulesNode);
        Map<String, Object> parameters
            = createAuthenticationParameters(Collections.EMPTY_SET, userParameterName, user, roles, accessRulesNode);
        InMemoryEvaluationParameters<Boolean> evaluationParameters
            = new InMemoryEvaluationParameters<Boolean>(mappingInformation,
                                                        Collections.singletonMap(alias, entity),
                                                        parameters,
                                                        Collections.EMPTY_MAP);
        return entityManagerEvaluator.evaluate(accessRulesNode, evaluationParameters);
    }

    public FilterResult filterQuery(String query, Object user, Set<Object> roles) {

        LOG.info("Filtering query " + query);

        JpqlCompiledStatement statement = compile(query);

        Node accessRules = createAccessRuleNode(statement, roles != null? roles.size(): 0);
        if (accessRules == null) {
            LOG.info("No access rules defined for selected type. Returning unfiltered query");
            return new FilterResult(query, null, null);
        }

        LOG.info("Using access rules " + accessRules);

        Set<String> namedParameters = statement.getNamedParameters();
        String userParameterName = createUserParameterName(namedParameters, accessRules);
        Map<String, Object> parameters
            = createAuthenticationParameters(namedParameters, userParameterName, user, roles, accessRules);
        try {
            InMemoryEvaluationParameters<Boolean> evaluationParameters
                = new InMemoryEvaluationParameters<Boolean>(mappingInformation,
                                                            Collections.EMPTY_MAP,
                                                            parameters,
                                                            Collections.EMPTY_MAP);
            if (queryEvaluator.evaluate(accessRules, evaluationParameters)) {
                LOG.info("Access rules are always true for current user and roles. Returning unfiltered query");
                return new FilterResult(query, null, null);
            }
        } catch (NotEvaluatableException e) {
            //access rules need to be applied then
        }

        JpqlWhere where = statement.getWhereClause();
        if (where == null) {
            where = queryPreparator.createWhere(accessRules);
            Node parent = statement.getFromClause().jjtGetParent();
            for (int i = parent.jjtGetNumChildren(); i > 2; i--) {
                parent.jjtAddChild(parent.jjtGetChild(i - 1), i);
            }
            parent.jjtAddChild(where, 2);
        } else {
            Node condition = where.jjtGetChild(0);
            if (!(condition instanceof JpqlBrackets)) {
                condition = queryPreparator.createBrackets(condition);
            }
            Node and = queryPreparator.createAnd(condition, accessRules);
            and.jjtSetParent(where);
            where.jjtSetChild(and, 0);
        }

        LOG.info("Optimizing filtered query " + statement.getStatement());

        QueryOptimizer optimizer
            = new QueryOptimizer(mappingInformation, Collections.EMPTY_MAP, parameters, Collections.EMPTY_MAP);
        optimizer.optimize(accessRules);
        Set<String> parameterNames = compiler.getNamedParameters(accessRules);
        parameters.keySet().retainAll(parameterNames);
        if (parameterNames.contains(userParameterName)) {
            parameters.remove(userParameterName);
        } else {
            userParameterName = null;
        }

        LOG.info("Returning optimized query " + statement.getStatement());
        return new FilterResult(statement.getStatement().toString(),
                                userParameterName,
                                parameters.size() > 0? parameters: null);
    }

    private Node createAccessRuleNode(JpqlCompiledStatement statement, int roleCount) {
        return createAccessRuleNode(getSelectedTypes(statement), roleCount);
    }
    
    private Node createAccessRuleNode(String alias, Class<?> type, int roleCount) {
        Map<String, Class<?>> aliases = new HashMap<String, Class<?>>();
        aliases.put(alias, type);
        return createAccessRuleNode(aliases, roleCount);
    }
    
    private Node createAccessRuleNode(Map<String, Class<?>> selectedTypes, int roleCount) {
        Node accessRuleNode = null;
        for (Map.Entry<String, Class<?>> selectedType: selectedTypes.entrySet()) {
            Collection<AccessRule> accessRules = new FilteredAccessRules(selectedType.getValue());
            for (AccessRule accessRule: accessRules) {
                accessRule = queryPreparator.expand(accessRule, roleCount);
                Node condition = queryPreparator.createBrackets(accessRule.getWhereClause().jjtGetChild(0));
                queryPreparator.replace(condition, accessRule.getSelectedPath(), selectedType.getKey());
                if (accessRuleNode == null) {
                    accessRuleNode = condition;
                } else {
                    accessRuleNode = queryPreparator.createOr(accessRuleNode, condition);
                }
            }
        }
        if (accessRuleNode == null) {
            return null;
        } else {
            return queryPreparator.createBrackets(accessRuleNode);
        }
    }
    
    private Map<String, Object> createAuthenticationParameters(Set<String> namedParameters,
                                                               String userParameterName,
                                                               Object user,
                                                               Set<Object> roles,
                                                               Node accessRules) {
        Set<String> roleParameterNames = createRoleParameterNames(namedParameters, userParameterName, accessRules);
        Map<String, Object> roleParameters = createRoleParameters(roleParameterNames, roles);

        Map<String, Object> parameters = new HashMap<String, Object>();
        if (userParameterName != null) {
            parameters.put(userParameterName, user);
        }
        parameters.putAll(roleParameters);
        return parameters;
    }

    private String createUserParameterName(Set<String> namedParameters, Node accessRules) {
        String userParameterName = AccessRule.DEFAULT_USER_PARAMETER_NAME;
        for (int i = 0; namedParameters.contains(userParameterName); i++) {
            userParameterName = AccessRule.DEFAULT_USER_PARAMETER_NAME + i;
        }
        int userParameterNameCount = replaceCurrentUser(accessRules, userParameterName);
        return userParameterNameCount > 0? userParameterName: null;
    }

    private Set<String> createRoleParameterNames(Set<String> namedParameters,
                                                 String userParameterName,
                                                 Node accessRules) {
        Set<String> roleParameterNames = compiler.getNamedParameters(accessRules);
        roleParameterNames.remove(userParameterName);
        Set<String> duplicateParameterNames = new HashSet<String>(roleParameterNames);
        duplicateParameterNames.retainAll(namedParameters);
        for (String duplicateParameterName: duplicateParameterNames) {
            String newParameterName = AccessRule.DEFAULT_ROLE_PARAMETER_NAME + 0;
            for (int i = 1;
                 namedParameters.contains(newParameterName) || roleParameterNames.contains(newParameterName);
                 i++) {
                newParameterName = AccessRule.DEFAULT_ROLE_PARAMETER_NAME + i;
            }
            roleParameterNames.remove(duplicateParameterName);
            roleParameterNames.add(newParameterName);
        }
        return roleParameterNames;
    }

    private Map<String, Object> createRoleParameters(Set<String> roleParameterNames, Set<Object> roles) {
        Map<String, Object> roleParameters = new HashMap<String, Object>();
        if (roles != null && roleParameterNames.size() > 0) {
            Iterator<String> roleParameterIterator = roleParameterNames.iterator();
            Iterator<Object> roleIterator = roles.iterator();
            for (; roleParameterIterator.hasNext() && roleIterator.hasNext();) {
                roleParameters.put(roleParameterIterator.next(), roleIterator.next());
            }
            if (roleParameterIterator.hasNext() || roleIterator.hasNext()) {
                throw new IllegalStateException("roleParameters don't match roles");
            }
        }
        return roleParameters;
    }

    private JpqlCompiledStatement compile(String query) {
        JpqlCompiledStatement compiledStatement = statementCache.get(query);
        if (compiledStatement == null) {
            try {
                JpqlStatement statement = parser.parseQuery(query);
                compiledStatement = compiler.compile(statement);
                statementCache.put(query, compiledStatement);
            } catch (ParseException e) {
                throw new PersistenceException(e);
            }
        }
        return compiledStatement.clone();
    }

    private Map<String, Class<?>> getSelectedTypes(JpqlCompiledStatement statement) {
        Map<String, Class<?>> selectedTypes = new HashMap<String, Class<?>>();
        for (String selectedPath: statement.getSelectedPathes()) {
            selectedTypes.put(selectedPath, compiler.getType(selectedPath, statement.getAliasTypes()));
        }
        return selectedTypes;
    }

    private Class<?> getSelectedType(AccessRule entry) {
        Map<String, Class<?>> selectedTypes = getSelectedTypes(entry);
        if (selectedTypes.size() > 1) {
            throw new IllegalStateException("an acl entry may have only one selected type");
        }
        return selectedTypes.values().iterator().next();
    }

    private int replaceCurrentUser(Node node, String namedParameter) {
        if (node == null) {
            return 0;
        }
        ReplacementParameters parameters = new ReplacementParameters(namedParameter);
        node.visit(currentUserReplacer, parameters);
        return parameters.getReplacementCount();
    }

    private class FilteredAccessRules extends AbstractSet<AccessRule> {

        private Class<?> type;

        public FilteredAccessRules(Class<?> type) {
            this.type = type;
        }

        public Iterator<AccessRule> iterator() {
            return new FilteredIterator(accessRules.iterator());
        }

        public int size() {
            int size = 0;
            for (Iterator<AccessRule> i = iterator(); i.hasNext(); i.next()) {
                size++;
            }
            return size;
        }

        private class FilteredIterator implements Iterator<AccessRule> {

            private Iterator<AccessRule> iterator;
            private AccessRule next;

            public FilteredIterator(Iterator<AccessRule> iterator) {
                this.iterator = iterator;
                initialize();
            }

            private void initialize() {
                try {
                    next();
                } catch (NoSuchElementException e) {
                    //this is expected to be thrown on initialization
                }
            }

            public boolean hasNext() {
                return next != null;
            }

            public AccessRule next() {
                AccessRule current = next;
                do {
                    if (!iterator.hasNext()) {
                        next = null;
                        break;
                    }
                    next = iterator.next();
                } while (getSelectedType(next) != type);
                if (current == null) {
                    throw new NoSuchElementException();
                }
                return current;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        }
    }

    private class CurrentUserReplacer extends JpqlVisitorAdapter<ReplacementParameters> {

        public boolean visit(JpqlCurrentUser node, ReplacementParameters replacement) {
            queryPreparator.replace(node, queryPreparator.createInputParameter(replacement.getNamedParameter()));
            replacement.incrementReplacementCount();
            return true;
        }
    }

    private class ReplacementParameters {

        private String namedParameter;
        private int replacementCount;

        public ReplacementParameters(String namedParameter) {
            this.namedParameter = namedParameter;
        }

        public String getNamedParameter() {
            return namedParameter;
        }

        public int getReplacementCount() {
            return replacementCount;
        }

        public void incrementReplacementCount() {
            replacementCount++;
        }
    }
}