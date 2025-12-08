package hr.fer.ppj.codegen.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the activation record (stack frame) for a function.
 * 
 * <p>This class manages the layout of local variables and parameters on the stack
 * for a single function. It implements the <b>stack frame allocation algorithm</b>
 * used by the FRISC calling convention, tracking the offsets of variables relative
 * to the frame pointer (R5) and providing methods for allocating space for new variables.
 * 
 * <p><b>Algorithm: Stack Frame Layout</b>
 * 
 * <p>This class implements a <b>linear stack frame layout</b> algorithm:
 * <ol>
 *   <li><b>Parameter Allocation:</b> Parameters are allocated first, starting at R5+8.
 *       Each parameter takes 4 bytes (32 bits), regardless of type (int, char, pointer).
 *       Parameters are allocated in declaration order (left-to-right).</li>
 *   <li><b>Local Variable Allocation:</b> Local variables are allocated after parameters,
 *       starting at R5-4 and growing downward (negative offsets). Each variable's size
 *       is rounded up to a multiple of 4 bytes for stack alignment.</li>
 *   <li><b>Array Allocation:</b> Arrays are allocated as contiguous blocks. For example,
 *       {@code int a[5]} uses 20 bytes (5 * 4) at offsets R5-20 to R5-1.</li>
 * </ol>
 * 
 * <p><b>Stack Frame Layout (FRISC Calling Convention):</b>
 * 
 * <p>The stack frame layout using frame pointer R5 is as follows:
 * <pre>
 * Higher addresses (lower memory addresses in FRISC)
 * +----------------+
 * | Parameter n    | R5 + (8 + (n-1)*4)  ; Last parameter
 * | ...            |
 * | Parameter 2    | R5 + 12              ; Second parameter
 * | Parameter 1    | R5 + 8               ; First parameter
 * | Return address | R5 + 4                ; Saved by CALL instruction
 * | Old R5         | R5 + 0                ; Saved by function prologue
 * +----------------+ <- R5 (frame pointer, fixed during function execution)
 * | Local var 1    | R5 - 4                ; First local variable
 * | Local var 2    | R5 - 8                ; Second local variable
 * | ...            |
 * | Local var n    | R5 - (n*4)            ; Last local variable
 * +----------------+ <- R7 (current stack pointer, after local allocation)
 * Lower addresses (higher memory addresses in FRISC)
 * </pre>
 * 
 * <p><b>Key Invariants:</b>
 * <ul>
 *   <li><b>Frame Pointer (R5):</b> Points to the saved old R5 value. This is fixed
 *       during function execution and provides a stable reference point for accessing
 *       parameters and local variables.</li>
 *   <li><b>Parameter Offsets:</b> Always positive (R5+8, R5+12, etc.). The first parameter
 *       is at R5+8 because R5+0 contains the saved old R5 and R5+4 contains the return address.</li>
 *   <li><b>Local Variable Offsets:</b> Always negative (R5-4, R5-8, etc.). Local variables
 *       are allocated downward from R5 to allow for variable-sized arrays and dynamic allocation.</li>
 *   <li><b>Stack Alignment:</b> All allocations are rounded up to multiples of 4 bytes to
 *       maintain 4-byte alignment, which is required for efficient memory access on FRISC.</li>
 * </ul>
 * 
 * <p><b>FRISC Calling Convention Details:</b>
 * 
 * <ul>
 *   <li><b>Parameter Passing:</b> Arguments are pushed on the stack by the caller in
 *       right-to-left order (C convention). The callee accesses them via positive offsets
 *       from R5.</li>
 *   <li><b>Return Value:</b> Returned in register R6, not on the stack.</li>
 *   <li><b>Stack Pointer (R7):</b> Points to the top of the stack (lowest allocated address).
 *       It is decremented when allocating local variables and incremented when deallocating.</li>
 *   <li><b>Frame Pointer (R5):</b> Set in the function prologue by saving the old R5 and
 *       setting R5 = R7. Restored in the epilogue before returning.</li>
 * </ul>
 * 
 * <p><b>Complexity:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) for addParameter() and addLocalVariable() operations</li>
 *   <li><b>Space Complexity:</b> O(n) where n is the number of variables (parameters + locals)</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b>
 * <pre>
 * ActivationRecord ar = new ActivationRecord();
 * 
 * // Add parameters (in declaration order)
 * ar.addParameter("x");      // R5+8
 * ar.addParameter("y");      // R5+12
 * 
 * // Add local variables
 * ar.addLocalVariable("a");  // R5-4
 * ar.addLocalVariable("b");  // R5-8
 * ar.addLocalVariable("arr", 20);  // Array: R5-20 to R5-1 (20 bytes)
 * 
 * // Get variable addresses for code generation
 * String addr = ar.getVariableAddress("x");  // "(R5+08)"
 * String addr2 = ar.getVariableAddress("a"); // "(R5-04)"
 * </pre>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ActivationRecord {
    
    /**
     * Map from variable name to its stack offset relative to R5 (frame pointer).
     * Positive offsets are for parameters (R5+8, R5+12, etc.),
     * negative offsets are for local variables (R5-4, R5-8, etc.).
     */
    private final Map<String, Integer> variableOffsets = new HashMap<>();
    
    /**
     * Map from variable name to its size in bytes.
     * Used to determine if a variable is an array (size > 4 bytes).
     */
    private final Map<String, Integer> variableSizes = new HashMap<>();
    
    /**
     * Current offset for the next parameter (positive, relative to R5).
     * First parameter starts at R5+8 (after old R5 at +0 and return address at +4).
     */
    private int currentParameterOffset = 8;
    
    /**
     * Total size of local variables in bytes.
     */
    private int localVariablesSize = 0;
    
    /**
     * Adds a parameter to the activation record.
     * 
     * <p>Parameters are added in declaration order and assigned offsets starting
     * at R5+8. Each parameter takes 4 bytes (32 bits) regardless of type.
     * 
     * @param name the parameter name
     * @return the stack offset for this parameter (positive, relative to R5)
     */
    public int addParameter(String name) {
        Objects.requireNonNull(name, "name must not be null");
        
        int offset = currentParameterOffset;
        currentParameterOffset += 4; // Each parameter takes 4 bytes
        
        // Store parameter with positive offset (R5+8, R5+12, etc.)
        variableOffsets.put(name, offset);
        variableSizes.put(name, 4); // Parameters are always 4 bytes (pointers)
        return offset;
    }
    
    /**
     * Adds a local variable to the activation record.
     * 
     * <p>Local variables are allocated with negative offsets from R5, starting
     * at R5-4. The size is rounded up to a multiple of 4 bytes for alignment.
     * 
     * <p><b>FRISC Stack Layout:</b>
     * <ul>
     *   <li>First local variable: R5-4</li>
     *   <li>Second local variable: R5-8</li>
     *   <li>Arrays: Allocated as contiguous block (e.g., int a[5] uses 20 bytes at R5-20 to R5-1)</li>
     * </ul>
     * 
     * @param name the variable name
     * @return the stack offset for this variable (negative, relative to R5)
     */
    public int addLocalVariable(String name) {
        return addLocalVariable(name, 4); // Default: 4 bytes for int/char/pointer
    }
    
    /**
     * Adds a local variable to the activation record with specified size.
     * 
     * <p><b>FRISC Semantics:</b>
     * <ul>
     *   <li>Size is aligned to 4-byte boundary (rounds up)</li>
     *   <li>Arrays: size = num_elements * 4 (element size is 4 bytes for both int and char)</li>
     *   <li>Stack grows downward: R7 decreases by aligned size</li>
     * </ul>
     * 
     * <p>The size is rounded up to a multiple of 4 bytes for stack alignment.
     * Local variables are allocated downward from R5 (negative offsets).
     * 
     * @param name the variable name
     * @param sizeInBytes the size of the variable in bytes
     * @return the stack offset for this variable (negative, relative to R5)
     */
    public int addLocalVariable(String name, int sizeInBytes) {
        Objects.requireNonNull(name, "name must not be null");
        
        // Check if variable already exists
        if (variableOffsets.containsKey(name)) {
            // If it's a parameter (positive offset), don't add as local - just return existing
            int existingOffset = variableOffsets.get(name);
            if (existingOffset > 0) {
                return existingOffset; // Return parameter offset as-is
            }
            // Variable already exists as local - return existing offset (shadowing not supported)
            return existingOffset;
        }
        
        // Round up to multiple of 4 for alignment
        int alignedSize = ((sizeInBytes + 3) / 4) * 4;
        
        localVariablesSize += alignedSize; // Increase total size
        // First local at R5-4, second at R5-8, etc.
        int offset = -(localVariablesSize); // Negative offset from R5
        
        variableOffsets.put(name, offset);
        variableSizes.put(name, sizeInBytes); // Store actual size (not aligned)
        return offset;
    }
    
    /**
     * Gets the stack offset for a variable.
     * 
     * @param name the variable name
     * @return the stack offset, or null if variable not found
     */
    public Integer getVariableOffset(String name) {
        return variableOffsets.get(name);
    }
    
    /**
     * Checks if a variable is defined in this activation record.
     * 
     * @param name the variable name
     * @return true if the variable is defined
     */
    public boolean hasVariable(String name) {
        return variableOffsets.containsKey(name);
    }
    
    /**
     * Gets the total size of local variables in bytes.
     * 
     * <p>This is used to determine how much stack space to allocate/deallocate
     * in the function prologue and epilogue.
     * 
     * @return the size of local variables
     */
    public int getLocalVariablesSize() {
        return localVariablesSize;
    }
    
    /**
     * Generates the FRISC address expression for a variable using frame pointer R5.
     * 
     * <p>Parameters are accessed with positive offsets (e.g., "(R5+08)"),
     * while local variables are accessed with negative offsets (e.g., "(R5-04)").
     * All offsets are formatted as hexadecimal.
     * 
     * @param name the variable name
     * @return the FRISC address expression (e.g., "(R5+8)" for parameters or "(R5-4)" for locals)
     * @throws IllegalArgumentException if the variable is not found
     */
    public String getVariableAddress(String name) {
        Integer offset = getVariableOffset(name);
        if (offset == null) {
            throw new IllegalArgumentException("Variable not found: " + name);
        }
        
        // Parameters have positive offsets (R5+8, R5+12, etc.)
        // Locals have negative offsets (R5-4, R5-8, etc.)
        if (offset >= 0) {
            // Parameter: use positive offset from R5
            return "(R5+" + formatHexOffset(offset) + ")";
        } else {
            // Local variable: use negative offset from R5
            return "(R5" + formatHexOffset(offset) + ")"; // formatHexOffset handles negative
        }
    }
    
    /**
     * Returns all variable names in this activation record.
     * 
     * @return a copy of all variable names and their offsets
     */
    public Map<String, Integer> getAllVariables() {
        return Map.copyOf(variableOffsets);
    }
    
    /**
     * Gets the size of a variable in bytes.
     * 
     * <p>This can be used to determine if a variable is an array:
     * if size > 4 bytes, it's likely an array.
     * 
     * @param name the variable name
     * @return the size in bytes, or null if variable not found
     */
    public Integer getVariableSize(String name) {
        return variableSizes.get(name);
    }
    
    /**
     * Formats an offset as hexadecimal for FRISC assembly.
     * 
     * <p>Stack offsets (addresses) use hex format: "08", "0C", "-04", etc.
     * This ensures consistency with FRISC assembly conventions where addresses
     * and offsets are represented in hexadecimal.
     * 
     * @param offset the offset value (can be positive or negative)
     * @return formatted hex string (e.g., "08" for +8, "-04" for -4)
     */
    private String formatHexOffset(int offset) {
        if (offset >= 0) {
            // Format as hex without leading zeros (but ensure at least 2 digits for small values)
            String hex = Integer.toHexString(offset).toUpperCase();
            // Pad to 2 digits minimum for readability
            if (hex.length() == 1) {
                return "0" + hex;
            }
            return hex;
        } else {
            // Negative offset: format absolute value as hex with minus sign
            String hex = Integer.toHexString(-offset).toUpperCase();
            if (hex.length() == 1) {
                return "-0" + hex;
            }
            return "-" + hex;
        }
    }
}

