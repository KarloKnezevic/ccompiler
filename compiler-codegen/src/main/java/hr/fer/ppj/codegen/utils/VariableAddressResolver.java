package hr.fer.ppj.codegen.utils;

import hr.fer.ppj.codegen.CodeGenContext;
import java.util.Objects;

/**
 * Utility class for resolving variable addresses in FRISC assembly.
 * 
 * <p>This class provides methods to get the FRISC address expression for variables,
 * handling both local variables (in activation records) and global variables (via labels).
 * It implements the <b>variable address resolution algorithm</b> used throughout code generation.
 * 
 * <p><b>Design Pattern: Strategy for Address Resolution</b>
 * 
 * <p>This class implements a <b>strategy pattern</b> for variable address resolution:
 * <ul>
 *   <li><b>Local Variable Strategy:</b> Uses frame pointer (R5) with negative offsets</li>
 *   <li><b>Global Variable Strategy:</b> Uses global labels (G_<NAME>)</li>
 * </ul>
 * 
 * <p>The strategy is chosen automatically based on whether the variable is found in the
 * current function's activation record.
 * 
 * <p><b>Algorithm: Variable Address Resolution</b>
 * 
 * <p>The address resolution algorithm works as follows:
 * <ol>
 *   <li><b>Check Function Context:</b> Determine if we're in a function (have an activation record)</li>
 *   <li><b>Check Local Scope:</b> If in a function, check if the variable exists in the
 *       activation record (local variable or parameter)</li>
 *   <li><b>Local Variable Resolution:</b> If found locally:
 *       <ul>
 *         <li>Get the variable's offset from the activation record</li>
 *         <li>Format as FRISC address: {@code (R5+offset)} for parameters or {@code (R5-offset)} for locals</li>
 *       </ul>
 *   </li>
 *   <li><b>Global Variable Resolution:</b> If not found locally:
 *       <ul>
 *         <li>Generate global variable label using LabelGenerator</li>
 *         <li>Format as FRISC address: {@code (G_<VARIABLE_NAME>)}</li>
 *       </ul>
 *   </li>
 * </ol>
 * 
 * <p><b>Address Formats:</b>
 * 
 * <p><b>Local Variables:</b>
 * <ul>
 *   <li><b>Parameters:</b> {@code (R5+08)}, {@code (R5+0C)}, etc. (positive offsets)</li>
 *   <li><b>Local Variables:</b> {@code (R5-04)}, {@code (R5-08)}, etc. (negative offsets)</li>
 * </ul>
 * 
 * <p><b>Global Variables:</b>
 * <ul>
 *   <li><b>Format:</b> {@code (G_<VARIABLE_NAME>)} where G_<VARIABLE_NAME> is the label</li>
 *   <li><b>Example:</b> Variable {@code x} → {@code (G_X)}</li>
 * </ul>
 * 
 * <p><b>Why This Algorithm?</b>
 * 
 * <p>The algorithm implements C's <b>scope resolution rules</b>:
 * <ul>
 *   <li><b>Local Scope First:</b> Local variables shadow global variables with the same name</li>
 *   <li><b>Frame Pointer Access:</b> Local variables are accessed via frame pointer (R5)
 *       because they're allocated on the stack</li>
 *   <li><b>Label Access:</b> Global variables are accessed via labels because they're
 *       allocated in the data section</li>
 * </ul>
 * 
 * <p><b>FRISC Memory Model:</b>
 * 
 * <p>FRISC uses a simple memory model:
 * <ul>
 *   <li><b>Stack:</b> Local variables and function parameters (accessed via R5)</li>
 *   <li><b>Data Section:</b> Global variables (accessed via labels)</li>
 *   <li><b>Code Section:</b> Instructions (accessed via labels for functions)</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>
 * int global = 42;           // Global variable
 * 
 * void foo(int param) {      // Parameter (local to function)
 *     int local = 10;        // Local variable
 *     global = param + local; // Accesses: global (G_GLOBAL), param (R5+08), local (R5-04)
 * }
 * </pre>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) for local variables (hash map lookup),
 *       O(1) for global variables (label generation)</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only existing data structures</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class VariableAddressResolver {
    
    private final CodeGenContext context;
    
    /**
     * Creates a new variable address resolver.
     * 
     * @param context the code generation context
     */
    public VariableAddressResolver(CodeGenContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
    }
    
    /**
     * Gets the FRISC address for a variable (local or global).
     * 
     * <p>This method checks if the variable is local (in the current function's
     * activation record) or global, and returns the appropriate address expression.
     * 
     * @param variableName the variable name
     * @return the FRISC address expression
     */
    public String getVariableAddress(String variableName) {
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

