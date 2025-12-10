package hr.fer.ppj.codegen.stmt.decl;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Extracts array size information from declarator nodes in the parse tree.
 * 
 * <p>This class handles the extraction of array dimensions from C declarator syntax,
 * which is needed to calculate the total size of array variables for stack allocation.
 * 
 * <p><b>Purpose:</b>
 * 
 * <p>When processing local variable declarations like {@code int arr[5];}, we need
 * to extract the array size (5) to calculate the total memory needed (5 * 4 = 20 bytes).
 * This class traverses the declarator AST structure to find array dimension specifications.
 * 
 * <p><b>Grammar Rule:</b>
 * 
 * <p>Handles array declarators from {@code <deklarator>} and {@code <izravni_deklarator>}:
 * <pre>
 * &lt;izravni_deklarator&gt; ::= IDN
 *                         | &lt;izravni_deklarator&gt; L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
 *                         | &lt;izravni_deklarator&gt; L_UGL_ZAGRADA D_UGL_ZAGRADA
 * </pre>
 * 
 * <p><b>Algorithm:</b>
 * 
 * <p>The extraction algorithm works recursively:
 * <ol>
 *   <li>Traverse the declarator structure looking for {@code <izravni_deklarator>} nodes</li>
 *   <li>Search for the pattern: {@code L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA}</li>
 *   <li>Extract the numeric value from the {@code BROJ} terminal</li>
 *   <li>Handle nested declarators (for multi-dimensional arrays)</li>
 * </ol>
 * 
 * <p><b>Limitations:</b>
 * 
 * <p>This class extracts array sizes from the parse tree structure. It defaults to
 * 4 bytes per element (int size) when calculating total size, as it doesn't have
 * access to the type specifier. The actual element size should be determined from
 * semantic attributes when available.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ArraySizeExtractor {
    
    /**
     * Private constructor to prevent instantiation.
     */
    private ArraySizeExtractor() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * Extracts array size from a declarator node.
     * 
     * <p>Returns 4 (default) for non-arrays, or size * element_size for arrays.
     * Note: This method is used as a fallback when variable is not in activation record.
     * It defaults to 4 bytes per element (int), but ideally should check the type specifier.
     * 
     * @param declarator the declarator node ({@code <deklarator>})
     * @return the size in bytes (4 for simple variables, size * 4 for arrays)
     * @throws NullPointerException if declarator is null
     */
    public static int extractArraySize(NonTerminalNode declarator) {
        Objects.requireNonNull(declarator, "declarator must not be null");
        
        // Find <izravni_deklarator>
        for (ParseNode child : declarator.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<izravni_deklarator>".equals(nonTerminal.symbol())) {
                return extractArraySizeFromDirectDeclarator(nonTerminal);
            }
        }
        return 4; // Default: simple variable
    }
    
    /**
     * Extracts array size from a direct declarator.
     * 
     * <p>Defaults to 4 bytes per element (int). For char arrays, this should be 1 byte,
     * but without access to the type specifier, we default to int.
     * 
     * @param directDeclarator the direct declarator node ({@code <izravni_deklarator>})
     * @return the size in bytes (4 for simple variables, size * 4 for arrays)
     */
    private static int extractArraySizeFromDirectDeclarator(NonTerminalNode directDeclarator) {
        List<ParseNode> children = directDeclarator.children();
        
        // Handle nested <izravni_deklarator> structure
        // For arrays: <izravni_deklarator> -> <izravni_deklarator> -> IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
        NonTerminalNode nestedDeclarator = null;
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<izravni_deklarator>".equals(nonTerminal.symbol())) {
                nestedDeclarator = nonTerminal;
                break;
            }
        }
        
        // If nested, recurse into it
        if (nestedDeclarator != null) {
            return extractArraySizeFromDirectDeclarator(nestedDeclarator);
        }
        
        // Check if it's an array: look for L_UGL_ZAGRADA followed by BROJ followed by D_UGL_ZAGRADA
        // Need to check all possible positions (including at the end)
        for (int i = 0; i <= children.size() - 3; i++) {
            if (i + 2 < children.size()) {
                ParseNode node1 = children.get(i);
                ParseNode node2 = children.get(i + 1);
                ParseNode node3 = children.get(i + 2);
                
                if (node1 instanceof TerminalNode t1 && "L_UGL_ZAGRADA".equals(t1.symbol()) &&
                    node2 instanceof TerminalNode t2 && "BROJ".equals(t2.symbol()) &&
                    node3 instanceof TerminalNode t3 && "D_UGL_ZAGRADA".equals(t3.symbol())) {
                    // It's an array declaration
                    try {
                        int arraySize = Integer.parseInt(t2.lexeme());
                        // Default to 4 bytes per element (int)
                        // Note: For char arrays, this should be 1, but we don't have type info here
                        // The correct size should be determined from the type specifier in the declaration
                        return arraySize * 4; // 4 bytes per element (int)
                    } catch (NumberFormatException e) {
                        return 4; // Invalid size, treat as simple variable
                    }
                }
            }
        }
        
        return 4; // Not an array, simple variable
    }
    
    /**
     * Extracts array length from a declarator node.
     * 
     * <p>Tries to get elementCount from semantic attributes first,
     * then falls back to parsing the declarator structure.
     * 
     * @param declarator the declarator node ({@code <deklarator>})
     * @return the array length, or 0 if not found
     */
    public static int extractArrayLength(NonTerminalNode declarator) {
        Objects.requireNonNull(declarator, "declarator must not be null");
        
        // Try semantic attributes first
        if (declarator.attributes() != null) {
            int elementCount = declarator.attributes().elementCount();
            if (elementCount > 0) {
                return elementCount;
            }
        }
        
        // Fallback: search parse tree for array size
        return extractArrayLengthFromDirectDeclarator(declarator);
    }
    
    /**
     * Recursively searches for array length in declarator structure.
     * 
     * <p>Looks for pattern: IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
     * 
     * @param node the node to search
     * @return the array length, or 0 if not found
     */
    private static int extractArrayLengthFromDirectDeclarator(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        // Check for array pattern: ... L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
        for (int i = 0; i <= children.size() - 3; i++) {
            if (i + 2 < children.size()) {
                ParseNode node1 = children.get(i);
                ParseNode node2 = children.get(i + 1);
                ParseNode node3 = children.get(i + 2);
                
                if (node1 instanceof TerminalNode t1 && "L_UGL_ZAGRADA".equals(t1.symbol()) &&
                    node2 instanceof TerminalNode t2 && "BROJ".equals(t2.symbol()) &&
                    node3 instanceof TerminalNode t3 && "D_UGL_ZAGRADA".equals(t3.symbol())) {
                    try {
                        return Integer.parseInt(t2.lexeme());
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                }
            }
        }
        
        // Recursively search children
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal) {
                int size = extractArrayLengthFromDirectDeclarator(nonTerminal);
                if (size > 0) {
                    return size;
                }
            }
        }
        
        return 0;
    }
}
