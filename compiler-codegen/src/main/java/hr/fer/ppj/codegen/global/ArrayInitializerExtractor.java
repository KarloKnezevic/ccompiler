package hr.fer.ppj.codegen.global;

import hr.fer.ppj.codegen.utils.ConstantValueExtractor;
import hr.fer.ppj.codegen.utils.IdentifierExtractor;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts initializer values for global array variables.
 * 
 * <p>This class searches the parse tree to find initializer lists for
 * global array variables, extracting constant values from array initializers.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ArrayInitializerExtractor {
    
    private final NonTerminalNode parseTree;
    
    /**
     * Creates a new array initializer extractor.
     * 
     * @param parseTree the parse tree from semantic analysis
     */
    public ArrayInitializerExtractor(NonTerminalNode parseTree) {
        this.parseTree = parseTree;
    }
    
    /**
     * Finds array initializer values for a global array variable.
     * 
     * @param variableName the name of the array variable
     * @param arrayType the array type
     * @return list of initializer values (with %D prefix), or null if not found
     */
    public List<String> findArrayInitializer(String variableName, ArrayType arrayType) {
        if (parseTree == null) {
            return null;
        }
        return findArrayInitializerInNode(parseTree, variableName, arrayType);
    }
    
    /**
     * Recursively searches for array initializer in parse tree nodes.
     */
    private List<String> findArrayInitializerInNode(NonTerminalNode node, String variableName, ArrayType arrayType) {
        String symbol = node.symbol();
        
        if ("<vanjska_deklaracija>".equals(symbol)) {
            return findArrayInitializerInDeclaration(node, variableName, arrayType);
        }
        
        for (ParseNode child : node.children()) {
            if (child instanceof NonTerminalNode nonTerminal) {
                List<String> result = findArrayInitializerInNode(nonTerminal, variableName, arrayType);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Searches for array initializer in a declaration node.
     */
    private List<String> findArrayInitializerInDeclaration(NonTerminalNode declaration, String variableName, ArrayType arrayType) {
        for (ParseNode child : declaration.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<deklaracija>".equals(nonTerminal.symbol())) {
                return findArrayInitializerInVariableDeclaration(nonTerminal, variableName, arrayType);
            }
        }
        return null;
    }
    
    /**
     * Searches for array initializer in a variable declaration.
     */
    private List<String> findArrayInitializerInVariableDeclaration(NonTerminalNode declaration, String variableName, ArrayType arrayType) {
        for (ParseNode child : declaration.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<lista_init_deklaratora>".equals(nonTerminal.symbol())) {
                return findArrayInitializerInInitDeclaratorList(nonTerminal, variableName, arrayType);
            }
        }
        return null;
    }
    
    /**
     * Searches for array initializer in init declarator list.
     */
    private List<String> findArrayInitializerInInitDeclaratorList(NonTerminalNode list, String variableName, ArrayType arrayType) {
        var children = list.children();
        
        if (children.size() == 1) {
            return findArrayInitializerInInitDeclarator((NonTerminalNode) children.get(0), variableName, arrayType);
        } else if (children.size() == 3) {
            List<String> result = findArrayInitializerInInitDeclaratorList((NonTerminalNode) children.get(0), variableName, arrayType);
            if (result != null) return result;
            return findArrayInitializerInInitDeclarator((NonTerminalNode) children.get(2), variableName, arrayType);
        }
        
        return null;
    }
    
    /**
     * Searches for array initializer in init declarator.
     */
    private List<String> findArrayInitializerInInitDeclarator(NonTerminalNode declarator, String variableName, ArrayType arrayType) {
        var children = declarator.children();
        
        if (children.size() == 3) {
            ParseNode operator = children.get(1);
            if (operator instanceof TerminalNode terminal && "OP_PRIDRUZI".equals(terminal.symbol())) {
                String declaredName = extractVariableName((NonTerminalNode) children.get(0));
                if (variableName.equals(declaredName)) {
                    return extractArrayInitializerValue((NonTerminalNode) children.get(2), arrayType);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extracts variable name from declarator.
     */
    private String extractVariableName(NonTerminalNode declarator) {
        return IdentifierExtractor.findIdentifier(declarator);
    }
    
    /**
     * Extracts array initializer values from inicijalizator node.
     */
    private List<String> extractArrayInitializerValue(NonTerminalNode initializer, ArrayType arrayType) {
        Type elementType = TypeSystem.stripConst(arrayType.elementType());
        boolean isCharArray = elementType == PrimitiveType.CHAR;
        return findArrayConstantValues(initializer, isCharArray);
    }
    
    /**
     * Recursively finds array constant values in expression tree.
     */
    private List<String> findArrayConstantValues(NonTerminalNode node, boolean isCharArray) {
        if ("<lista_izraza_pridruzivanja>".equals(node.symbol())) {
            return extractListValues(node, isCharArray);
        }
        
        for (ParseNode child : node.children()) {
            if (child instanceof NonTerminalNode nonTerminal) {
                List<String> result = findArrayConstantValues(nonTerminal, isCharArray);
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extracts values from a list of assignment expressions.
     */
    private List<String> extractListValues(NonTerminalNode listNode, boolean isChar) {
        List<String> values = new ArrayList<>();
        var children = listNode.children();
        
        if (children.size() == 1) {
            String value = ConstantValueExtractor.findConstantValue((NonTerminalNode) children.get(0), isChar);
            if (value != null) {
                values.add("%D " + value);
            }
        } else if (children.size() == 3) {
            List<String> prevValues = extractListValues((NonTerminalNode) children.get(0), isChar);
            if (prevValues != null) {
                values.addAll(prevValues);
            }
            String value = ConstantValueExtractor.findConstantValue((NonTerminalNode) children.get(2), isChar);
            if (value != null) {
                values.add("%D " + value);
            }
        }
        
        return values.isEmpty() ? null : values;
    }
}

