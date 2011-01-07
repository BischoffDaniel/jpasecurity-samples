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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.sf.jpasecurity.configuration.ExceptionFactory;
import net.sf.jpasecurity.jpql.JpqlCompiledStatement;
import net.sf.jpasecurity.jpql.parser.JpqlFromItem;
import net.sf.jpasecurity.jpql.parser.JpqlIdentificationVariable;
import net.sf.jpasecurity.jpql.parser.JpqlInCollection;
import net.sf.jpasecurity.jpql.parser.JpqlInnerFetchJoin;
import net.sf.jpasecurity.jpql.parser.JpqlInnerJoin;
import net.sf.jpasecurity.jpql.parser.JpqlNamedInputParameter;
import net.sf.jpasecurity.jpql.parser.JpqlOuterFetchJoin;
import net.sf.jpasecurity.jpql.parser.JpqlOuterJoin;
import net.sf.jpasecurity.jpql.parser.JpqlPath;
import net.sf.jpasecurity.jpql.parser.JpqlPositionalInputParameter;
import net.sf.jpasecurity.jpql.parser.JpqlSelectExpression;
import net.sf.jpasecurity.jpql.parser.JpqlStatement;
import net.sf.jpasecurity.jpql.parser.JpqlSubselect;
import net.sf.jpasecurity.jpql.parser.JpqlVisitorAdapter;
import net.sf.jpasecurity.jpql.parser.Node;
import net.sf.jpasecurity.jpql.parser.ToStringVisitor;
import net.sf.jpasecurity.mapping.MappingInformation;
import net.sf.jpasecurity.mapping.TypeDefinition;

/**
 * Compiles a {@link JpqlStatement} into a {@link JpqlCompiledStatement}.
 * @author Arne Limburg
 */
public class JpqlCompiler {

    private MappingInformation mappingInformation;
    private ExceptionFactory exceptionFactory;
    private final SelectVisitor selectVisitor = new SelectVisitor();
    private final AliasVisitor aliasVisitor = new AliasVisitor();
    private final NamedParameterVisitor namedParameterVisitor = new NamedParameterVisitor();
    private final PositionalParameterVisitor positionalParameterVisitor = new PositionalParameterVisitor();

    public JpqlCompiler(MappingInformation mappingInformation, ExceptionFactory exceptionFactory) {
        this.mappingInformation = mappingInformation;
        this.exceptionFactory = exceptionFactory;
    }

    public JpqlCompiledStatement compile(JpqlStatement statement) {
        return compile((Node)statement);
    }

    public JpqlCompiledStatement compile(JpqlSubselect statement) {
        return compile((Node)statement);
    }

    private JpqlCompiledStatement compile(Node statement) {
        List<String> selectedPathes = getSelectedPaths(statement);
        Set<TypeDefinition> typeDefinitions = getAliasDefinitions(statement);
        Set<String> namedParameters = getNamedParameters(statement);
        return new JpqlCompiledStatement(statement, selectedPathes, typeDefinitions, namedParameters);
    }

