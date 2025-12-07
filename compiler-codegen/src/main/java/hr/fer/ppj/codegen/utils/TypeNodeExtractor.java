package hr.fer.ppj.codegen.utils;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import java.util.Objects;

/**
 * Extracts type information from parse tree nodes.
 * 
 * <p>This utility class provides methods to extract type information from
 * type-related parse tree nodes, such as {@code <ime_tipa>} and {@code <specifikator_tipa>}.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TypeNodeExtractor {
    
    /**
     * Extracts the target type from a type node ({@code <ime_tipa>}).
     * 
     * <p>First tries to get the type from semantic attributes, then falls back
     * to parsing the tree structure.
     * 
     * @param typeNode the type node
     * @return the target type, or null if not determinable
     */
    public static Type extractTypeFromTypeNode(NonTerminalNode typeNode) {
        Objects.requireNonNull(typeNode, "typeNode must not be null");
        
        // Try to get type from semantic attributes first
        if (typeNode.attributes() != null && typeNode.attributes().type() != null) {
            return typeNode.attributes().type();
        }
        
        // Fallback: parse from tree structure
        // <ime_tipa> -> <specifikator_tipa>
        // <specifikator_tipa> -> KR_CHAR or KR_INT
        return extractTypeFromSpecifikatorTipa(typeNode);
    }
    
    /**
     * Extracts type from specifikator_tipa node by looking for KR_CHAR or KR_INT.
     * 
     * @param node the node to search
     * @return the type, or null if not found
     */
    private static Type extractTypeFromSpecifikatorTipa(NonTerminalNode node) {
        // Recursively search for KR_CHAR or KR_INT terminal
        for (ParseNode child : node.children()) {
            if (child instanceof TerminalNode terminal) {
                String symbol = terminal.symbol();
                if ("KR_CHAR".equals(symbol)) {
                    return PrimitiveType.CHAR;
                } else if ("KR_INT".equals(symbol)) {
                    return PrimitiveType.INT;
                }
            } else if (child instanceof NonTerminalNode nonTerminal) {
                Type result = extractTypeFromSpecifikatorTipa(nonTerminal);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}

