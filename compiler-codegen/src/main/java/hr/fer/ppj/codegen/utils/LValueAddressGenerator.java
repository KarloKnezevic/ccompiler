package hr.fer.ppj.codegen.utils;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.env.VariableAddressResolver;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.structs.StructArraySizeExtractor;
import hr.fer.ppj.codegen.structs.StructFieldAddressGenerator;
import hr.fer.ppj.codegen.structs.StructFieldOffsetCalculator;
import hr.fer.ppj.codegen.structs.StructSizeCalculator;
import hr.fer.ppj.codegen.types.TypeSizeCalculator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.List;
import java.util.Map;
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
        // Note: unwrapExpressionLayers() preserves <postfiks_izraz> structure, so nested
        // field/array accesses are preserved correctly
        NonTerminalNode unwrapped = unwrapExpressionLayers(node);
        
        // Check for field access FIRST (before array indexing) because field access
        // can be the base of array indexing: o.arr[i] is array indexing with field access base
        // Pattern: <postfiks_izraz> TOCKA IDN
        if (isFieldAccess(unwrapped)) {
            // Use specialized field address generator (handles null arraySizeExtractor gracefully)
            StructFieldAddressGenerator fieldGen = new StructFieldAddressGenerator(context, expressionGenerator, this, arraySizeExtractor);
            fieldGen.generateFieldAddress(unwrapped, targetRegister);
            return;
        }
        
        // Check for array indexing SECOND
        // Pattern: <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
        // The base can be a field access (o.arr[i]) or nested field access (o.middle.inner.arr[i])
        if (isArrayIndexing(unwrapped)) {
            // Use specialized array element address generator (handles null arraySizeExtractor gracefully)
            ArrayElementAddressGenerator arrayGen = new ArrayElementAddressGenerator(context, expressionGenerator, this, arraySizeExtractor);
            arrayGen.generateArrayElementAddress(unwrapped, targetRegister);
            return;
        }
        
        // Simple variable: <primarni_izraz> IDN or <postfiks_izraz> -> <primarni_izraz> IDN
        // This is the base case of recursion - when we've unwound all field/array accesses
        String variableName = extractVariableName(unwrapped);
        if (variableName != null) {
            generateVariableAddress(variableName, targetRegister);
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
     * @param variableName the variable name
     * @param targetRegister the register to store the address in
     */
    private void generateVariableAddress(String variableName, String targetRegister) {
        String address = addressResolver.getVariableAddress(variableName);
        loadVariableAddress(address, targetRegister);
    }
    
    
    /**
     * Loads a variable address into a register.
     * 
     * <p>For local variables: computes R5 + offset
     * For global variables: loads global label
     * 
     * <p><b>Key:</b> This computes the ADDRESS, not the value.
     * Never uses LOAD to get the address of a struct variable.
     * 
     * @param address the address expression (e.g., "(R5-08)" or "(G_X)")
     * @param targetRegister the register to load the address into
     */
    private void loadVariableAddress(String address, String targetRegister) {
        if (address.startsWith("(G_")) {
            // Global variable: extract label from "(G_LABEL)" format
            String label = address.substring(1, address.length() - 1); // Remove parentheses
            context.emitter().emitInstruction("MOVE", label, targetRegister, "load global variable address");
        } else if (address.startsWith("(R5")) {
            // Local variable: compute address from frame pointer
            context.emitter().emitInstruction("MOVE", "R5", targetRegister, "load frame pointer");
            String offsetStr = extractOffsetFromAddress(address);
            if (offsetStr != null) {
                // offsetStr is like "-08" (hex) or "+08" (hex)
                try {
                    int offsetValue;
                    if (offsetStr.startsWith("-")) {
                        // Negative offset: parse hex value and negate
                        String hexValue = offsetStr.substring(1); // Remove minus sign
                        offsetValue = -Integer.parseInt(hexValue, 16);
                        context.emitter().emitInstruction("SUB", targetRegister, "%D " + Math.abs(offsetValue), targetRegister,
                            "add variable offset");
                    } else if (offsetStr.startsWith("+")) {
                        // Positive offset: parse hex value
                        String hexValue = offsetStr.substring(1); // Remove plus sign
                        offsetValue = Integer.parseInt(hexValue, 16);
                        context.emitter().emitInstruction("ADD", targetRegister, "%D " + offsetValue, targetRegister,
                            "add variable offset");
                    } else {
                        // No sign: assume positive hex
                        offsetValue = Integer.parseInt(offsetStr, 16);
                        context.emitter().emitInstruction("ADD", targetRegister, "%D " + offsetValue, targetRegister,
                            "add variable offset");
                    }
                } catch (NumberFormatException e) {
                    // If parsing fails, try as decimal
                    if (offsetStr.startsWith("-")) {
                        context.emitter().emitInstruction("SUB", targetRegister, "%D " + offsetStr.substring(1), targetRegister,
                            "add variable offset");
                    } else if (offsetStr.startsWith("+")) {
                        context.emitter().emitInstruction("ADD", targetRegister, "%D " + offsetStr.substring(1), targetRegister,
                            "add variable offset");
                    } else {
                        context.emitter().emitInstruction("ADD", targetRegister, "%D " + offsetStr, targetRegister,
                            "add variable offset");
                    }
                }
            }
        } else {
            // Fallback: assume it's a label
            context.emitter().emitInstruction("MOVE", address, targetRegister, "load variable address");
        }
    }
    
    /**
     * Extracts the offset part from an address expression.
     * 
     * @param address the address expression (e.g., "(R5-04)" or "(R5+08)")
     * @return the offset string (e.g., "-04" or "+08"), or null if not found
     */
    private String extractOffsetFromAddress(String address) {
        int r5Index = address.indexOf("R5");
        if (r5Index == -1) {
            return null;
        }
        
        int offsetStart = r5Index + 2; // After "R5"
        if (offsetStart >= address.length()) {
            return null;
        }
        
        char firstChar = address.charAt(offsetStart);
        if (firstChar != '+' && firstChar != '-') {
            return null;
        }
        
        int offsetEnd = address.indexOf(')', offsetStart);
        if (offsetEnd == -1) {
            return null;
        }
        
        return address.substring(offsetStart, offsetEnd);
    }
    
    /**
     * Checks if a node represents a field access expression.
     * 
     * @param node the node to check
     * @return true if the node is a field access
     */
    private boolean isFieldAccess(NonTerminalNode node) {
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
     * <p>Pattern: <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
     * 
     * <p>This method ONLY checks for the [...] pattern. It does NOT require
     * that the base is a simple variable - it can be a field access (a.arr[i]),
     * nested struct access (o.inner.arr[i]), etc.
     * 
     * @param node the node to check
     * @return true if the node is array indexing
     */
    private boolean isArrayIndexing(NonTerminalNode node) {
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
    
    /**
     * Unwraps all expression layers to get to the underlying l-value.
     * 
     * <p>This recursively unwraps expression nonterminals that don't change the l-value nature:
     * <ul>
     *   <li><izraz_pridruzivanja> -> unwraps to child</li>
     *   <li><log_ili_izraz>, <log_i_izraz>, <bin_ili_izraz>, etc. -> unwraps to child</li>
     *   <li><jednakosni_izraz>, <odnosni_izraz>, <aditivni_izraz>, etc. -> unwraps to child</li>
     *   <li><multiplikativni_izraz>, <cast_izraz> -> unwraps to child</li>
     *   <li><unarni_izraz> -> unwraps to child</li>
     * </ul>
     * 
     * <p>Stops when it reaches <postfiks_izraz> or <primarni_izraz>, which are the actual l-values.
     * 
     * @param node the node to unwrap
     * @return the unwrapped node (should be <postfiks_izraz> or <primarni_izraz>)
     */
    private NonTerminalNode unwrapExpressionLayers(NonTerminalNode node) {
        String symbol = node.symbol();
        List<ParseNode> children = node.children();
        
        // Expression layers that can be unwrapped (single-child nonterminals)
        if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
            // These are expression layers that don't change the l-value nature
            if ("<izraz_pridruzivanja>".equals(symbol) ||
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
                "<unarni_izraz>".equals(symbol)) {
                // Recursively unwrap
                return unwrapExpressionLayers(child);
            }
        }
        
        // If it's already a postfiks_izraz or primarni_izraz, return as-is
        // (these are the actual l-values)
        return node;
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
        NonTerminalNode unwrapped = unwrapExpressionLayers(node);
        
        // Check if this is a field access or array indexing - if so, return null
        if (isFieldAccess(unwrapped) || isArrayIndexing(unwrapped)) {
            return null;
        }
        
        // Use IdentifierExtractor to find the identifier recursively
        // This handles all the nested expression layers
        return hr.fer.ppj.codegen.utils.IdentifierExtractor.findIdentifier(unwrapped);
    }
}
