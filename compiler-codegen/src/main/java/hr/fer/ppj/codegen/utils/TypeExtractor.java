package hr.fer.ppj.codegen.utils;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.types.Type;
import java.util.Objects;

/**
 * Utility class for extracting type information from parse tree nodes.
 * 
 * <p>This class provides a centralized way to extract type information from expression
 * nodes, eliminating duplication across generators. It implements the <b>type extraction
 * pattern</b> used throughout the code generation module.
 * 
 * <p><b>Design Pattern: Utility Class</b>
 * 
 * <p>This class implements the <b>utility class pattern</b>:
 * <ul>
 *   <li>All methods are static (no instance state)</li>
 *   <li>Private constructor prevents instantiation</li>
 *   <li>Provides a single, consistent way to extract types</li>
 * </ul>
 * 
 * <p><b>Type Information Source:</b>
 * 
 * <p>Type information is stored in the <b>semantic attributes</b> of parse tree nodes,
 * which are added during the semantic analysis phase. The attributes contain:
 * <ul>
 *   <li><b>Type:</b> The semantic type of the expression (int, char, float, pointer, etc.)</li>
 *   <li><b>L-value:</b> Whether the expression is an l-value (can appear on left side of assignment)</li>
 *   <li><b>Other semantic information:</b> Array sizes, function signatures, etc.</li>
 * </ul>
 * 
 * <p><b>Why Centralize Type Extraction?</b>
 * 
 * <p>Type extraction is needed in many places during code generation:
 * <ul>
 *   <li>Binary operations need to know operand types to choose the correct operation
 *       (integer vs. float arithmetic)</li>
 *   <li>Type conversions need to know source and target types</li>
 *   <li>Array indexing needs to know the element type</li>
 *   <li>Function calls need to know parameter types</li>
 * </ul>
 * 
 * <p>Centralizing this logic:
 * <ul>
 *   <li>Eliminates code duplication</li>
 *   <li>Ensures consistent type extraction across all generators</li>
 *   <li>Makes it easier to change how types are stored/accessed</li>
 *   <li>Provides a single point of failure for debugging type-related issues</li>
 * </ul>
 * 
 * <p><b>Type Extraction Algorithm:</b>
 * 
 * <p>The algorithm is straightforward:
 * <ol>
 *   <li>Check if the node has semantic attributes (added during semantic analysis)</li>
 *   <li>If attributes exist, extract the type from the attributes</li>
 *   <li>If attributes don't exist, return null (indicates a semantic analysis error)</li>
 * </ol>
 * 
 * <p><b>Null Return Value:</b>
 * 
 * <p>This method may return null if:
 * <ul>
 *   <li>The node has no semantic attributes (semantic analysis error)</li>
 *   <li>The node is not an expression node (should not happen in normal operation)</li>
 * </ul>
 * 
 * <p>Callers should handle null return values appropriately. In practice, semantic
 * analysis should have already validated the parse tree, so null should be rare.
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) - simple attribute access</li>
 *   <li><b>Space Complexity:</b> O(1) - no additional memory required</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TypeExtractor {
    
    /**
     * Private constructor to prevent instantiation.
     */
    private TypeExtractor() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * Gets the type of an expression from its semantic attributes.
     * 
     * @param node the expression node
     * @return the type, or null if not available
     */
    public static Type getExpressionType(NonTerminalNode node) {
        Objects.requireNonNull(node, "node must not be null");
        
        if (node.attributes() != null) {
            return node.attributes().type();
        }
        return null;
    }
}

