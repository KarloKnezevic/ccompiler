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
     * <p>Structure: <deklaracija_parametra> ::= <ime_tipa> IDN | <ime_tipa> <deklarator>
     * 
     * <p>For simple parameters (<ime_tipa> IDN), the IDN is child 1.
     * For complex parameters (<ime_tipa> <deklarator>), we need to search the declarator,
     * not the type specification, to avoid finding the struct tag name instead of the parameter name.
     * 
     * @param declaration the parameter declaration node ({@code <deklaracija_parametra>})
     * @param names the list to add parameter name to
     */
    private void extractParameterFromDeclaration(NonTerminalNode declaration, List<String> names) {
        // First, try to get identifier from semantic attributes (set by semantic analysis)
        if (declaration.attributes() != null && declaration.attributes().identifier() != null) {
            names.add(declaration.attributes().identifier());
            return;
        }
        
        // Fallback: extract from parse tree structure
        // Structure: <deklaracija_parametra> has children: <ime_tipa> [IDN | <deklarator>]
        List<ParseNode> children = declaration.children();
        if (children.size() >= 2) {
            ParseNode secondChild = children.get(1);
            
            // Case 1: Simple parameter: <ime_tipa> IDN
            if (secondChild instanceof hr.fer.ppj.semantics.tree.TerminalNode terminal && 
                "IDN".equals(terminal.symbol())) {
                names.add(terminal.lexeme());
                return;
            }
            
            // Case 2: Complex parameter: <ime_tipa> <deklarator>
            // Search only in the declarator (child 1), not in the type specification (child 0)
            // This avoids finding the struct tag name (e.g., "Outer") instead of the parameter name (e.g., "o")
            if (secondChild instanceof NonTerminalNode declarator) {
                String paramName = hr.fer.ppj.codegen.utils.IdentifierExtractor.findIdentifier(declarator);
                if (paramName != null) {
                    names.add(paramName);
                    return;
                }
            }
        }
        
        // Last resort: search entire node (might find wrong identifier for struct parameters)
        String paramName = hr.fer.ppj.codegen.utils.IdentifierExtractor.findIdentifier(declaration);
        if (paramName != null) {
            names.add(paramName);
        }
    }
}

