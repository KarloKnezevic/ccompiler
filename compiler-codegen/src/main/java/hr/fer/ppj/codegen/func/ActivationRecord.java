package hr.fer.ppj.codegen.func;

import hr.fer.ppj.codegen.FriscEmitter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the activation record (stack frame) for a function.
 * 
 * <p>This class manages the layout of local variables and parameters on the stack
 * for a single function. It tracks the offsets of variables relative to the
 * stack pointer (R7) and provides methods for allocating space for new variables.
 * 
 * <p>Stack layout (growing downward):
 * <pre>
 * Higher addresses (after local allocation)
 * +----------------+
 * | Parameter n    | R7 + (localSize + 4 + n*4)
 * | ...            |
 * | Parameter 1    | R7 + (localSize + 8)
 * | Return address | R7 + (localSize + 4)
 * +----------------+ <- R7 (before SUB R7, localSize, R7)
 * | Local var 1    | R7 + (localSize - 4)
 * | Local var 2    | R7 + (localSize - 8)
 * | ...            |
 * | Local var n    | R7 + 0
 * +----------------+ <- R7 (after SUB R7, localSize, R7)
 * Lower addresses
 * </pre>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ActivationRecord {
    
    /**
     * Map from variable name to its stack offset relative to R7.
     * Positive offsets are for parameters, negative for local variables.
     */
    private final Map<String, Integer> variableOffsets = new HashMap<>();
    
    /**
     * Current offset for the next local variable (positive, from top of locals).
     */
    private int currentLocalOffset = 0;
    
    /**
     * Current offset for the next parameter (positive, growing upward).
     */
    private int currentParameterOffset = 4; // Start after return address (R7+4)
    
    /**
     * Total size of local variables in bytes.
     */
    private int localVariablesSize = 0;
    
    /**
     * Adds a parameter to the activation record.
     * 
     * @param name the parameter name
     * @return the stack offset for this parameter (positive)
     */
    public int addParameter(String name) {
        Objects.requireNonNull(name, "name must not be null");
        
        int offset = currentParameterOffset;
        currentParameterOffset += 4; // Each parameter takes 4 bytes
        
        // Store parameter with negative offset to distinguish from locals
        variableOffsets.put(name, -offset);
        return offset;
    }
    
    /**
     * Adds a local variable to the activation record.
     * 
     * @param name the variable name
     * @return the stack offset for this variable (negative)
     */
    public int addLocalVariable(String name) {
        Objects.requireNonNull(name, "name must not be null");
        
        // Check if variable already exists (e.g., as parameter)
        if (variableOffsets.containsKey(name)) {
            // If it's a parameter (negative offset), don't add as local - just return existing
            int existingOffset = variableOffsets.get(name);
            if (existingOffset < 0) {
                return existingOffset; // Return parameter offset as-is
            }
            return existingOffset;
        }
        
        localVariablesSize += 4; // Increase total size
        int offset = localVariablesSize - 4; // Offset from bottom of locals
        
        variableOffsets.put(name, offset);
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
     * @return the size of local variables
     */
    public int getLocalVariablesSize() {
        return localVariablesSize;
    }
    
    /**
     * Generates the FRISC address expression for a variable.
     * 
     * @param name the variable name
     * @return the FRISC address expression (e.g., "(R7+8)" or "(R7-4)")
     */
    public String getVariableAddress(String name) {
        Integer offset = getVariableOffset(name);
        if (offset == null) {
            throw new IllegalArgumentException("Variable not found: " + name);
        }
        
        // Check if it's a parameter (stored with negative offset)
        if (offset < 0) {
            // Parameter: adjust for local variable allocation
            int paramOffset = -offset; // Convert back to positive
            int adjustedOffset = paramOffset + localVariablesSize;
            return "(R7+" + formatHexOffset(adjustedOffset) + ")";
        } else {
            // Local variable: positive offset from current R7
            int localOffset = localVariablesSize - offset;
            return "(R7+" + formatHexOffset(localOffset) + ")";
        }
    }
    
    /**
     * Returns all variable names in this activation record.
     * 
     * @return a copy of all variable names
     */
    public Map<String, Integer> getAllVariables() {
        return Map.copyOf(variableOffsets);
    }
    
    /**
     * Formats an offset as hexadecimal for FRISC assembly.
     * 
     * @param offset the offset value
     * @return formatted hex string (e.g., "04", "-08")
     */
    private String formatHexOffset(int offset) {
        if (offset >= 0) {
            return FriscEmitter.formatHex(offset);
        } else {
            return "-" + FriscEmitter.formatHex(-offset);
        }
    }
}
