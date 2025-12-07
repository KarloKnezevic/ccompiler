package hr.fer.ppj.codegen.expr.array;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.expr.assignment.AssignmentExpressionGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.util.Objects;

/**
 * Generates FRISC assembly code for array indexing operations.
 * 
 * <p>This class handles the generation of code for array element access and assignment,
 * implementing the <b>array indexing code generation algorithm</b> that translates C
 * array operations into FRISC assembly with proper address calculation.
 * 
 * <p><b>Grammar Rule:</b> Handles array indexing from {@code <postfiks_izraz>}:
 * <pre>
 * &lt;postfiks_izraz&gt; ::= &lt;postfiks_izraz&gt; L_UGL_ZAGRADA &lt;izraz&gt; D_UGL_ZAGRADA
 * </pre>
 * 
 * <p><b>Algorithm: Array Indexing Code Generation</b>
 * 
 * <p>The algorithm works as follows:
 * <ol>
 *   <li><b>Base Address Resolution:</b> Resolve the base address of the array:
 *       <ul>
 *         <li>Global arrays: Use global label (e.g., {@code G_A})</li>
 *         <li>Local arrays: Use frame pointer with offset (e.g., {@code R5 - 20})</li>
 *         <li>Array parameters: Load pointer from parameter slot (array decay to pointer)</li>
 *       </ul>
 *   </li>
 *   <li><b>Index Evaluation:</b> Evaluate the index expression (result in R0)</li>
 *   <li><b>Offset Calculation:</b> Multiply index by element size (4 bytes) using left shift:
 *       {@code SHL R0, %D 2, R0} (shift left by 2 = multiply by 4)</li>
 *   <li><b>Address Computation:</b> Add offset to base address: {@code ADD R1, R0, R1}</li>
 *   <li><b>Memory Access:</b> Load or store element value using computed address</li>
 * </ol>
 * 
 * <p><b>Array Element Size:</b>
 * 
 * <p>For this project, both {@code int} and {@code char} arrays use 4-byte elements:
 * <ul>
 *   <li>This simplifies the implementation (no need for different element sizes)</li>
 *   <li>Chars are stored as 32-bit words (not bytes)</li>
 *   <li>Uses {@code LOAD} and {@code STORE} instructions (not {@code LOADB} and {@code STOREB})</li>
 * </ul>
 * 
 * <p><b>Address Calculation Formula:</b>
 * <pre>
 * element_address = base_address + (index × element_size)
 *                 = base_address + (index × 4)
 * </pre>
 * 
 * <p><b>Index Multiplication Optimization:</b>
 * 
 * <p>Multiplying by 4 is optimized using left shift (more efficient than calling F_MUL):
 * <pre>
 * index × 4 = index << 2
 * </pre>
 * 
 * <p>This is implemented as: {@code SHL R0, %D 2, R0}
 * 
 * <p><b>Array Parameter Handling:</b>
 * 
 * <p>When an array is passed as a parameter, C's array decay to pointer semantics apply:
 * <ul>
 *   <li>The parameter slot contains a pointer to the array (not the array itself)</li>
 *   <li>We must first load the pointer value: {@code LOAD R1, (R5+offset)}</li>
 *   <li>Then index from that pointer: {@code ADD R1, R0, R1}</li>
 * </ul>
 * 
 * <p><b>FRISC Code Pattern (Array Access):</b>
 * <pre>
 * ; Array access: a[i]
 * 
 * ; Evaluate index
 * ... (evaluate i, result in R0) ...
 * 
 * ; Multiply index by element size (4 bytes)
 * SHL R0, %D 2, R0              ; index * 4
 * 
 * ; Load base address
 * MOVE G_A, R1                   ; global array
 * ; OR
 * MOVE R5, R1                    ; local array
 * ADD R1, -20, R1                ; add base offset
 * ; OR
 * LOAD R1, (R5+08)               ; array parameter (load pointer)
 * 
 * ; Compute element address
 * ADD R1, R0, R1                 ; R1 = base + (index * 4)
 * 
 * ; Load element value
 * LOAD R0, (R1)                  ; load a[i]
 * </pre>
 * 
 * <p><b>FRISC Code Pattern (Array Assignment):</b>
 * <pre>
 * ; Array assignment: a[i] = value
 * 
 * ; Save value (already in sourceRegister, typically R0)
 * MOVE R0, R2                    ; save value
 * 
 * ; Evaluate index
 * ... (evaluate i, result in R0) ...
 * SHL R0, %D 2, R0               ; index * 4
 * 
 * ; Compute element address (same as access)
 * MOVE G_A, R1                   ; base address
 * ADD R1, R0, R1                 ; element address
 * 
 * ; Store value
 * STORE R2, (R1)                 ; store to a[i]
 * </pre>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) for code generation (constant number of instructions)</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only registers</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ArrayExpressionGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator expressionGenerator;
    private final AssignmentExpressionGenerator assignmentGenerator;
    
    /**
     * Creates a new array expression generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the main expression generator for recursive calls
     * @param assignmentGenerator the assignment generator for array assignments
     */
    public ArrayExpressionGenerator(CodeGenContext context, ExpressionCodeGenerator expressionGenerator,
                                     AssignmentExpressionGenerator assignmentGenerator) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
        this.assignmentGenerator = Objects.requireNonNull(assignmentGenerator, "assignmentGenerator must not be null");
    }
    
    /**
     * Generates code for array element access: a[i].
     * 
     * <p><b>Grammar Rule:</b> Implements {@code <postfiks_izraz> ::= <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA}
     * 
     * <p><b>FRISC Code Sequence:</b>
     * <pre>
     * ; Evaluate index expression (result in R0)
     * ... (index evaluation) ...
     * 
     * ; Multiply index by element size (4 bytes)
     * SHL R0, %D 2, R0          ; index * 4 (shift left by 2 = multiply by 4)
     * 
     * ; Load base address into R1
     * MOVE G_A, R1              ; for global arrays
     * ; OR
     * MOVE R5, R1               ; for local arrays
     * ADD R1, -20, R1           ; add base offset (e.g., -20 for local array)
     * ; OR
     * LOAD R1, (R5+8)           ; for array parameters (load pointer)
     * 
     * ; Compute element address: base + offset
     * ADD R1, R0, R1            ; R1 = base + (index * 4)
     * 
     * ; Load element value
     * LOAD R0, (R1)             ; load array element into R0
     * </pre>
     * 
     * <p><b>FRISC Semantics:</b>
     * <ul>
     *   <li>Element size is always 4 bytes (for both int and char arrays)</li>
     *   <li>Index multiplication uses SHL (shift left) for efficiency</li>
     *   <li>Array parameters require loading the pointer first (array decay to pointer)</li>
     * </ul>
     * 
     * <p>The result is loaded into register R0.
     * 
     * @param base the base array expression ({@code <postfiks_izraz>})
     * @param indexExpr the index expression ({@code <izraz>})
     */
    public void generateArrayIndexing(NonTerminalNode base, NonTerminalNode indexExpr) {
        // Extract base variable name
        String baseVarName = extractVariableName(base);
        if (baseVarName == null) {
            // Complex array base expression - not a simple variable
            // Delegate to expression generator (e.g., for nested array access)
            expressionGenerator.generateExpression(base);
            return;
        }
        
        // Get base address (local or global)
        String baseAddress = getVariableAddress(baseVarName);
        
        // Generate code for index expression (result in R0)
        expressionGenerator.generateExpression(indexExpr);
        
        // Calculate element address: base + index * element_size
        // R0 contains index, multiply by element size (4 bytes)
        // Use SHL for multiplication by 4 (shift left by 2 bits = multiply by 4)
        // This is more efficient than using F_MUL helper function
        context.emitter().emitInstruction("SHL", "R0", "%D 2", "R0", "index * 4 (element size)");
        
        // R0 now contains byte offset (index * 4), add to base address
        // Load base address into R1
        // For array parameters, this will LOAD the pointer value
        loadBaseAddress(baseAddress, "R1");
        
        // Add index offset to base: R1 = R1 + R0
        // R1 now contains the address of a[i]
        context.emitter().emitInstruction("ADD", "R1", "R0", "R1", "compute element address");
        
        // Load element value from computed address
        // Use LOAD for both int and char arrays (treating chars as 4-byte words)
        // Note: We use LOAD/STORE, not LOADB/STOREB, because element size is 4 bytes
        context.emitter().emitInstruction("LOAD", "R0", "(R1)", "load array element");
    }
    
    /**
     * Generates code for array element assignment: a[i] = value.
     * 
     * <p>The value to assign should be in the specified source register.
     * 
     * @param lvalue the array indexing expression
     * @param sourceRegister the register containing the value to assign
     */
    public void generateArrayAssignment(NonTerminalNode lvalue, String sourceRegister) {
        // Find the array indexing node (might be nested)
        var info = assignmentGenerator.extractArrayIndexingInfo(lvalue);
        if (info == null) {
            // Not array indexing - fall back to simple assignment
            assignmentGenerator.generateAssignment(lvalue, sourceRegister);
            return;
        }
        
        NonTerminalNode base = info.base();
        NonTerminalNode indexExpr = info.indexExpr();
        
        // Extract base variable name
        String baseVarName = extractVariableName(base);
        if (baseVarName == null) {
            throw new IllegalStateException("Complex array base expression not supported");
        }
        
        // Get base address
        String baseAddress = getVariableAddress(baseVarName);
        
        // Save source value (it's in sourceRegister, which is typically R0)
        context.emitter().emitInstruction("MOVE", sourceRegister, "R2", "save value to assign");
        
        // Generate code for index expression (result in R0)
        expressionGenerator.generateExpression(indexExpr);
        
        // Calculate element address: base + index * element_size
        // R0 contains index, multiply by element size (4 bytes)
        // Use SHL for multiplication by 4 (shift left by 2 bits = multiply by 4)
        context.emitter().emitInstruction("SHL", "R0", "%D 2", "R0", "index * 4 (element size)");
        
        // Compute base address in R1
        loadBaseAddress(baseAddress, "R1");
        
        // Add index offset: R1 = R1 + R0
        context.emitter().emitInstruction("ADD", "R1", "R0", "R1", "compute element address");
        
        // Store value to computed address (R2 contains the value)
        // Use STORE for both int and char arrays (treating chars as 4-byte words)
        context.emitter().emitInstruction("STORE", "R2", "(R1)", "store array element");
    }
    
    /**
     * Loads the base address of an array into a register.
     * 
     * @param baseAddress the base address expression
     * @param targetRegister the register to load the address into
     */
    private void loadBaseAddress(String baseAddress, String targetRegister) {
        if (baseAddress.startsWith("(G_")) {
            // Global variable: extract label from "(G_LABEL)" format
            String label = baseAddress.substring(1, baseAddress.length() - 1); // Remove parentheses
            context.emitter().emitInstruction("MOVE", label, targetRegister, "load base address");
        } else {
            // Check if this is a parameter (positive offset) or local variable (negative offset)
            String offsetStr = extractOffsetFromAddress(baseAddress);
            if (offsetStr != null && offsetStr.startsWith("+")) {
                // Parameter: baseAddress is like "(R5+08)" - this is a pointer parameter
                // We need to LOAD the pointer value first, then add the index offset
                context.emitter().emitInstruction("LOAD", targetRegister, baseAddress, "load array pointer from parameter");
            } else if (offsetStr != null && offsetStr.startsWith("-")) {
                // Local array variable: baseAddress is like "(R5-04)"
                // Compute: R1 = R5 + offset, then add R0
                context.emitter().emitInstruction("MOVE", "R5", targetRegister, "load frame pointer");
                // Use hex offset directly (address offsets are always hex in FRISC)
                String formattedOffset = offsetStr; // Keep hex format (e.g., "-04")
                context.emitter().emitInstruction("ADD", targetRegister, formattedOffset, targetRegister, "add base offset");
            } else {
                // Fallback: might be a parameter - try loading as pointer
                if (baseAddress.startsWith("(R5+")) {
                    context.emitter().emitInstruction("LOAD", targetRegister, baseAddress, "load array pointer from parameter");
                } else {
                    context.emitter().emitComment("Array indexing: " + baseAddress + " + offset");
                    generateArrayAddressCalculation(baseAddress, targetRegister);
                }
            }
        }
    }
    
    /**
     * Generates code to calculate array base address into a register.
     * 
     * @param baseAddress the base address expression
     * @param targetRegister the register to store the address in
     */
    private void generateArrayAddressCalculation(String baseAddress, String targetRegister) {
        if (baseAddress.startsWith("(G_")) {
            // Global variable: extract label from "(G_LABEL)" format
            String label = baseAddress.substring(1, baseAddress.length() - 1);
            context.emitter().emitInstruction("MOVE", label, targetRegister, "load global array address");
        } else if (baseAddress.startsWith("(R5")) {
            // Local variable: extract offset and compute address
            String offset = extractOffsetFromAddress(baseAddress);
            if (offset != null) {
                context.emitter().emitInstruction("MOVE", "R5", targetRegister, "load frame pointer");
                String formattedOffset = offset; // Keep hex format
                context.emitter().emitInstruction("ADD", targetRegister, formattedOffset, targetRegister, "add base offset");
            }
        }
    }
    
    /**
     * Extracts the offset from an address expression like "(R5-04)" or "(R5+08)".
     * 
     * @param address the address expression
     * @return the offset as a string (e.g., "-04", "+08"), or null if not parseable
     */
    private String extractOffsetFromAddress(String address) {
        // Address format: "(R5-04)" or "(R5+08)" or "(R5-0C)"
        if (address.startsWith("(R5") && address.endsWith(")")) {
            // Extract offset starting from position 3 (after "R5") to before the closing ")"
            String offset = address.substring(3, address.length() - 1);
            return offset; // Returns "-04", "+08", "-0C" etc. (hex format)
        }
        return null;
    }
    
    /**
     * Extracts a variable name from an expression.
     * 
     * @param expr the expression
     * @return the variable name, or null if not a simple variable
     */
    private String extractVariableName(NonTerminalNode expr) {
        return hr.fer.ppj.codegen.utils.IdentifierExtractor.findIdentifier(expr);
    }
    
    /**
     * Gets the FRISC address for a variable (local or global).
     * 
     * @param variableName the variable name
     * @return the FRISC address expression
     */
    private String getVariableAddress(String variableName) {
        var resolver = new hr.fer.ppj.codegen.utils.VariableAddressResolver(context);
        return resolver.getVariableAddress(variableName);
    }
}