    public List<String> getSelectedPaths(Node node) {
        if (node == null) {
            return Collections.EMPTY_LIST;
        }
        List<String> selectedPaths = new ArrayList<String>();
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            node.jjtGetChild(i).visit(selectVisitor, selectedPaths);
        }
        return Collections.unmodifiableList(selectedPaths);
    }

    public Set<TypeDefinition> getAliasDefinitions(Node node) {
        if (node == null) {
            return Collections.EMPTY_SET;
        }
        Set<TypeDefinition> typeDefinitions = new HashSet<TypeDefinition>();
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            node.jjtGetChild(i).visit(aliasVisitor, typeDefinitions);
        }
        return Collections.unmodifiableSet(typeDefinitions);
    }

    public Set<String> getNamedParameters(Node node) {
        if (node == null) {
            return Collections.EMPTY_SET;
        }
        Set<String> namedParameters = new HashSet<String>();
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            node.jjtGetChild(i).visit(namedParameterVisitor, namedParameters);
        }
        return Collections.unmodifiableSet(namedParameters);
    }

    public Set<String> getPositionalParameters(Node node) {
        if (node == null) {
            return Collections.EMPTY_SET;
        }
        Set<String> positionalParameters = new HashSet<String>();
        for (int i = 0; i < node.jjtGetNumChildren(); i++) {
            node.jjtGetChild(i).visit(positionalParameterVisitor, positionalParameters);
        }
        return Collections.unmodifiableSet(positionalParameters);
    }

    private class SelectVisitor extends JpqlVisitorAdapter<List<String>> {

        private final SelectPathVisitor selectPathVisitor = new SelectPathVisitor();

        public boolean visit(JpqlSelectExpression node, List<String> selectedPaths) {
            node.visit(selectPathVisitor, selectedPaths);
            return false;
        }

        public boolean visit(JpqlSubselect node, List<String> selectedPaths) {
            return false;
        }
    }

    private class SelectPathVisitor extends JpqlVisitorAdapter<List<String>> {

        private final ToStringVisitor toStringVisitor = new ToStringVisitor();

        public boolean visit(JpqlPath node, List<String> selectedPaths) {
            return extractSelectedPath(node, selectedPaths);
        }

        public boolean visit(JpqlIdentificationVariable node, List<String> selectedPaths) {
            return extractSelectedPath(node, selectedPaths);
        }

        private boolean extractSelectedPath(Node node, List<String> selectedPaths) {
            StringBuilder path = new StringBuilder();
            node.visit(toStringVisitor, path);
            selectedPaths.add(path.toString());
            return false;
        }
    }

    private class AliasVisitor extends JpqlVisitorAdapter<Set<TypeDefinition>> {

        public boolean visit(JpqlFromItem node, Set<TypeDefinition> typeDefinitions) {
            String abstractSchemaName = node.jjtGetChild(0).toString();
            String alias = node.jjtGetChild(1).toString();
            Class<?> type = mappingInformation.getClassMapping(abstractSchemaName.trim()).getEntityType();
            if (type == null) {
                throw exceptionFactory.createTypeNotFoundException(abstractSchemaName.trim());
            }
            typeDefinitions.add(new TypeDefinition(alias, type));
            return false;
        }

        public boolean visit(JpqlInCollection node, Set<TypeDefinition> typeDefinitions) {
            return visitJoin(node, typeDefinitions, true, false);
        }

        public boolean visit(JpqlInnerJoin node, Set<TypeDefinition> typeDefinitions) {
            return visitJoin(node, typeDefinitions, true, false);
        }

        public boolean visit(JpqlOuterJoin node, Set<TypeDefinition> typeDefinitions) {
            return visitJoin(node, typeDefinitions, false, false);
        }

        public boolean visit(JpqlOuterFetchJoin node, Set<TypeDefinition> typeDefinitions) {
            return visitJoin(node, typeDefinitions, false, true);
        }

        public boolean visit(JpqlInnerFetchJoin node, Set<TypeDefinition> typeDefinitions) {
            return visitJoin(node, typeDefinitions, true, true);
        }

        private boolean visitJoin(Node node,
                                  Set<TypeDefinition> typeDefinitions,
                                  boolean innerJoin,
                                  boolean fetchJoin) {
            String fetchPath = node.jjtGetChild(0).toString();
            Class type = mappingInformation.getType(fetchPath, typeDefinitions);
            if (node.jjtGetNumChildren() == 1) {
                typeDefinitions.add(new TypeDefinition(type, fetchPath, innerJoin, fetchJoin));
            } else {
                String alias = node.jjtGetChild(1).toString();
                typeDefinitions.add(new TypeDefinition(alias, type, fetchPath, innerJoin, fetchJoin));
            }
            return false;
        }

        public boolean visit(JpqlSubselect node, Set<TypeDefinition> typeDefinitions) {
            return false;
        }
    }

    private class NamedParameterVisitor extends JpqlVisitorAdapter<Set<String>> {

        public boolean visit(JpqlNamedInputParameter node, Set<String> namedParameters) {
            namedParameters.add(node.getValue());
            return true;
        }
    }

    private class PositionalParameterVisitor extends JpqlVisitorAdapter<Set<String>> {

        public boolean visit(JpqlPositionalInputParameter node, Set<String> positionalParameters) {
            positionalParameters.add(node.getValue());
            return true;
        }
    }
}
