package hr.fer.ppj.codegen.func;

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
 * Higher addresses
 * +----------------+
 * | Parameter n    | R7 + (4 + n*4)
 * | ...            |
 * | Parameter 1    | R7 + 8
 * | Return address | R7 + 4  (pushed by CALL)
 * +----------------+ <- R7 (on function entry)
 * | Local var 1    | R7 - 4
 * | Local var 2    | R7 - 8
 * | ...            |
 * | Local var n    | R7 - (n*4)
 * +----------------+ <- R7 (after locals allocated)
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
     * Current offset for the next local variable (negative, growing downward).
     */
    private int currentLocalOffset = 0;
    
    /**
     * Current offset for the next parameter (positive, growing upward).
     */
    private int currentParameterOffset = 8; // Start after return address (R7+4) + first param (R7+8)
    
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
        variableOffsets.put(name, offset);
        currentParameterOffset += 4; // Each parameter takes 4 bytes
        
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
        
        currentLocalOffset -= 4; // Allocate 4 bytes for the variable
        localVariablesSize += 4;
        
        variableOffsets.put(name, currentLocalOffset);
        return currentLocalOffset;
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
        
        if (offset > 0) {
            return "(R7+" + offset + ")";
        } else if (offset < 0) {
            return "(R7" + offset + ")"; // offset is already negative
        } else {
            return "(R7)";
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
}
