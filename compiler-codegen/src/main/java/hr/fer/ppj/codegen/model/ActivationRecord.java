package hr.fer.ppj.codegen.model;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the activation record (stack frame) for a function.
 * 
 * <p>This class manages the layout of local variables and parameters on the stack
 * for a single function. It tracks the offsets of variables relative to the
 * frame pointer (R5) and provides methods for allocating space for new variables.
 * 
 * <p>Stack layout using frame pointer R5:
 * <pre>
 * Higher addresses
 * +----------------+
 * | Parameter n    | R5 + (8 + (n-1)*4)
 * | ...            |
 * | Parameter 1    | R5 + 8
 * | Return address | R5 + 4
 * | Old R5         | R5 + 0  (saved by current function)
 * +----------------+ <- R5 (frame pointer, fixed)
 * | Local var 1    | R5 - 4
 * | Local var 2    | R5 - 8
 * | ...            |
 * | Local var n    | R5 - (n*4)
 * +----------------+ <- R7 (current stack pointer, after local allocation)
 * Lower addresses
 * </pre>
 * 
 * <p>Parameters are accessed with positive offsets from R5 (R5+8, R5+12, etc.),
 * while local variables are accessed with negative offsets (R5-4, R5-8, etc.).
 * All offsets are formatted as hexadecimal for FRISC assembly.
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

