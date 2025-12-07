package hr.fer.ppj.codegen.func;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Extracts function parameters from parse tree nodes.
 * 
 * <p>This class handles extraction of parameter lists and parameter names
 * from function declarator nodes.
 * 
 * <p><b>Grammar Rules Handled:</b>
 * <ul>
 *   <li>{@code <lista_parametara> ::= <deklaracija_parametra>}</li>
 *   <li>{@code <lista_parametara> ::= <lista_parametara> ZAREZ <deklaracija_parametra>}</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ParameterExtractor {
    
    /**
     * Extracts function parameters from a deklarator node.
     * 
     * @param deklarator the deklarator node ({@code <deklarator>})
     * @return the parameter list node ({@code <lista_parametara>}), or null if not found
     */
    public NonTerminalNode extractFunctionParameters(NonTerminalNode deklarator) {
        Objects.requireNonNull(deklarator, "deklarator must not be null");
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
     * @param declaration the parameter declaration node ({@code <deklaracija_parametra>})
     * @param names the list to add parameter name to
     */
    private void extractParameterFromDeclaration(NonTerminalNode declaration, List<String> names) {
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
        return hr.fer.ppj.codegen.utils.IdentifierExtractor.findIdentifier(node);
    }
}

