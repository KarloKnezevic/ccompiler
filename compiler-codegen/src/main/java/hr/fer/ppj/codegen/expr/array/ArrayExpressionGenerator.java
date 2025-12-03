package hr.fer.ppj.codegen.expr.array;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.expr.assignment.AssignmentExpressionGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.util.Objects;

/**
 * Generates FRISC assembly code for array indexing operations.
 * 
 * <p>This class handles the generation of code for:
 * <ul>
 *   <li>Array element access: a[i]</li>
 *   <li>Array element assignment: a[i] = value</li>
 * </ul>
 * 
 * <p>Array indexing involves:
 * <ol>
 *   <li>Computing the element address: base + index * element_size</li>
 *   <li>Loading or storing the element value</li>
 * </ol>
 * 
 * <p>The generator handles both global and local arrays, and supports
 * different element sizes (1 byte for char, 4 bytes for int).
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
     * <p>The result is loaded into register R0.
     * 
     * @param base the base array expression
     * @param indexExpr the index expression
     */
    public void generateArrayIndexing(NonTerminalNode base, NonTerminalNode indexExpr) {
        // Extract base variable name
        String baseVarName = extractVariableName(base);
        if (baseVarName == null) {
            // Complex array base expression - not a simple variable
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
        context.emitter().emitInstruction("SHL", "R0", "%D 2", "R0", "index * 4 (element size)");
        
        // R0 now contains byte offset (index * 4), add to base address
        // Load base address into R1
        loadBaseAddress(baseAddress, "R1");
        
        // Add index offset to base: R1 = R1 + R0
        context.emitter().emitInstruction("ADD", "R1", "R0", "R1", "compute element address");
        
        // Load element value from computed address
        // Use LOAD for both int and char arrays (treating chars as 4-byte words)
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
        // Navigate through the expression hierarchy to find the identifier
        return findIdentifierInExpression(expr);
    }
    
    /**
     * Recursively searches for an identifier in an expression.
     */
    private String findIdentifierInExpression(NonTerminalNode node) {
        for (var child : node.children()) {
            if (child instanceof hr.fer.ppj.semantics.tree.TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                return terminal.lexeme();
            } else if (child instanceof NonTerminalNode nonTerminal) {
                String result = findIdentifierInExpression(nonTerminal);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
    
    /**
     * Gets the FRISC address for a variable (local or global).
     * 
     * @param variableName the variable name
     * @return the FRISC address expression
     */
    private String getVariableAddress(String variableName) {
        // Check if we're in a function and the variable is local
        if (context.isInFunction() && context.activationRecord().hasVariable(variableName)) {
            return context.activationRecord().getVariableAddress(variableName);
        } else {
            // Global variable
            String label = context.labelGenerator().getGlobalVariableLabel(variableName);
            return "(" + label + ")";
        }
    }
}

