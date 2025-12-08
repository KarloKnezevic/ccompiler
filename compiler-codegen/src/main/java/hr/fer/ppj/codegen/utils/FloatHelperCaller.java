package hr.fer.ppj.codegen.utils;

import hr.fer.ppj.codegen.CodeGenContext;
import java.util.Objects;

/**
 * Utility class for calling float helper functions.
 * 
 * <p>This class encapsulates the common pattern of calling float helper functions
 * (F_FADD, F_FSUB, F_FMUL, F_FDIV, F_FCMP) with proper argument pushing,
 * function call, and stack cleanup. It eliminates duplication across
 * binary expression generators.
 * 
 * <p><b>Calling Convention:</b>
 * <ul>
 *   <li>Arguments are pushed right-to-left on the stack</li>
 *   <li>Function is called via CALL instruction</li>
 *   <li>Caller cleans up arguments (ADD R7, %D 8, R7)</li>
 *   <li>Return value is in R6, moved to R0</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FloatHelperCaller {
    
    private final CodeGenContext context;
    
    /**
     * Creates a new float helper caller.
     * 
     * @param context the code generation context
     */
    public FloatHelperCaller(CodeGenContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
    }
    
    /**
     * Calls a float helper function with two operands.
     * 
     * <p>Assumes R0 contains the left operand and R1 contains the right operand.
     * After the call, R0 contains the result.
     * 
     * @param functionName the helper function name (e.g., "F_FADD", "F_FSUB")
     * @param markNeeded a runnable that marks the helper as needed (e.g., markFloatAddNeeded)
     */
    public void callFloatHelper(String functionName, Runnable markNeeded) {
        Objects.requireNonNull(functionName, "functionName must not be null");
        Objects.requireNonNull(markNeeded, "markNeeded must not be null");
        
        markNeeded.run();
        context.emitter().emitInstruction("PUSH", "R1", null, "push second arg");
        context.emitter().emitInstruction("PUSH", "R0", null, "push first arg");
        context.emitter().emitInstruction("CALL", functionName, null, "call " + functionName);
        context.emitter().emitInstruction("ADD", "R7", "%D 8", "R7", "cleanup arguments");
        context.emitter().emitInstruction("MOVE", "R6", "R0", "move result to R0");
    }
}

