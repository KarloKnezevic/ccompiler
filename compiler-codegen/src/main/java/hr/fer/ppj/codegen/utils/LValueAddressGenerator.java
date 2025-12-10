package hr.fer.ppj.codegen.utils;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.env.VariableAddressResolver;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.structs.StructArraySizeExtractor;
import hr.fer.ppj.codegen.structs.StructFieldAddressGenerator;
import hr.fer.ppj.codegen.utils.ArrayElementAddressGenerator;
import hr.fer.ppj.codegen.utils.address.ExpressionUnwrapper;
import hr.fer.ppj.codegen.utils.address.LValuePatternMatcher;
import hr.fer.ppj.codegen.utils.address.VariableAddressLoader;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Central address generator for L-values (left-hand sides of assignments).
 * 
 * <p>This class provides a unified interface for computing addresses of:
 * <ul>
 *   <li>Simple variables (IDN)</li>
 *   <li>Array elements (a[i])</li>
 *   <li>Struct fields (p.x, o.inner.value)</li>
 *   <li>Nested combinations (o.arr[i])</li>
 * </ul>
 * 
 * <p><b>Key Invariant:</b> This generator computes ADDRESSES, not values.
 * For struct variables, it computes the address of the struct object itself
 * (e.g., R5-8 for a local struct), NOT the value stored at that address.
 * 
 * <p><b>Algorithm:</b>
 * <ol>
 *   <li>If simple variable: compute address from frame pointer or global label</li>
 *   <li>If field access: recursively compute base address, then add field offset</li>
 *   <li>If array indexing: compute base address, then add index * elementSize</li>
 * </ol>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LValueAddressGenerator {
    
    private final CodeGenContext context;
    private final VariableAddressResolver addressResolver;
    private final ExpressionCodeGenerator expressionGenerator;
    private final VariableAddressLoader addressLoader;
    private StructArraySizeExtractor arraySizeExtractor;
    
    /**
     * Creates a new L-value address generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the expression generator (for evaluating array indices)
     */
    public LValueAddressGenerator(CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.addressResolver = new VariableAddressResolver(context);
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
        this.addressLoader = new VariableAddressLoader(context);
        // Parse tree will be set later if needed (lazy initialization)
        this.arraySizeExtractor = null;
    }
    
    /**
     * Sets the parse tree for extracting struct array sizes.
     * 
     * @param parseTree the parse tree from semantic analysis
     */
    public void setParseTree(NonTerminalNode parseTree) {
        this.arraySizeExtractor = new StructArraySizeExtractor(parseTree);
    }
    
    /**
     * Gets the array size extractor (for use by other generators).
     * 
     * @return the array size extractor, or null if not set
     */
    public StructArraySizeExtractor getArraySizeExtractor() {
        return arraySizeExtractor;
    }
    
    /**
     * Generates code to compute the address of an L-value expression.
     * 
     * <p>The computed address is left in register R0.
     * 
     * <p>This method handles:
     * <ul>
     *   <li>Simple variables: {@code x} → address of x</li>
     *   <li>Field access: {@code p.x} → address of field x in struct p</li>
     *   <li>Nested field access: {@code o.inner.value} → address of nested field</li>
     *   <li>Array indexing: {@code a[i]} → address of array element</li>
     * </ul>
     * 
     * @param node the L-value expression node (typically a {@code <postfiks_izraz>})
     * @throws IllegalStateException if the node is not a valid L-value
     */
    public void generateAddress(NonTerminalNode node) {
        generateAddress(node, "R0");
    }
    
    /**
     * Generates code to compute the address of an L-value expression.
     * 
     * <p>The computed address is left in the specified target register.
     * 
     * <p>This method handles arbitrary nesting depth:
     * <ul>
     *   <li>Simple variables: {@code x}</li>
     *   <li>Field access: {@code p.x}, {@code o.inner.value}</li>
     *   <li>Array indexing: {@code a[i]}, {@code o.arr[i]}</li>
     *   <li>Deeply nested: {@code o.middle.inner.arr[0]}</li>
     * </ul>
     * 
     * <p>The algorithm recursively processes each level:
     * <ol>
     *   <li>If field access: recursively compute base address, add field offset</li>
     *   <li>If array indexing: recursively compute base address, add index * elementSize</li>
     *   <li>If simple variable: compute variable address</li>
     * </ol>
     * 
     * @param node the L-value expression node
     * @param targetRegister the register to store the address in
     * @throws IllegalStateException if the node is not a valid L-value
     */
    public void generateAddress(NonTerminalNode node, String targetRegister) {
        Objects.requireNonNull(node, "node must not be null");
        Objects.requireNonNull(targetRegister, "targetRegister must not be null");
        
        // Unwrap all expression layers to get to the underlying l-value
        // This handles cases where the node is wrapped in <izraz_pridruzivanja>, <log_ili_izraz>, etc.
        // Note: ExpressionUnwrapper.unwrap() preserves <postfiks_izraz> structure, so nested
        // field/array accesses are preserved correctly
        NonTerminalNode unwrapped = ExpressionUnwrapper.unwrap(node);
        
        // Check for field access FIRST (before array indexing) because field access
        // can be the base of array indexing: o.arr[i] is array indexing with field access base
        // Pattern: <postfiks_izraz> TOCKA IDN
        if (LValuePatternMatcher.isFieldAccess(unwrapped)) {
            // Use specialized field address generator (handles null arraySizeExtractor gracefully)
            StructFieldAddressGenerator fieldGen = new StructFieldAddressGenerator(context, expressionGenerator, this, arraySizeExtractor);
            fieldGen.generateFieldAddress(unwrapped, targetRegister);
            return;
        }
        
        // Check for array indexing SECOND
        // Pattern: <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
        // The base can be a field access (o.arr[i]) or nested field access (o.middle.inner.arr[i])
        if (LValuePatternMatcher.isArrayIndexing(unwrapped)) {
            // Use specialized array element address generator (handles null arraySizeExtractor gracefully)
            ArrayElementAddressGenerator arrayGen = new ArrayElementAddressGenerator(context, expressionGenerator, this, arraySizeExtractor);
            arrayGen.generateArrayElementAddress(unwrapped, targetRegister);
            return;
        }
        
        // Simple variable: <primarni_izraz> IDN or <postfiks_izraz> -> <primarni_izraz> IDN
        // This is the base case of recursion - when we've unwound all field/array accesses
        String variableName = extractVariableName(unwrapped);
        if (variableName != null) {
            // Try to extract type from the original node first (before unwrapping),
            // as unwrapping might lose type information in some cases.
            // If not available, fall back to the unwrapped node.
            NonTerminalNode typeSourceNode = node;
            if (node.attributes() == null || node.attributes().type() == null) {
                typeSourceNode = unwrapped; // Fall back to unwrapped node if original has no type
            }
            // Pass the node with type information so we can extract type from semantic attributes
            // This is especially important for distinguishing struct-by-value parameters from array/pointer parameters
            generateVariableAddress(variableName, targetRegister, typeSourceNode);
            return;
        }
        
        // CRITICAL: Do NOT use fallback with IdentifierExtractor.findIdentifier() here!
        // The fallback would find "o" for o.middle.inner.arr[0] and ignore nested field/array accesses,
        // leading to incorrect code generation (accessing o.arr[0] instead of o.middle.inner.arr[0]).
        //
        // If we've reached this point, it means:
        // 1. The node is not a field access (isFieldAccess returned false)
        // 2. The node is not an array indexing (isArrayIndexing returned false)
        // 3. The node is not a simple variable (extractVariableName returned null)
        //
        // This should never happen for valid C code. If it does, it indicates a bug in:
        // - unwrapExpressionLayers() not properly preserving nested structures
        // - isFieldAccess() or isArrayIndexing() not recognizing nested patterns
        // - extractVariableName() incorrectly identifying nested expressions as simple variables
        //
        // Throw a helpful error with debugging information
        throw new IllegalStateException("Node is not a valid L-value: " + node.symbol() + 
            " (unwrapped to: " + unwrapped.symbol() + "). " +
            "This might indicate a deeply nested expression that wasn't properly recognized. " +
            "Node structure: " + (node.children().size() > 0 ? 
                "children=" + node.children().size() + ", firstChild=" + node.children().get(0).symbol() : "no children") +
            ". Check that the AST structure matches expected patterns for field access or array indexing.");
    }
    
    /**
     * Generates code to compute the address of a simple variable.
     * 
     * <p>For local variables, computes: targetRegister = R5 + offset
     * For global variables, computes: targetRegister = G_LABEL
     * 
     * <p><b>Key:</b> This computes the ADDRESS of the variable, not its value.
     * For struct variables, this is the address of the struct object itself.
     * 
     * <p><b>Array Parameters:</b> In C, array parameters decay to pointers. When accessing
     * an array parameter (e.g., {@code a[3]} where {@code a} is a parameter), we need to:
     * <ol>
     *   <li>Compute the address of the parameter slot (e.g., R5+8)</li>
     *   <li>LOAD the pointer value from that slot (the actual array base address)</li>
     * </ol>
     * 
     * @param variableName the variable name
     * @param targetRegister the register to store the address in
     * @param expressionNode optional expression node for type extraction
     */
    private void generateVariableAddress(String variableName, String targetRegister, NonTerminalNode expressionNode) {
        String address = addressResolver.getVariableAddress(variableName);
        addressLoader.loadAddress(address, targetRegister, variableName, expressionNode);
    }
    
    
    /**
     * Extracts variable name from an expression node.
     * 
     * <p>Returns null if the expression is not a simple variable
     * (e.g., if it's a field access or array indexing).
     * 
     * @param node the expression node
     * @return the variable name, or null if not a simple variable
     */
    private String extractVariableName(NonTerminalNode node) {
        // First unwrap all expression layers to get to the underlying l-value
        NonTerminalNode unwrapped = ExpressionUnwrapper.unwrap(node);
        
        // Check if this is a field access or array indexing - if so, return null
        if (LValuePatternMatcher.isFieldAccess(unwrapped) || LValuePatternMatcher.isArrayIndexing(unwrapped)) {
            return null;
        }
        
        // Use IdentifierExtractor to find the identifier recursively
        // This handles all the nested expression layers
        return hr.fer.ppj.codegen.utils.IdentifierExtractor.findIdentifier(unwrapped);
    }
}
