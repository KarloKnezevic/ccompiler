package hr.fer.ppj.codegen;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Utility class for generating unique labels in FRISC assembly code.
 * 
 * <p>This class ensures that all generated labels are unique and follow
 * consistent naming conventions. It supports different label types:
 * <ul>
 *   <li>Function labels (F_MAIN, F_FOO, etc.)</li>
 *   <li>Global variable labels (G_X, G_ARRAY, etc.)</li>
 *   <li>Control flow labels (L_IF1, L_ELSE1, L_END1, etc.)</li>
 *   <li>Loop labels (L_LOOP1, L_CONTINUE1, L_BREAK1, etc.)</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LabelGenerator {
    
    /**
     * Counters for different label types to ensure uniqueness.
     */
    private final Map<String, Integer> counters = new HashMap<>();
    
    /**
     * Generates a function label for the given function name.
     * 
     * <p>Function labels follow the pattern {@code F_<NAME>} where NAME
     * is the uppercased function identifier.
     * 
     * @param functionName the function name
     * @return the function label (e.g., "F_MAIN", "F_FOO")
     */
    public String getFunctionLabel(String functionName) {
        Objects.requireNonNull(functionName, "functionName must not be null");
        return "F_" + functionName.toUpperCase();
    }
    
    /**
     * Generates a global variable label for the given variable name.
     * 
     * <p>Global variable labels follow the pattern {@code G_<NAME>} where NAME
     * is the uppercased variable identifier.
     * 
     * @param variableName the variable name
     * @return the global variable label (e.g., "G_X", "G_COUNTER")
     */
    public String getGlobalVariableLabel(String variableName) {
        Objects.requireNonNull(variableName, "variableName must not be null");
        return "G_" + variableName.toUpperCase();
    }
    
    /**
     * Generates a unique control flow label with the given prefix.
     * 
     * <p>Control flow labels are numbered sequentially to ensure uniqueness.
     * Common prefixes include:
     * <ul>
     *   <li>"L_IF" for if statement labels</li>
     *   <li>"L_ELSE" for else clause labels</li>
     *   <li>"L_END" for end-of-block labels</li>
     *   <li>"L_LOOP" for loop start labels</li>
     *   <li>"L_CONTINUE" for continue target labels</li>
     *   <li>"L_BREAK" for break target labels</li>
     * </ul>
     * 
     * @param prefix the label prefix
     * @return a unique label (e.g., "L_IF1", "L_ELSE2", "L_END3")
     */
    public String getUniqueLabel(String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        
        int counter = counters.getOrDefault(prefix, 0) + 1;
        counters.put(prefix, counter);
        
        return prefix + counter;
    }
    
    /**
     * Generates a unique if-statement label set.
     * 
     * @return a label set containing if, else, and end labels
     */
    public IfLabelSet generateIfLabels() {
        String suffix = String.valueOf(counters.getOrDefault("L_IF", 0) + 1);
        counters.put("L_IF", counters.getOrDefault("L_IF", 0) + 1);
        
        return new IfLabelSet(
            "L_IF" + suffix,
            "L_ELSE" + suffix,
            "L_END" + suffix
        );
    }
    
    /**
     * Generates a unique loop label set.
     * 
     * @return a label set containing loop, continue, and break labels
     */
    public LoopLabelSet generateLoopLabels() {
        String suffix = String.valueOf(counters.getOrDefault("L_LOOP", 0) + 1);
        counters.put("L_LOOP", counters.getOrDefault("L_LOOP", 0) + 1);
        
        return new LoopLabelSet(
            "L_LOOP" + suffix,
            "L_CONTINUE" + suffix,
            "L_BREAK" + suffix
        );
    }
    
    /**
     * Generates a unique short-circuit evaluation label set for logical operators.
     * 
     * @return a label set for short-circuit evaluation
     */
    public ShortCircuitLabelSet generateShortCircuitLabels() {
        String suffix = String.valueOf(counters.getOrDefault("L_SC", 0) + 1);
        counters.put("L_SC", counters.getOrDefault("L_SC", 0) + 1);
        
        return new ShortCircuitLabelSet(
            "L_SC_TRUE" + suffix,
            "L_SC_FALSE" + suffix,
            "L_SC_END" + suffix
        );
    }
    
    /**
     * Label set for if-else statements.
     */
    public record IfLabelSet(String ifLabel, String elseLabel, String endLabel) {}
    
    /**
     * Label set for loops (while, for).
     */
    public record LoopLabelSet(String loopLabel, String continueLabel, String breakLabel) {}
    
    /**
     * Label set for short-circuit evaluation of logical operators.
     */
    public record ShortCircuitLabelSet(String trueLabel, String falseLabel, String endLabel) {}
}
