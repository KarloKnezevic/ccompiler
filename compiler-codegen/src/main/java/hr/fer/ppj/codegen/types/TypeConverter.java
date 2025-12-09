package hr.fer.ppj.codegen.types;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.emitter.FriscEmitter;
import java.util.Objects;

/**
 * Generates code for type conversions between int, char, and float.
 * 
 * <p>This utility class encapsulates the common patterns for type conversions
 * in FRISC assembly, including calls to helper functions for float conversions.
 * 
 * <p><b>Supported Conversions:</b>
 * <ul>
 *   <li><b>int → float:</b> Converts integer to Q16.16 fixed-point format</li>
 *   <li><b>float → int:</b> Converts Q16.16 fixed-point to integer (truncates)</li>
 * </ul>
 * 
 * <p><b>FRISC Calling Convention for Conversions:</b>
 * 
 * <p>Type conversions use helper functions that follow the FRISC calling convention:
 * <ul>
 *   <li><b>Argument:</b> Value to convert is pushed onto the stack</li>
 *   <li><b>Call:</b> Helper function is called (F_I2F or F_F2I)</li>
 *   <li><b>Return:</b> Converted value is returned in R6</li>
 *   <li><b>Cleanup:</b> Caller removes argument from stack</li>
 * </ul>
 * 
 * <p><b>Helper Functions:</b>
 * <ul>
 *   <li><b>F_I2F:</b> Converts integer to float (Q16.16 format)</li>
 *   <li><b>F_F2I:</b> Converts float (Q16.16 format) to integer (truncates)</li>
 * </ul>
 * 
 * <p><b>Register Usage:</b>
 * <ul>
 *   <li><b>R0:</b> Input value (before conversion), output value (after conversion)</li>
 *   <li><b>R6:</b> Return value from helper function</li>
 *   <li><b>R7:</b> Stack pointer (used for PUSH and cleanup)</li>
 * </ul>
 * 
 * <p><b>Struct Type Support:</b>
 * 
 * <p>This class does not handle struct type conversions, as structs are not convertible
 * to other types in C. Struct assignments are handled separately by
 * {@link hr.fer.ppj.codegen.expr.assignment.StructAssignmentGenerator}.
 * 
 * <p><b>FRISC Code Pattern (int → float):</b>
 * <pre>
 * ; Convert integer in R0 to float
 * PUSH R0                      ; push integer value
 * CALL F_I2F                   ; convert int to float
 * ADD R7, %D 4, R7             ; cleanup argument
 * MOVE R6, R0                  ; move float result to R0
 * </pre>
 * 
 * <p><b>FRISC Code Pattern (float → int):</b>
 * <pre>
 * ; Convert float in R0 to integer
 * PUSH R0                      ; push float value
 * CALL F_F2I                   ; convert float to int
 * ADD R7, %D 4, R7             ; cleanup argument
 * MOVE R6, R0                  ; move integer result to R0
 * </pre>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TypeConverter {
    
    private final FriscEmitter emitter;
    
    /**
     * Creates a new type converter.
     * 
     * @param context the code generation context
     * @throws NullPointerException if context is null
     */
    public TypeConverter(CodeGenContext context) {
        Objects.requireNonNull(context, "context must not be null");
        this.emitter = context.emitter();
    }
    
    /**
     * Converts an integer value in R0 to float (Q16.16) format.
     * 
     * <p>This method generates code to convert an integer value to the Q16.16
     * fixed-point format used for floats in FRISC. The conversion is performed
     * by calling the F_I2F helper function.
     * 
     * <p><b>Algorithm:</b>
     * <ol>
     *   <li>Push the integer value onto the stack (argument for F_I2F)</li>
     *   <li>Call F_I2F helper function</li>
     *   <li>Clean up the stack (remove argument)</li>
     *   <li>Move the result from R6 to R0</li>
     * </ol>
     * 
     * <p><b>Preconditions:</b>
     * <ul>
     *   <li>R0 contains the integer value to convert</li>
     * </ul>
     * 
     * <p><b>Postconditions:</b>
     * <ul>
     *   <li>R0 contains the float value (Q16.16 format)</li>
     *   <li>Stack is unchanged (argument removed)</li>
     * </ul>
     * 
     * <p><b>Side Effects:</b>
     * <ul>
     *   <li>Marks F_I2F as needed (for runtime library inclusion)</li>
     *   <li>Modifies R0, R6, R7</li>
     * </ul>
     */
    public void convertIntToFloat() {
        // Mark that F_I2F helper function is needed (for runtime library inclusion)
        emitter.markIntToFloatNeeded();
        
        // Push integer value onto stack (argument for F_I2F)
        emitter.emitInstruction("PUSH", "R0", null, "push integer value");
        
        // Call helper function to convert int to float
        // F_I2F takes integer argument from stack, returns float in R6
        emitter.emitInstruction("CALL", "F_I2F", null, "convert int to float");
        
        // Clean up stack: remove argument (4 bytes)
        // FRISC calling convention: caller cleans up arguments
        emitter.emitInstruction("ADD", "R7", "%D 4", "R7", "cleanup argument");
        
        // Move result from R6 (return register) to R0 (expression result register)
        emitter.emitInstruction("MOVE", "R6", "R0", "move float result to R0");
    }
    
    /**
     * Converts a float value in R0 to integer format (truncates).
     * 
     * <p>This method generates code to convert a Q16.16 fixed-point float value
     * to an integer. The conversion truncates (discards fractional part) by calling
     * the F_F2I helper function.
     * 
     * <p><b>Algorithm:</b>
     * <ol>
     *   <li>Push the float value onto the stack (argument for F_F2I)</li>
     *   <li>Call F_F2I helper function</li>
     *   <li>Clean up the stack (remove argument)</li>
     *   <li>Move the result from R6 to R0</li>
     * </ol>
     * 
     * <p><b>Preconditions:</b>
     * <ul>
     *   <li>R0 contains the float value (Q16.16 format) to convert</li>
     * </ul>
     * 
     * <p><b>Postconditions:</b>
     * <ul>
     *   <li>R0 contains the integer value (truncated)</li>
     *   <li>Stack is unchanged (argument removed)</li>
     * </ul>
     * 
     * <p><b>Side Effects:</b>
     * <ul>
     *   <li>Marks F_F2I as needed (for runtime library inclusion)</li>
     *   <li>Modifies R0, R6, R7</li>
     * </ul>
     * 
     * <p><b>Note:</b> The conversion truncates (discards fractional part), which matches
     * C's behavior for float-to-int conversions.
     */
    public void convertFloatToInt() {
        // Mark that F_F2I helper function is needed (for runtime library inclusion)
        emitter.markFloatToIntNeeded();
        
        // Push float value onto stack (argument for F_F2I)
        emitter.emitInstruction("PUSH", "R0", null, "push float value");
        
        // Call helper function to convert float to int
        // F_F2I takes float argument from stack, returns integer in R6
        emitter.emitInstruction("CALL", "F_F2I", null, "convert float to int");
        
        // Clean up stack: remove argument (4 bytes)
        // FRISC calling convention: caller cleans up arguments
        emitter.emitInstruction("ADD", "R7", "%D 4", "R7", "cleanup argument");
        
        // Move result from R6 (return register) to R0 (expression result register)
        emitter.emitInstruction("MOVE", "R6", "R0", "move integer result to R0");
    }
}
