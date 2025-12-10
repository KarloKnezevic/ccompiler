package hr.fer.ppj.codegen.utils.address;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for unwrapping expression layers to extract underlying L-values.
 * 
 * <p>This class handles the recursive unwrapping of expression nonterminals that don't
 * change the L-value nature of an expression. It's used to extract the actual L-value
 * from expressions wrapped in various grammar layers.
 * 
 * <p><b>Purpose:</b>
 * 
 * <p>In the C grammar, L-values can be wrapped in multiple expression layers:
 * <ul>
 *   <li>{@code <izraz_pridruzivanja>} - assignment expressions</li>
 *   <li>{@code <log_ili_izraz>}, {@code <log_i_izraz>} - logical expressions</li>
 *   <li>{@code <bin_ili_izraz>}, {@code <bin_xili_izraz>}, {@code <bin_i_izraz>} - bitwise expressions</li>
 *   <li>{@code <jednakosni_izraz>}, {@code <odnosni_izraz>} - comparison expressions</li>
 *   <li>{@code <aditivni_izraz>}, {@code <multiplikativni_izraz>} - arithmetic expressions</li>
 *   <li>{@code <cast_izraz>}, {@code <unarni_izraz>} - unary expressions</li>
 * </ul>
 * 
 * <p>These layers don't change the L-value nature, so we can safely unwrap them
 * to get to the underlying {@code <postfiks_izraz>} or {@code <primarni_izraz>}.
 * 
 * <p><b>Algorithm:</b>
 * 
 * <p>The unwrapping algorithm recursively processes single-child nonterminals:
 * <ol>
 *   <li>Check if the node is a single-child nonterminal</li>
 *   <li>If it's an expression layer (doesn't change L-value), recursively unwrap the child</li>
 *   <li>Stop when reaching {@code <postfiks_izraz>} or {@code <primarni_izraz>}</li>
 * </ol>
 * 
 * <p><b>Example:</b>
 * <pre>
 * // Input: <izraz_pridruzivanja> -> <log_ili_izraz> -> <postfiks_izraz> -> <primarni_izraz> -> IDN
 * // Output: <postfiks_izraz> (or <primarni_izraz>)
 * 
 * NonTerminalNode wrapped = ...; // <izraz_pridruzivanja>
 * NonTerminalNode unwrapped = ExpressionUnwrapper.unwrap(wrapped);
 * // unwrapped is <postfiks_izraz> or <primarni_izraz>
 * </pre>
 * 
 * <p><b>Key Invariant:</b>
 * 
 * <p>Unwrapping preserves the structure of nested field/array accesses. For example:
 * <ul>
 *   <li>{@code o.middle.inner.arr[0]} remains {@code <postfiks_izraz>} with nested structure</li>
 *   <li>{@code p.x} remains {@code <postfiks_izraz>} with field access structure</li>
 * </ul>
 * 
 * <p>This is critical because field access and array indexing are preserved correctly
 * for address generation.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ExpressionUnwrapper {
    
    /**
     * Private constructor to prevent instantiation.
     */
    private ExpressionUnwrapper() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * Unwraps all expression layers to get to the underlying L-value.
     * 
     * <p>This recursively unwraps expression nonterminals that don't change the L-value nature:
     * <ul>
     *   <li>{@code <izraz_pridruzivanja>} → unwraps to child</li>
     *   <li>{@code <log_ili_izraz>}, {@code <log_i_izraz>}, {@code <bin_ili_izraz>}, etc. → unwraps to child</li>
     *   <li>{@code <jednakosni_izraz>}, {@code <odnosni_izraz>}, {@code <aditivni_izraz>}, etc. → unwraps to child</li>
     *   <li>{@code <multiplikativni_izraz>}, {@code <cast_izraz>} → unwraps to child</li>
     *   <li>{@code <unarni_izraz>} → unwraps to child</li>
     * </ul>
     * 
     * <p>Stops when it reaches {@code <postfiks_izraz>} or {@code <primarni_izraz>}, which are the actual L-values.
     * 
     * <p><b>Preservation Guarantee:</b>
     * 
     * <p>This method preserves the structure of nested field/array accesses. For example:
     * <ul>
     *   <li>{@code o.middle.inner.arr[0]} remains {@code <postfiks_izraz>} with nested structure intact</li>
     *   <li>{@code p.x} remains {@code <postfiks_izraz>} with field access structure intact</li>
     * </ul>
     * 
     * @param node the node to unwrap
     * @return the unwrapped node (should be {@code <postfiks_izraz>} or {@code <primarni_izraz>})
     * @throws NullPointerException if node is null
     */
    public static NonTerminalNode unwrap(NonTerminalNode node) {
        Objects.requireNonNull(node, "node must not be null");
        
        String symbol = node.symbol();
        List<ParseNode> children = node.children();
        
        // Expression layers that can be unwrapped (single-child nonterminals)
        if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
            // These are expression layers that don't change the L-value nature
            if (isExpressionLayer(symbol)) {
                // Recursively unwrap
                return unwrap(child);
            }
        }
        
        // If it's already a postfiks_izraz or primarni_izraz, return as-is
        // (these are the actual L-values)
        return node;
    }
    
    /**
     * Checks if a symbol represents an expression layer that can be unwrapped.
     * 
     * <p>Expression layers are nonterminals that wrap other expressions but don't
     * change the L-value nature. They can be safely unwrapped to get to the
     * underlying L-value.
     * 
     * @param symbol the nonterminal symbol to check
     * @return true if the symbol is an expression layer that can be unwrapped
     */
    private static boolean isExpressionLayer(String symbol) {
        return "<izraz_pridruzivanja>".equals(symbol) ||
               "<log_ili_izraz>".equals(symbol) ||
               "<log_i_izraz>".equals(symbol) ||
               "<bin_ili_izraz>".equals(symbol) ||
               "<bin_xili_izraz>".equals(symbol) ||
               "<bin_i_izraz>".equals(symbol) ||
               "<jednakosni_izraz>".equals(symbol) ||
               "<odnosni_izraz>".equals(symbol) ||
               "<aditivni_izraz>".equals(symbol) ||
               "<multiplikativni_izraz>".equals(symbol) ||
               "<cast_izraz>".equals(symbol) ||
               "<unarni_izraz>".equals(symbol);
    }
}
