package hr.fer.ppj.codegen.global;

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
 * Extracts initializer values for global variables from the parse tree.
 * 
 * <p>This class provides functionality to search the parse tree and extract
 * initializer values for both simple variables and arrays. It handles:
 * <ul>
 *   <li>Integer and character constants</li>
 *   <li>Negative literals (unary minus)</li>
 *   <li>Array initializer lists</li>
 *   <li>Escape sequences in character literals</li>
 * </ul>
 * 
 * <p>The extractor searches through the parse tree structure, navigating
 * from external declarations down to initializer expressions, extracting
 * constant values where present.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class InitializerExtractor {
    
    private final NonTerminalNode parseTree;
    
    /**
     * Creates a new initializer extractor.
     * 
     * @param parseTree the parse tree from semantic analysis
     */
    public InitializerExtractor(NonTerminalNode parseTree) {
        this.parseTree = parseTree;
    }
    
    /**
     * Finds the initializer value for a global variable from the parse tree.
     * 
     * @param variableName the name of the variable to find
     * @param isChar whether the variable is of char type
     * @return the initializer value as string, or null if not found
     */
    public String findInitializerValue(String variableName, boolean isChar) {
        if (parseTree == null) {
            return null;
        }
        
        return findInitializerInNode(parseTree, variableName, isChar);
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
     * Recursively searches for variable initializer in parse tree nodes.
     */
    private String findInitializerInNode(NonTerminalNode node, String variableName, boolean isChar) {
        String symbol = node.symbol();
        
        // Look for global variable declarations
        if ("<vanjska_deklaracija>".equals(symbol)) {
            return findInitializerInDeclaration(node, variableName, isChar);
        }
        
        // Recursively search children
        for (ParseNode child : node.children()) {
            if (child instanceof NonTerminalNode nonTerminal) {
                String result = findInitializerInNode(nonTerminal, variableName, isChar);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Recursively searches for array initializer in parse tree nodes.
     */
    private List<String> findArrayInitializerInNode(NonTerminalNode node, String variableName, ArrayType arrayType) {
        String symbol = node.symbol();
        
        // Look for global variable declarations
        if ("<vanjska_deklaracija>".equals(symbol)) {
            return findArrayInitializerInDeclaration(node, variableName, arrayType);
        }
        
        // Recursively search children
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
     * Searches for variable initializer in a declaration node.
     */
    private String findInitializerInDeclaration(NonTerminalNode declaration, String variableName, boolean isChar) {
        // Look for <deklaracija> nodes
        for (ParseNode child : declaration.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<deklaracija>".equals(nonTerminal.symbol())) {
                return findInitializerInVariableDeclaration(nonTerminal, variableName, isChar);
            }
        }
        return null;
    }
    
    /**
     * Searches for array initializer in a declaration node.
     */
    private List<String> findArrayInitializerInDeclaration(NonTerminalNode declaration, String variableName, ArrayType arrayType) {
        // Look for <deklaracija> nodes
        for (ParseNode child : declaration.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<deklaracija>".equals(nonTerminal.symbol())) {
                return findArrayInitializerInVariableDeclaration(nonTerminal, variableName, arrayType);
            }
        }
        return null;
    }
    
    /**
     * Searches for variable initializer in a variable declaration.
     */
    private String findInitializerInVariableDeclaration(NonTerminalNode declaration, String variableName, boolean isChar) {
        // Find <lista_init_deklaratora>
        for (ParseNode child : declaration.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<lista_init_deklaratora>".equals(nonTerminal.symbol())) {
                return findInitializerInInitDeclaratorList(nonTerminal, variableName, isChar);
            }
        }
        return null;
    }
    
    /**
     * Searches for array initializer in a variable declaration.
     */
    private List<String> findArrayInitializerInVariableDeclaration(NonTerminalNode declaration, String variableName, ArrayType arrayType) {
        // Find <lista_init_deklaratora>
        for (ParseNode child : declaration.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<lista_init_deklaratora>".equals(nonTerminal.symbol())) {
                return findArrayInitializerInInitDeclaratorList(nonTerminal, variableName, arrayType);
            }
        }
        return null;
    }
    
    /**
     * Searches for variable initializer in init declarator list.
     */
    private String findInitializerInInitDeclaratorList(NonTerminalNode list, String variableName, boolean isChar) {
        List<ParseNode> children = list.children();
        
        if (children.size() == 1) {
            // Single declarator
            return findInitializerInInitDeclarator((NonTerminalNode) children.get(0), variableName, isChar);
        } else if (children.size() == 3) {
            // Multiple declarators: <lista_init_deklaratora> ZAREZ <init_deklarator>
            String result = findInitializerInInitDeclaratorList((NonTerminalNode) children.get(0), variableName, isChar);
            if (result != null) return result;
            return findInitializerInInitDeclarator((NonTerminalNode) children.get(2), variableName, isChar);
        }
        
        return null;
    }
    
    /**
     * Searches for array initializer in init declarator list.
     */
    private List<String> findArrayInitializerInInitDeclaratorList(NonTerminalNode list, String variableName, ArrayType arrayType) {
        List<ParseNode> children = list.children();
        
        if (children.size() == 1) {
            // Single declarator
            return findArrayInitializerInInitDeclarator((NonTerminalNode) children.get(0), variableName, arrayType);
        } else if (children.size() == 3) {
            // Multiple declarators: <lista_init_deklaratora> ZAREZ <init_deklarator>
            List<String> result = findArrayInitializerInInitDeclaratorList((NonTerminalNode) children.get(0), variableName, arrayType);
            if (result != null) return result;
            return findArrayInitializerInInitDeclarator((NonTerminalNode) children.get(2), variableName, arrayType);
        }
        
        return null;
    }
    
    /**
     * Searches for variable initializer in init declarator.
     */
    private String findInitializerInInitDeclarator(NonTerminalNode declarator, String variableName, boolean isChar) {
        List<ParseNode> children = declarator.children();
        
        if (children.size() == 3) {
            // <deklarator> OP_PRIDRUZI <inicijalizator>
            ParseNode operator = children.get(1);
            if (operator instanceof TerminalNode terminal && "OP_PRIDRUZI".equals(terminal.symbol())) {
                String declaredName = extractVariableName((NonTerminalNode) children.get(0));
                if (variableName.equals(declaredName)) {
                    return extractInitializerValue((NonTerminalNode) children.get(2), isChar);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Searches for array initializer in init declarator.
     */
    private List<String> findArrayInitializerInInitDeclarator(NonTerminalNode declarator, String variableName, ArrayType arrayType) {
        List<ParseNode> children = declarator.children();
        
        if (children.size() == 3) {
            // <deklarator> OP_PRIDRUZI <inicijalizator>
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
        // Find IDN token in declarator structure
        return findIdentifier(declarator);
    }
    
    /**
     * Recursively finds identifier in node.
     */
    private String findIdentifier(NonTerminalNode node) {
        for (ParseNode child : node.children()) {
            if (child instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                return terminal.lexeme();
            } else if (child instanceof NonTerminalNode nonTerminal) {
                String result = findIdentifier(nonTerminal);
                if (result != null) return result;
            }
        }
        return null;
    }
    
    /**
     * Extracts initializer value from inicijalizator node.
     */
    private String extractInitializerValue(NonTerminalNode initializer, boolean isChar) {
        // Look for <izraz_pridruzivanja> -> ... -> BROJ or ZNAK
        return findConstantValue(initializer, isChar);
    }
    
    /**
     * Extracts array initializer values from inicijalizator node.
     */
    private List<String> extractArrayInitializerValue(NonTerminalNode initializer, ArrayType arrayType) {
        // Look for <lista_izraza_pridruzivanja> with character or integer literals
        return findArrayConstantValues(initializer, arrayType);
    }
    
    /**
     * Recursively finds constant value in expression tree.
     * Handles both positive and negative literals.
     */
    private String findConstantValue(NonTerminalNode node, boolean isChar) {
        return findConstantValueWithSign(node, isChar, false);
    }
    
    /**
     * Recursively finds constant value in expression tree, tracking sign.
     */
    private String findConstantValueWithSign(NonTerminalNode node, boolean isChar, boolean isNegative) {
        String symbol = node.symbol();
        List<ParseNode> children = node.children();
        
        // Check for unary minus at <unarni_izraz> level
        // Structure: <unarni_izraz> -> <unarni_operator> -> MINUS + <cast_izraz>
        if ("<unarni_izraz>".equals(symbol) && children.size() == 2) {
            ParseNode first = children.get(0);
            // Check if first child is <unarni_operator> containing MINUS
            if (first instanceof NonTerminalNode unaryOp && 
                "<unarni_operator>".equals(unaryOp.symbol())) {
                // Check if <unarni_operator> contains MINUS
                for (ParseNode opChild : unaryOp.children()) {
                    if (opChild instanceof TerminalNode terminal && "MINUS".equals(terminal.symbol())) {
                        // Found unary minus, continue with operand and mark as negative
                        if (children.get(1) instanceof NonTerminalNode operand) {
                            return findConstantValueWithSign(operand, isChar, true);
                        }
                        return null;
                    }
                }
            }
        }
        
        // Check for <primarni_izraz> with BROJ terminal
        if ("<primarni_izraz>".equals(symbol)) {
            for (ParseNode child : children) {
                if (child instanceof TerminalNode terminal) {
                    if ("BROJ".equals(terminal.symbol())) {
                        String value = terminal.lexeme();
                        return isNegative ? "-" + value : value;
                    } else if (isChar && "ZNAK".equals(terminal.symbol())) {
                        // Extract character value - 'a' -> 97
                        String lexeme = terminal.lexeme();
                        if (lexeme.length() >= 3 && lexeme.startsWith("'") && lexeme.endsWith("'")) {
                            char ch = lexeme.charAt(1);
                            // Handle escape sequences
                            if (ch == '\\' && lexeme.length() >= 4) {
                                char next = lexeme.charAt(2);
                                switch (next) {
                                    case 'n': return "10"; // newline
                                    case 't': return "9";  // tab
                                    case '0': return "0";  // null
                                    case '\\': return "92"; // backslash
                                    case '\'': return "39"; // single quote
                                    default: return String.valueOf((int)next);
                                }
                            }
                            return String.valueOf((int)ch);
                        }
                    }
                }
            }
        }
        
        // Recursively search children
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal) {
                String result = findConstantValueWithSign(nonTerminal, isChar, isNegative);
                if (result != null) return result;
            }
        }
        
        return null;
    }
    
    /**
     * Recursively finds array constant values in expression tree.
     */
    private List<String> findArrayConstantValues(NonTerminalNode node, ArrayType arrayType) {
        Type elementType = TypeSystem.stripConst(arrayType.elementType());
        boolean isCharArray = elementType == PrimitiveType.CHAR;
        
        // Look for <lista_izraza_pridruzivanja>
        if ("<lista_izraza_pridruzivanja>".equals(node.symbol())) {
            return extractListValues(node, isCharArray);
        }
        
        // Recursively search children
        for (ParseNode child : node.children()) {
            if (child instanceof NonTerminalNode nonTerminal) {
                List<String> result = findArrayConstantValues(nonTerminal, arrayType);
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
        List<ParseNode> children = listNode.children();
        
        if (children.size() == 1) {
            // Single expression
            String value = findConstantValue((NonTerminalNode) children.get(0), isChar);
            if (value != null) {
                // Always use %D prefix for C integer constants (including char arrays)
                values.add("%D " + value);
            }
        } else if (children.size() == 3) {
            // <lista_izraza_pridruzivanja> ZAREZ <izraz_pridruzivanja>
            List<String> prevValues = extractListValues((NonTerminalNode) children.get(0), isChar);
            if (prevValues != null) {
                values.addAll(prevValues);
            }
            String value = findConstantValue((NonTerminalNode) children.get(2), isChar);
            if (value != null) {
                // Always use %D prefix for C integer constants (including char arrays)
                values.add("%D " + value);
            }
        }
        
        return values.isEmpty() ? null : values;
    }
}

