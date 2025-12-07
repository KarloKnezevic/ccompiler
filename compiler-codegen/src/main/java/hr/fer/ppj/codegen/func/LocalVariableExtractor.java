package hr.fer.ppj.codegen.func;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Extracts local variable information from function body nodes.
 * 
 * <p>This class processes compound statements ({@code <slozena_naredba>}) to extract
 * information about local variables, including their names and sizes.
 * 
 * <p><b>Grammar Rule:</b> Processes {@code <slozena_naredba>}:
 * <pre>
 * &lt;slozena_naredba&gt; ::= L_VIT_ZAGRADA &lt;lista_deklaracija&gt; &lt;lista_naredbi&gt; D_VIT_ZAGRADA
 * </pre>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LocalVariableExtractor {
    
    /**
     * Information about a local variable including its name and size.
     */
    public record VariableInfo(String name, int sizeInBytes) {}
    
    /**
     * Extracts local variable information from a function body.
     * 
     * @param body the function body node ({@code <slozena_naredba>})
     * @param elementSize the element size in bytes (always 4 for this project)
     * @return list of variable information (name and size in bytes)
     */
    public List<VariableInfo> extractLocalVariables(NonTerminalNode body, int elementSize) {
        Objects.requireNonNull(body, "body must not be null");
        
        List<VariableInfo> variables = new ArrayList<>();
        findLocalVariables(body, variables, elementSize);
        return variables;
    }
    
    /**
     * Recursively finds local variable declarations in the parse tree.
     * 
     * @param node the node to search in
     * @param variables the list to add variable information to
     * @param elementSize the element size in bytes (always 4 for this project)
     */
    private void findLocalVariables(NonTerminalNode node, List<VariableInfo> variables, int elementSize) {
        String symbol = node.symbol();
        
        if ("<lista_deklaracija>".equals(symbol)) {
            processDeclarationList(node, variables, elementSize);
        } else if ("<slozena_naredba>".equals(symbol)) {
            for (ParseNode child : node.children()) {
                if (child instanceof NonTerminalNode nonTerminal) {
                    String childSymbol = nonTerminal.symbol();
                    if ("<lista_deklaracija>".equals(childSymbol)) {
                        processDeclarationList(nonTerminal, variables, elementSize);
                    } else {
                        findLocalVariables(nonTerminal, variables, elementSize);
                    }
                }
            }
        } else {
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
                    processDeclarationList(nonTerminal, variables, elementSize);
                } else if ("<deklaracija>".equals(childSymbol)) {
                    List<VariableInfo> declVars = extractVariableInfo(nonTerminal, elementSize);
                    variables.addAll(declVars);
                }
            }
        }
    }
    
    /**
     * Extracts variable information (name and size) from a declaration node.
     * 
     * @param declaration the declaration node ({@code <deklaracija>})
     * @param elementSize the element size in bytes (always 4 for this project)
     * @return list of variable information (name and size in bytes)
     */
    private List<VariableInfo> extractVariableInfo(NonTerminalNode declaration, int elementSize) {
        List<VariableInfo> variables = new ArrayList<>();
        
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
     * @param list the list of init declarators ({@code <lista_init_deklaratora>})
     * @param variables the list to add variable info to
     * @param elementSize the element size in bytes (always 4 for this project)
     */
    private void extractVariableInfoFromList(NonTerminalNode list, List<VariableInfo> variables, int elementSize) {
        List<ParseNode> children = list.children();
        
        if (children.size() == 1) {
            extractVariableInfoFromInitDeclarator((NonTerminalNode) children.get(0), variables, elementSize);
        } else if (children.size() == 3) {
            extractVariableInfoFromList((NonTerminalNode) children.get(0), variables, elementSize);
            extractVariableInfoFromInitDeclarator((NonTerminalNode) children.get(2), variables, elementSize);
        }
    }
    
    /**
     * Extracts variable information from an init declarator.
     * 
     * @param initDeclarator the init declarator node ({@code <init_deklarator>})
     * @param variables the list to add variable info to
     * @param elementSize the element size in bytes (always 4 for this project)
     */
    private void extractVariableInfoFromInitDeclarator(NonTerminalNode initDeclarator, List<VariableInfo> variables, int elementSize) {
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
     * @param declarator the declarator node ({@code <deklarator>})
     * @param variables the list to add variable info to
     * @param elementSize the element size in bytes (always 4 for this project)
     */
    private void extractVariableInfoFromDeclarator(NonTerminalNode declarator, List<VariableInfo> variables, int elementSize) {
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
     * <p>Handles both simple variables and arrays, extracting array sizes from
     * expression nodes when present.
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
        NonTerminalNode nestedDeclarator = null;
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<izravni_deklarator>".equals(nonTerminal.symbol())) {
                nestedDeclarator = nonTerminal;
                break;
            }
        }
        
        // Find IDN (variable name)
        for (ParseNode child : children) {
            if (child instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                varName = terminal.lexeme();
                break;
            }
        }
        
        // Handle nested declarator case
        if (nestedDeclarator != null) {
            List<ParseNode> nestedChildren = nestedDeclarator.children();
            for (ParseNode nestedChild : nestedChildren) {
                if (nestedChild instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                    varName = terminal.lexeme();
                    break;
                }
            }
            
            // Check for array brackets after nested declarator
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
            
            if (varName != null && isArray && arraySize > 0) {
                variables.add(new VariableInfo(varName, arraySize * elementSize));
                return;
            }
            
            if (varName != null && !isArray) {
                variables.add(new VariableInfo(varName, 4));
                return;
            }
            
            extractVariableInfoFromDirectDeclarator(nestedDeclarator, variables, elementSize);
            return;
        }
        
        if (varName == null) {
            return;
        }
        
        // Check if it's an array
        if (!isArray) {
            for (int i = 0; i <= children.size() - 3; i++) {
                if (i + 2 < children.size()) {
                    ParseNode node1 = children.get(i);
                    ParseNode node2 = children.get(i + 1);
                    ParseNode node3 = children.get(i + 2);
                    
                    if (node1 instanceof TerminalNode t1 && "L_UGL_ZAGRADA".equals(t1.symbol()) &&
                        node2 instanceof TerminalNode t2 && "BROJ".equals(t2.symbol()) &&
                        node3 instanceof TerminalNode t3 && "D_UGL_ZAGRADA".equals(t3.symbol())) {
                        isArray = true;
                        try {
                            arraySize = Integer.parseInt(t2.lexeme());
                        } catch (NumberFormatException e) {
                            arraySize = 0;
                        }
                        break;
                    }
                }
            }
        }
        
        if (isArray) {
            variables.add(new VariableInfo(varName, arraySize * elementSize));
        } else {
            variables.add(new VariableInfo(varName, 4));
        }
    }
    
    /**
     * Extracts a number value from an expression node.
     * 
     * <p>Recursively searches for a BROJ terminal and returns its lexeme.
     * 
     * @param exprNode the expression node
     * @return the number value as string, or null if not found
     */
    private String extractNumberFromExpression(NonTerminalNode exprNode) {
        if (exprNode == null) {
            return null;
        }
        
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

