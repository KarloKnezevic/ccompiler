package hr.fer.ppj.codegen.types;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import java.util.Objects;

/**
 * Extracts type information from type-related parse tree nodes.
 * 
 * <p>This utility class provides methods to extract type information from
 * type-related parse tree nodes, such as {@code <ime_tipa>} and {@code <specifikator_tipa>}.
 * 
 * <p><b>Grammar Rules Handled:</b>
 * <ul>
 *   <li>{@code <ime_tipa>} - type name nodes</li>
 *   <li>{@code <specifikator_tipa>} - type specifier nodes (KR_CHAR, KR_INT, etc.)</li>
 * </ul>
 * 
 * <p><b>Type Extraction Strategy:</b>
 * 
 * <p>This class uses a two-phase extraction strategy:
 * <ol>
 *   <li><b>Semantic Attributes First:</b> Attempts to extract type from semantic attributes
 *       (preferred, as it's more reliable and includes struct types)</li>
 *   <li><b>Tree Parsing Fallback:</b> Falls back to parsing the tree structure to find
 *       type specifier terminals (KR_CHAR, KR_INT, etc.)</li>
 * </ol>
 * 
 * <p><b>Struct Type Support:</b>
 * 
 * <p>For struct types, the semantic attributes approach is essential, as struct types
 * cannot be determined by parsing terminals alone. The semantic analyzer stores the
 * complete struct type information (including field types, nested structs, etc.) in
 * the semantic attributes.
 * 
 * <p><b>Limitations:</b>
 * 
 * <p>The tree parsing fallback only works for primitive types (char, int) because:
 * <ul>
 *   <li>Struct types require semantic analysis to resolve struct tags and field information</li>
 *   <li>Array types require size information that's not in the parse tree</li>
 *   <li>Pointer types require base type information</li>
 * </ul>
 * 
 * <p>For non-primitive types, semantic attributes must be available.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TypeNodeExtractor {
    
    /**
     * Private constructor to prevent instantiation.
     */
    private TypeNodeExtractor() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * Extracts the target type from a type node ({@code <ime_tipa>}).
     * 
     * <p>This method uses a two-phase approach:
     * <ol>
     *   <li><b>Semantic Attributes:</b> First tries to get the type from semantic attributes
     *       (preferred for struct types and other complex types)</li>
     *   <li><b>Tree Parsing:</b> Falls back to parsing the tree structure to find
     *       type specifier terminals (KR_CHAR, KR_INT)</li>
     * </ol>
     * 
     * <p><b>Struct Type Handling:</b>
     * 
     * <p>For struct type nodes, the semantic attributes approach is essential. The semantic
     * analyzer stores the complete {@link hr.fer.ppj.semantics.types.StructType} information
     * in the attributes, including:
     * <ul>
     *   <li>Struct tag</li>
     *   <li>Field names and types</li>
     *   <li>Nested struct information</li>
     * </ul>
     * 
     * <p>The tree parsing fallback cannot extract struct types, as they require semantic
     * analysis to resolve struct definitions.
     * 
     * @param typeNode the type node ({@code <ime_tipa>})
     * @return the target type, or null if not determinable
     * @throws NullPointerException if typeNode is null
     */
    public static Type extractTypeFromTypeNode(NonTerminalNode typeNode) {
        Objects.requireNonNull(typeNode, "typeNode must not be null");
        
        // Phase 1: Try to get type from semantic attributes first
        // This is preferred because:
        // - It works for all types (primitive, pointer, array, struct)
        // - For struct types, it contains complete struct information
        // - It's more reliable than parsing the tree
        if (typeNode.attributes() != null && typeNode.attributes().type() != null) {
            return typeNode.attributes().type();
        }
        
        // Phase 2: Fallback to parsing tree structure
        // This only works for primitive types (char, int)
        // Grammar: <ime_tipa> -> <specifikator_tipa>
        //          <specifikator_tipa> -> KR_CHAR or KR_INT
        return extractTypeFromSpecifikatorTipa(typeNode);
    }
    
    /**
     * Extracts type from specifikator_tipa node by looking for KR_CHAR or KR_INT terminals.
     * 
     * <p>This method recursively searches the parse tree for type specifier terminals.
     * It only works for primitive types (char, int) because:
     * <ul>
     *   <li>Struct types require semantic analysis to resolve struct definitions</li>
     *   <li>Array types require size information not in the parse tree</li>
     *   <li>Pointer types require base type information</li>
     * </ul>
     * 
     * <p><b>Tree Structure:</b>
     * 
     * <p>The method searches for terminals with symbols:
     * <ul>
     *   <li>{@code KR_CHAR} - returns {@link PrimitiveType#CHAR}</li>
     *   <li>{@code KR_INT} - returns {@link PrimitiveType#INT}</li>
     * </ul>
     * 
     * @param node the node to search (typically {@code <specifikator_tipa>} or parent)
     * @return the primitive type (CHAR or INT), or null if not found
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
                // Recursively search child nodes
                Type result = extractTypeFromSpecifikatorTipa(nonTerminal);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
