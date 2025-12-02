package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.emitter.FriscEmitter;
import java.util.Objects;

/**
 * Generates FRISC helper functions for operations not directly supported by the architecture.
 * 
 * <p>FRISC architecture does not have native MUL (multiplication) or DIV (division) instructions.
 * This class generates helper functions F_MUL and F_DIV that implement these operations using
 * basic arithmetic operations (addition, subtraction, comparison).
 * 
 * <p>The helper functions follow the standard FRISC calling convention:
 * <ul>
 *   <li>Arguments are pushed right-to-left on the stack</li>
 *   <li>Return value is placed in register R6</li>
 *   <li>Caller cleans up arguments from the stack</li>
 * </ul>
 * 
 * <p>F_MUL implements multiplication using repeated addition, handling both positive and
 * negative operands correctly. F_DIV implements integer division using repeated subtraction.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class HelperFunctionGenerator {
    
    /**
     * Generates helper functions F_MUL and/or F_DIV if needed.
     * 
     * <p>These functions are generated only if they are actually needed by the program
     * (i.e., if multiplication or division operations are encountered during code generation).
     * 
     * @param context the code generation context
     * @param needsMul whether F_MUL helper function is needed
     * @param needsDiv whether F_DIV helper function is needed
     */
    public void generateHelperFunctions(CodeGenContext context, boolean needsMul, boolean needsDiv) {
        Objects.requireNonNull(context, "context must not be null");
        
        FriscEmitter emitter = context.emitter();
        
        if (!needsMul && !needsDiv) {
            return; // No helper functions needed
        }
        
        emitter.emitComment("Helper functions for multiplication and division");
        emitter.emitNewline();
        
        if (needsMul) {
            generateMultiplicationHelper(context);
        }
        
        if (needsDiv) {
            generateDivisionHelper(context);
        }
    }
    
    /**
     * Generates the F_MUL helper function for multiplication.
     * 
     * <p>Implements multiplication using repeated addition. Handles:
     * <ul>
     *   <li>Zero multiplier (returns 0 immediately)</li>
     *   <li>Negative multiplier (negates both operands to get positive multiplier)</li>
     *   <li>Positive multiplier (repeated addition loop)</li>
     * </ul>
     * 
     * <p>Calling convention:
     * <pre>
     *   push b (right operand)
     *   push a (left operand)
     *   CALL F_MUL
     *   ADD R7, %D 8, R7  ; cleanup arguments
     *   ; result in R6
     * </pre>
     * 
     * <p>Stack layout after prologue:
     * <pre>
     *   (R5+0C) : b (right operand, pushed first)
     *   (R5+08) : a (left operand, pushed last)
     *   (R5+04) : return address
     *   (R5+00) : old R5
     * </pre>
     * 
     * @param context the code generation context
     */
    private void generateMultiplicationHelper(CodeGenContext context) {
        FriscEmitter emitter = context.emitter();
        
        emitter.emitLabel("F_MUL", "Helper function: int mul(int a, int b)");
        
        // Function prologue
        emitter.emitInstruction("PUSH", "R5", null, "save old frame pointer");
        emitter.emitInstruction("MOVE", "R7", "R5", "R5 = current SP -> base of frame");
        emitter.emitInstruction("SUB", "R7", "%D 4", "R7", "allocate local for accumulator");
        
        // Load arguments from stack
        // Arguments are pushed right-to-left: push b first, then push a
        // After prologue: [old_R5, return_addr, b, a]
        // First parameter (a) is at (R5+08), second parameter (b) is at (R5+0C)
        emitter.emitInstruction("LOAD", "R0", "(R5+08)", "a (left operand, pushed last, first parameter)");
        emitter.emitInstruction("LOAD", "R1", "(R5+0C)", "b (right operand, pushed first, second parameter)");
        emitter.emitInstruction("MOVE", "%D 0", "R2", "acc = 0");
        
        // Handle zero multiplier - return 0 immediately
        String mulEndLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R1", "%D 0", null);
        emitter.emitInstruction("JR_EQ", mulEndLabel, "if b == 0, result is 0");
        
        // Handle negative multiplier: if b < 0, negate both a and b
        // This ensures we always work with a positive multiplier in the loop
        String mulPositiveLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R1", "%D 0", null);
        emitter.emitInstruction("JR_SGT", mulPositiveLabel, "if b > 0, proceed with positive multiplication");
        
        // b is negative: negate both a and b to get positive multiplier
        // This preserves the sign of the result: (-a) * (-b) = a * b
        emitter.emitInstruction("MOVE", "%D 0", "R3", "zero for negation");
        emitter.emitInstruction("SUB", "R3", "R0", "R0", "negate a");
        emitter.emitInstruction("SUB", "R3", "R1", "R1", "negate b");
        
        // Multiplication loop: acc += a, b--
        emitter.emitLabel(mulPositiveLabel, "multiplication loop (b > 0)");
        String mulLoopLabel = context.labelGenerator().generateLabel();
        emitter.emitLabel(mulLoopLabel, "multiplication loop");
        emitter.emitInstruction("ADD", "R2", "R0", "R2", "acc += a");
        emitter.emitInstruction("SUB", "R1", "%D 1", "R1", "b--");
        emitter.emitInstruction("CMP", "R1", "%D 0", null);
        emitter.emitInstruction("JR_SGT", mulLoopLabel, "while (b > 0)");
        
        // Function epilogue
        emitter.emitLabel(mulEndLabel, "multiplication done");
        emitter.emitInstruction("MOVE", "R2", "R6", "result");
        emitter.emitInstruction("ADD", "R7", "%D 4", "R7", "deallocate local");
        emitter.emitInstruction("POP", "R5", null, "restore old frame pointer");
        emitter.emitInstruction("RET", null, null, "return to caller");
        emitter.emitNewline();
    }
    
    /**
     * Generates the F_DIV helper function for integer division.
     * 
     * <p>Implements integer division using repeated subtraction. Handles:
     * <ul>
     *   <li>Division by zero (returns 0)</li>
     *   <li>Normal division (repeated subtraction until remainder < divisor)</li>
     * </ul>
     * 
     * <p>Calling convention:
     * <pre>
     *   push b (divisor)
     *   push a (dividend)
     *   CALL F_DIV
     *   ADD R7, %D 8, R7  ; cleanup arguments
     *   ; result in R6
     * </pre>
     * 
     * <p>Stack layout after prologue:
     * <pre>
     *   (R5+0C) : b (divisor, pushed first)
     *   (R5+08) : a (dividend, pushed last)
     *   (R5+04) : return address
     *   (R5+00) : old R5
     * </pre>
     * 
     * @param context the code generation context
     */
    private void generateDivisionHelper(CodeGenContext context) {
        FriscEmitter emitter = context.emitter();
        
        emitter.emitLabel("F_DIV", "Helper function: int div(int a, int b)");
        
        // Function prologue
        emitter.emitInstruction("PUSH", "R5", null, "save old frame pointer");
        emitter.emitInstruction("MOVE", "R7", "R5", "R5 = current SP -> base of frame");
        
        // Load arguments from stack
        // Arguments are pushed right-to-left: push b first, then push a
        // After prologue: [old_R5, return_addr, b, a]
        // First parameter (a) is at (R5+08), second parameter (b) is at (R5+0C)
        emitter.emitInstruction("LOAD", "R0", "(R5+08)", "a (dividend, pushed last, first parameter)");
        emitter.emitInstruction("LOAD", "R1", "(R5+0C)", "b (divisor, pushed first, second parameter)");
        
        // Handle division by zero - return 0
        String divByZeroLabel = context.labelGenerator().generateLabel();
        String divEndLabel = context.labelGenerator().generateLabel();
        emitter.emitInstruction("CMP", "R1", "%D 0", null);
        emitter.emitInstruction("JR_EQ", divByZeroLabel, "division by zero");
        
        // Save dividend and divisor, initialize quotient
        emitter.emitInstruction("MOVE", "R0", "R2", "save dividend");
        emitter.emitInstruction("MOVE", "R1", "R3", "save divisor");
        emitter.emitInstruction("MOVE", "%D 0", "R0", "initialize quotient");
        
        // Division loop: subtract divisor from dividend until dividend < divisor
        String divLoopLabel = context.labelGenerator().generateLabel();
        emitter.emitLabel(divLoopLabel, "division loop");
        emitter.emitInstruction("CMP", "R2", "R3", null);
        emitter.emitInstruction("JR_SLT", divEndLabel, "exit if dividend < divisor");
        emitter.emitInstruction("SUB", "R2", "R3", "R2", "subtract divisor from dividend");
        emitter.emitInstruction("ADD", "R0", "%D 1", "R0", "increment quotient");
        emitter.emitInstruction("JR", divLoopLabel, "continue division");
        
        // Handle division by zero case
        emitter.emitLabel(divByZeroLabel, "division by zero");
        emitter.emitInstruction("MOVE", "%D 0", "R0", "result 0 for division by zero");
        
        // Function epilogue
        emitter.emitLabel(divEndLabel, "end division");
        emitter.emitInstruction("MOVE", "R0", "R6", "result");
        emitter.emitInstruction("POP", "R5", null, "restore old frame pointer");
        emitter.emitInstruction("RET", null, null, "return to caller");
        emitter.emitNewline();
    }
}

