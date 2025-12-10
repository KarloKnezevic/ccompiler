package hr.fer.ppj.codegen.utils.address;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;

/**
 * Utility class for pattern matching L-value expressions in the AST.
 * 
 * <p>This class provides static methods to identify different types of L-value patterns:
 * <ul>
 *   <li><b>Field Access:</b> {@code <postfiks_izraz> TOCKA IDN} (e.g., {@code p.x}, {@code o.inner.value})</li>
 *   <li><b>Array Indexing:</b> {@code <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA} (e.g., {@code a[i]}, {@code o.arr[0]})</li>
 * </ul>
 * 
 * <p><b>Purpose:</b>
 * 
 * <p>When generating code for L-values, we need to identify the pattern to determine
 * which code generation strategy to use:
 * <ul>
 *   <li><b>Field Access:</b> Use {@link hr.fer.ppj.codegen.structs.StructFieldAddressGenerator}</li>
 *   <li><b>Array Indexing:</b> Use {@link hr.fer.ppj.codegen.utils.ArrayElementAddressGenerator}</li>
 *   <li><b>Simple Variable:</b> Use direct variable address resolution</li>
 * </ul>
 * 
 * <p><b>Pattern Matching Algorithm:</b>
 * 
 * <p>The pattern matching is based on the AST structure:
 * <ol>
 *   <li>Check the nonterminal symbol (must be {@code <postfiks_izraz>})</li>
 *   <li>Check the number of children</li>
 *   <li>Check the terminal symbols at specific positions</li>
 * </ol>
 * 
 * <p><b>Key Design Decision:</b>
 * 
 * <p>Field access is checked BEFORE array indexing because field access can be the base
 * of array indexing. For example, {@code o.arr[i]} is array indexing with a field access base.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LValuePatternMatcher {
    
    /**
     * Private constructor to prevent instantiation.
     */
    private LValuePatternMatcher() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * Checks if a node represents a field access expression.
     * 
     * <p>Pattern: {@code <postfiks_izraz> TOCKA IDN}
     * 
     * <p>Examples:
     * <ul>
     *   <li>{@code p.x} → true</li>
     *   <li>{@code o.inner.value} → true</li>
     *   <li>{@code a[i]} → false (array indexing)</li>
     *   <li>{@code x} → false (simple variable)</li>
     * </ul>
     * 
     * @param node the node to check
     * @return true if the node is a field access
     */
    public static boolean isFieldAccess(NonTerminalNode node) {
        if (!"<postfiks_izraz>".equals(node.symbol())) {
            return false;
        }
        List<ParseNode> children = node.children();
        if (children.size() != 3) {
            return false;
        }
        ParseNode second = children.get(1);
        return second instanceof TerminalNode terminal && "TOCKA".equals(terminal.symbol());
    }
    
    /**
     * Checks if a node represents an array indexing expression.
     * 
     * <p>Pattern: {@code <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA}
     * 
     * <p>This method ONLY checks for the {@code [...]} pattern. It does NOT require
     * that the base is a simple variable - it can be a field access ({@code a.arr[i]}),
     * nested struct access ({@code o.inner.arr[i]}), etc.
     * 
     * <p>Examples:
     * <ul>
     *   <li>{@code a[i]} → true</li>
     *   <li>{@code o.arr[0]} → true (array indexing with field access base)</li>
     *   <li>{@code p.x} → false (field access)</li>
     *   <li>{@code x} → false (simple variable)</li>
     * </ul>
     * 
     * @param node the node to check
     * @return true if the node is array indexing
     */
    public static boolean isArrayIndexing(NonTerminalNode node) {
        if (!"<postfiks_izraz>".equals(node.symbol())) {
            return false;
        }
        List<ParseNode> children = node.children();
        if (children.size() != 4) {
            return false;
        }
        ParseNode second = children.get(1);
        ParseNode fourth = children.get(3);
        return second instanceof TerminalNode terminal1 && "L_UGL_ZAGRADA".equals(terminal1.symbol()) &&
               fourth instanceof TerminalNode terminal2 && "D_UGL_ZAGRADA".equals(terminal2.symbol());
    }
}
