package hr.fer.ppj.codegen.func;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Extracts function information from parse tree nodes.
 * 
 * <p>This class is responsible for extracting:
 * <ul>
 *   <li>Function names from {@code <deklarator>} nodes</li>
 *   <li>Parameter lists from {@code <lista_parametara>} nodes</li>
 *   <li>Local variable information from {@code <slozena_naredba>} nodes</li>
 * </ul>
 * 
 * <p><b>Grammar Rules Handled:</b>
 * <ul>
 *   <li>{@code <definicija_funkcije> ::= <ime_tipa> <deklarator> <slozena_naredba>}</li>
 *   <li>{@code <deklarator> -> <izravni_deklarator> -> IDN}</li>
 *   <li>{@code <lista_parametara> -> <deklaracija_parametra>}</li>
 *   <li>{@code <slozena_naredba> -> <lista_deklaracija>}</li>
 * </ul>
 * 
 * <p><b>Parse Tree Traversal:</b>
 * <ul>
 *   <li>Recursively searches for IDN terminals (function/variable names)</li>
 *   <li>Handles nested structures (e.g., nested {@code <izravni_deklarator>})</li>
 *   <li>Extracts array sizes from {@code <log_ili_izraz>} expressions</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FunctionInfoExtractor {
    
    /**
     * Information about a local variable including its name and size.
     */
    public record VariableInfo(String name, int sizeInBytes) {}
    
    /**
     * Extracts the function name from a deklarator node.
     * 
     * <p><b>Grammar Rule:</b> Navigates through {@code <deklarator> -> <izravni_deklarator> -> IDN}
     * 
     * <p><b>Parse Tree Structure:</b>
     * <pre>
     * &lt;deklarator&gt; -> &lt;izravni_deklarator&gt; -> ... -> IDN
     * </pre>
     * 
     * @param deklarator the deklarator node ({@code <deklarator>})
     * @return the function name (IDN lexeme), or null if not found
     */
    public String extractFunctionName(NonTerminalNode deklarator) {
        Objects.requireNonNull(deklarator, "deklarator must not be null");
        
        // Navigate through <deklarator> -> <izravni_deklarator> -> <izravni_deklarator> -> IDN
        List<ParseNode> children = deklarator.children();
        
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<izravni_deklarator>".equals(nonTerminal.symbol())) {
                return extractFunctionNameFromDirectDeclarator(nonTerminal);
            }
        }
        
        return null;
    }
    
    /**
     * Extracts the function name from an izravni_deklarator node.
     * 
     * <p>Handles both direct and nested {@code <izravni_deklarator>} structures.
     * 
     * @param directDeclarator the direct declarator node ({@code <izravni_deklarator>})
     * @return the function name (IDN lexeme), or null if not found
     */
    private String extractFunctionNameFromDirectDeclarator(NonTerminalNode directDeclarator) {
        List<ParseNode> children = directDeclarator.children();
        
        for (ParseNode child : children) {
            if (child instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                return terminal.lexeme();
            } else if (child instanceof NonTerminalNode nonTerminal && 
                      "<izravni_deklarator>".equals(nonTerminal.symbol())) {
                // Recursive case for nested izravni_deklarator
                String name = extractFunctionNameFromDirectDeclarator(nonTerminal);
                if (name != null) {
                    return name;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extracts function parameters from a deklarator node.
     * 
     * <p><b>Grammar Rule:</b> Finds {@code <lista_parametara>} within {@code <deklarator>}
     * 
     * @param deklarator the deklarator node ({@code <deklarator>})
     * @return the parameter list node ({@code <lista_parametara>}), or null if not found
     */
    public NonTerminalNode extractFunctionParameters(NonTerminalNode deklarator) {
        Objects.requireNonNull(deklarator, "deklarator must not be null");
        
        // Navigate through the deklarator structure to find parameter list
        return findParameterList(deklarator);
    }
    
    /**
     * Recursively searches for parameter list in the deklarator structure.
     * 
     * @param node the node to search in
     * @return the parameter list node ({@code <lista_parametara>}), or null if not found
     */
    private NonTerminalNode findParameterList(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal) {
                String symbol = nonTerminal.symbol();
                
                if ("<lista_parametara>".equals(symbol)) {
                    return nonTerminal;
                } else {
                    NonTerminalNode result = findParameterList(nonTerminal);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extracts parameter names from the parameter list.
     * 
     * <p><b>Grammar Rule:</b> Processes {@code <lista_parametara>}:
     * <pre>
     * &lt;lista_parametara&gt; ::= &lt;deklaracija_parametra&gt;
     *                        | &lt;lista_parametara&gt; ZAREZ &lt;deklaracija_parametra&gt;
     * </pre>
     * 
     * @param parameters the parameter list node ({@code <lista_parametara>})
     * @return list of parameter names (IDN lexemes)
     */
    public List<String> extractParameterNames(NonTerminalNode parameters) {
        Objects.requireNonNull(parameters, "parameters must not be null");
        
        List<String> names = new ArrayList<>();
        extractParameterNamesRecursive(parameters, names);
        return names;
    }
    
    /**
     * Recursively extracts parameter names from the parameter list structure.
     * 
     * @param node the parameter list node ({@code <lista_parametara>})
     * @param names the list to add parameter names to
     */
    private void extractParameterNamesRecursive(NonTerminalNode node, List<String> names) {
        String symbol = node.symbol();
        
        if ("<lista_parametara>".equals(symbol)) {
            List<ParseNode> children = node.children();
            
            if (children.size() == 1) {
                // Single parameter: <deklaracija_parametra>
                extractParameterFromDeclaration((NonTerminalNode) children.get(0), names);
            } else if (children.size() == 3) {
                // Multiple parameters: <lista_parametara> ZAREZ <deklaracija_parametra>
                extractParameterNamesRecursive((NonTerminalNode) children.get(0), names);
                extractParameterFromDeclaration((NonTerminalNode) children.get(2), names);
            }
        }
    }
    
    /**
     * Extracts parameter name from a parameter declaration.
     * 
     * <p><b>Grammar Rule:</b> Finds IDN terminal in {@code <deklaracija_parametra>}
     * 
     * @param declaration the parameter declaration node ({@code <deklaracija_parametra>})
     * @param names the list to add parameter name to
     */
    private void extractParameterFromDeclaration(NonTerminalNode declaration, List<String> names) {
        // Find the identifier in the parameter declaration
        String paramName = findIdentifierInDeclaration(declaration);
        if (paramName != null) {
            names.add(paramName);
        }
    }
    
    /**
     * Finds the identifier in a declaration node (recursively searches for IDN terminal).
     * 
     * @param node the declaration node
     * @return the identifier name (IDN lexeme), or null if not found
     */
    private String findIdentifierInDeclaration(NonTerminalNode node) {
        for (ParseNode child : node.children()) {
            if (child instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                return terminal.lexeme();
            } else if (child instanceof NonTerminalNode nonTerminal) {
                String result = findIdentifierInDeclaration(nonTerminal);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
    
    /**
     * Extracts local variable information from a function body.
     * 
     * <p><b>Grammar Rule:</b> Processes {@code <slozena_naredba>}:
     * <pre>
     * &lt;slozena_naredba&gt; ::= L_VIT_ZAGRADA &lt;lista_deklaracija&gt; &lt;lista_naredbi&gt; D_VIT_ZAGRADA
     * </pre>
     * 
     * <p><b>FRISC Semantics:</b>
     * <ul>
     *   <li>Element size: 4 bytes for both int and char arrays</li>
     *   <li>Array size: extracted from {@code <log_ili_izraz>} expression</li>
     *   <li>Total size: arraySize * 4 bytes</li>
     * </ul>
     * 
     * @param body the function body node ({@code <slozena_naredba>})
     * @param elementSize the element size in bytes (always 4 for this project)
     * @return list of variable information (name and size in bytes)
     */
    public List<VariableInfo> extractLocalVariables(NonTerminalNode body, int elementSize) {
        Objects.requireNonNull(body, "body must not be null");
        
        List<VariableInfo> variables = new ArrayList<>();
        
        // Find all local variable declarations in the compound statement
        findLocalVariables(body, variables, elementSize);
        
        return variables;
    }
    
    /**
     * Recursively finds local variable declarations in the parse tree.
     * 
     * <p><b>Parse Tree Structure:</b>
     * <pre>
     * &lt;slozena_naredba&gt; -> L_VIT_ZAGRADA &lt;lista_deklaracija&gt; &lt;lista_naredbi&gt; D_VIT_ZAGRADA
     * </pre>
     * 
     * @param node the node to search in
     * @param variables the list to add variable information to
     * @param elementSize the element size in bytes (always 4 for this project)
     */
    private void findLocalVariables(NonTerminalNode node, List<VariableInfo> variables, int elementSize) {
        String symbol = node.symbol();
        
        if ("<lista_deklaracija>".equals(symbol)) {
            // Process declaration list
            processDeclarationList(node, variables, elementSize);
        } else if ("<slozena_naredba>".equals(symbol)) {
            // Compound statement: look for <lista_deklaracija> in children
            for (ParseNode child : node.children()) {
                if (child instanceof NonTerminalNode nonTerminal) {
                    String childSymbol = nonTerminal.symbol();
                    if ("<lista_deklaracija>".equals(childSymbol)) {
                        processDeclarationList(nonTerminal, variables, elementSize);
                    } else {
                        // Continue searching in other children
                        findLocalVariables(nonTerminal, variables, elementSize);
                    }
                }
            }
        } else {
            // Recursively process children
            for (ParseNode child : node.children()) {
                if (child instanceof NonTerminalNode nonTerminal) {
                    findLocalVariables(nonTerminal, variables, elementSize);
                }
            }
        }
    }
    
    /**
     * Processes a declaration list and extracts variable information.
     * 
     * <p><b>Grammar Rule:</b> Processes {@code <lista_deklaracija>}:
     * <pre>
     * &lt;lista_deklaracija&gt; ::= &lt;deklaracija&gt;
     *                         | &lt;lista_deklaracija&gt; &lt;deklaracija&gt;
     * </pre>
     * 
     * @param declarationList the declaration list node ({@code <lista_deklaracija>})
     * @param variables the list to add variable information to
     * @param elementSize the element size in bytes (always 4 for this project)
     */
    private void processDeclarationList(NonTerminalNode declarationList, List<VariableInfo> variables, int elementSize) {
        List<ParseNode> children = declarationList.children();
        
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal) {
                String childSymbol = nonTerminal.symbol();
                
                if ("<lista_deklaracija>".equals(childSymbol)) {
                    // Recursive case
                    processDeclarationList(nonTerminal, variables, elementSize);
                } else if ("<deklaracija>".equals(childSymbol)) {
                    // Process individual declaration
                    List<VariableInfo> declVars = extractVariableInfo(nonTerminal, elementSize);
                    variables.addAll(declVars);
                }
            }
        }
    }
    
    /**
     * Extracts variable information (name and size) from a declaration node.
     * 
     * <p><b>Grammar Rule:</b> Processes {@code <deklaracija>}:
     * <pre>
     * &lt;deklaracija&gt; ::= &lt;ime_tipa&gt; &lt;lista_init_deklaratora&gt;
     * </pre>
     * 
     * <p><b>Parse Tree Structure:</b>
     * <pre>
     * &lt;deklaracija&gt; -> &lt;lista_init_deklaratora&gt; -> &lt;init_deklarator&gt; -> 
     * &lt;deklarator&gt; -> &lt;izravni_deklarator&gt; -> IDN
     * </pre>
     * 
     * <p>For this project, both int and char arrays use element size 4 bytes.
     * 
     * @param declaration the declaration node ({@code <deklaracija>})
     * @param elementSize the element size in bytes (always 4 for this project)
     * @return list of variable information (name and size in bytes)
     */
    private List<VariableInfo> extractVariableInfo(NonTerminalNode declaration, int elementSize) {
        List<VariableInfo> variables = new ArrayList<>();
        
        // Find <lista_init_deklaratora>
        for (ParseNode child : declaration.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<lista_init_deklaratora>".equals(nonTerminal.symbol())) {
                extractVariableInfoFromList(nonTerminal, variables, elementSize);
            }
        }
        
        return variables;
    }
    
    /**
     * Extracts variable information from a list of init declarators.
     * 
     * <p><b>Grammar Rule:</b> Processes {@code <lista_init_deklaratora>}:
     * <pre>
     * &lt;lista_init_deklaratora&gt; ::= &lt;init_deklarator&gt;
     *                             | &lt;lista_init_deklaratora&gt; ZAREZ &lt;init_deklarator&gt;
     * </pre>
     * 
     * @param list the list of init declarators ({@code <lista_init_deklaratora>})
     * @param variables the list to add variable info to
     * @param elementSize the element size in bytes (always 4 for this project)
     */
    private void extractVariableInfoFromList(NonTerminalNode list, List<VariableInfo> variables, int elementSize) {
        List<ParseNode> children = list.children();
        
        if (children.size() == 1) {
            // Single <init_deklarator>
            extractVariableInfoFromInitDeclarator((NonTerminalNode) children.get(0), variables, elementSize);
        } else if (children.size() == 3) {
            // <lista_init_deklaratora> ZAREZ <init_deklarator>
            extractVariableInfoFromList((NonTerminalNode) children.get(0), variables, elementSize);
            extractVariableInfoFromInitDeclarator((NonTerminalNode) children.get(2), variables, elementSize);
        }
    }
    
    /**
     * Extracts variable information from an init declarator.
     * 
     * <p><b>Parse Tree Structure:</b>
     * <pre>
     * &lt;init_deklarator&gt; -> &lt;deklarator&gt; -> &lt;izravni_deklarator&gt; -> IDN
     * </pre>
     * 
     * @param initDeclarator the init declarator node ({@code <init_deklarator>})
     * @param variables the list to add variable info to
     * @param elementSize the element size in bytes (always 4 for this project)
     */
    private void extractVariableInfoFromInitDeclarator(NonTerminalNode initDeclarator, List<VariableInfo> variables, int elementSize) {
        // Find <deklarator>
        for (ParseNode child : initDeclarator.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<deklarator>".equals(nonTerminal.symbol())) {
                extractVariableInfoFromDeclarator(nonTerminal, variables, elementSize);
            }
        }
    }
    
    /**
     * Extracts variable information from a declarator.
     * 
     * <p><b>Parse Tree Structure:</b>
     * <pre>
     * &lt;deklarator&gt; -> &lt;izravni_deklarator&gt; -> IDN
     * </pre>
     * 
     * @param declarator the declarator node ({@code <deklarator>})
     * @param variables the list to add variable info to
     * @param elementSize the element size in bytes (always 4 for this project)
     */
    private void extractVariableInfoFromDeclarator(NonTerminalNode declarator, List<VariableInfo> variables, int elementSize) {
        // Find <izravni_deklarator>
        for (ParseNode child : declarator.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<izravni_deklarator>".equals(nonTerminal.symbol())) {
                extractVariableInfoFromDirectDeclarator(nonTerminal, variables, elementSize);
            }
        }
    }
    
    /**
     * Extracts variable information from a direct declarator.
     * 
     * <p><b>Parse Tree Structure:</b>
     * <ul>
     *   <li>Simple variable: {@code <izravni_deklarator> -> IDN}</li>
     *   <li>Array: {@code <izravni_deklarator> -> IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA}</li>
     *   <li>Nested: {@code <izravni_deklarator> -> <izravni_deklarator> -> IDN L_UGL_ZAGRADA <log_ili_izraz> D_UGL_ZAGRADA}</li>
     * </ul>
     * 
     * <p><b>FRISC Semantics:</b>
     * <ul>
     *   <li>Simple variables: 4 bytes (int or char)</li>
     *   <li>Arrays: arraySize * 4 bytes (element size is 4 bytes)</li>
     *   <li>Array size extracted from {@code <log_ili_izraz>} expression (recursively searches for BROJ)</li>
     * </ul>
     * 
     * @param directDeclarator the direct declarator node ({@code <izravni_deklarator>})
     * @param variables the list to add variable info to
     * @param elementSize the element size in bytes (always 4 for this project)
     */
    private void extractVariableInfoFromDirectDeclarator(NonTerminalNode directDeclarator, List<VariableInfo> variables, int elementSize) {
        String varName = null;
        int arraySize = 0;
        boolean isArray = false;
        
        List<ParseNode> children = directDeclarator.children();
        
        // Handle nested <izravni_deklarator> structure
        // For arrays: <izravni_deklarator> -> <izravni_deklarator> -> IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
        // Or: <izravni_deklarator> -> IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
        NonTerminalNode nestedDeclarator = null;
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<izravni_deklarator>".equals(nonTerminal.symbol())) {
                nestedDeclarator = nonTerminal;
                break;
            }
        }
        
        // Find IDN (variable name) - check both current level and nested level
        for (ParseNode child : children) {
            if (child instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                varName = terminal.lexeme();
                break;
            }
        }
        
        // If nested, check current level for array brackets after nested declarator
        // Structure: <izravni_deklarator> -> <izravni_deklarator> -> IDN, then L_UGL_ZAGRADA <log_ili_izraz> D_UGL_ZAGRADA
        if (nestedDeclarator != null) {
            // Find IDN in nested level
            List<ParseNode> nestedChildren = nestedDeclarator.children();
            for (ParseNode nestedChild : nestedChildren) {
                if (nestedChild instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                    varName = terminal.lexeme();
                    break;
                }
            }
            
            // Check current level for array brackets after nested declarator
            // Structure: <izravni_deklarator> L_UGL_ZAGRADA <log_ili_izraz> D_UGL_ZAGRADA
            // where <log_ili_izraz> contains BROJ terminal
            int nestedIndex = -1;
            for (int i = 0; i < children.size(); i++) {
                if (children.get(i) == nestedDeclarator) {
                    nestedIndex = i;
                    break;
                }
            }
            if (nestedIndex >= 0 && nestedIndex + 3 < children.size()) {
                ParseNode node1 = children.get(nestedIndex + 1);
                ParseNode node2 = children.get(nestedIndex + 2);
                ParseNode node3 = children.get(nestedIndex + 3);
                if (node1 instanceof TerminalNode t1 && "L_UGL_ZAGRADA".equals(t1.symbol()) &&
                    node2 instanceof NonTerminalNode exprNode &&
                    node3 instanceof TerminalNode t3 && "D_UGL_ZAGRADA".equals(t3.symbol())) {
                    // Extract BROJ from <log_ili_izraz> expression
                    String numberValue = extractNumberFromExpression(exprNode);
                    if (numberValue != null) {
                        isArray = true;
                        try {
                            arraySize = Integer.parseInt(numberValue);
                        } catch (NumberFormatException e) {
                            arraySize = 0;
                        }
                    }
                }
            }
            
            // If we found varName and array brackets, we're done
            if (varName != null && isArray && arraySize > 0) {
                variables.add(new VariableInfo(varName, arraySize * elementSize));
                return;
            }
            
            // If we found varName but no array brackets, it's a simple variable
            if (varName != null && !isArray) {
                variables.add(new VariableInfo(varName, 4));
                return;
            }
            
            // Otherwise, recurse into nested level to handle it (might have different structure)
            extractVariableInfoFromDirectDeclarator(nestedDeclarator, variables, elementSize);
            return;
        }
        
        if (varName == null) {
            return; // No variable name found
        }
        
        // Check if it's an array: look for L_UGL_ZAGRADA followed by BROJ followed by D_UGL_ZAGRADA
        // (Only if we haven't already found array brackets above)
        if (!isArray) {
            for (int i = 0; i <= children.size() - 3; i++) {
                if (i + 2 < children.size()) {
                    ParseNode node1 = children.get(i);
                    ParseNode node2 = children.get(i + 1);
                    ParseNode node3 = children.get(i + 2);
                    
                    if (node1 instanceof TerminalNode t1 && "L_UGL_ZAGRADA".equals(t1.symbol()) &&
                        node2 instanceof TerminalNode t2 && "BROJ".equals(t2.symbol()) &&
                        node3 instanceof TerminalNode t3 && "D_UGL_ZAGRADA".equals(t3.symbol())) {
                        // It's an array declaration
                        isArray = true;
                        try {
                            arraySize = Integer.parseInt(t2.lexeme());
                        } catch (NumberFormatException e) {
                            arraySize = 0; // Invalid size, treat as error
                        }
                        break;
                    }
                }
            }
        }
        
        if (isArray) {
            // Array: size * element_size bytes
            variables.add(new VariableInfo(varName, arraySize * elementSize));
        } else {
            // Simple variable: 4 bytes (int or char both stored as 4 bytes on stack)
            variables.add(new VariableInfo(varName, 4));
        }
    }
    
    /**
     * Extracts a number value from an expression node (e.g., <log_ili_izraz>).
     * 
     * <p>Recursively searches for a BROJ terminal and returns its lexeme.
     * This is used to extract array sizes from expressions like {@code int a[5];}.
     * 
     * @param exprNode the expression node ({@code <log_ili_izraz>} or similar)
     * @return the number value as string, or null if not found
     */
    private String extractNumberFromExpression(NonTerminalNode exprNode) {
        if (exprNode == null) {
            return null;
        }
        
        // Search for BROJ terminal in the expression tree
        for (ParseNode child : exprNode.children()) {
            if (child instanceof TerminalNode terminal && "BROJ".equals(terminal.symbol())) {
                return terminal.lexeme();
            } else if (child instanceof NonTerminalNode nonTerminal) {
                String result = extractNumberFromExpression(nonTerminal);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }
}

