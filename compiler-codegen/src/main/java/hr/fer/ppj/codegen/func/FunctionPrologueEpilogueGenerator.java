package hr.fer.ppj.codegen.func;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.model.ActivationRecord;
import java.util.Objects;

/**
 * Generates FRISC function prologue and epilogue code.
 * 
 * <p>This class is responsible for generating the standard FRISC function
 * prologue (entry code) and epilogue (exit code) sequences that manage
 * the stack frame and calling convention.
 * 
 * <p><b>FRISC Function Prologue:</b>
 * <pre>
 * PUSH R5                ; Save old frame pointer
 * MOVE R7, R5            ; R5 = current SP (new frame pointer)
 * SUB  R7, %D K, R7      ; Allocate K bytes for local variables (stack grows down)
 * </pre>
 * 
 * <p><b>FRISC Function Epilogue:</b>
 * <pre>
 * ADD  R7, %D K, R7      ; Deallocate local variables
 * POP  R5                 ; Restore old frame pointer
 * RET                     ; Return to caller (pops return address and jumps)
 * </pre>
 * 
 * <p><b>FRISC Calling Convention:</b>
 * <ul>
 *   <li>R5 - Frame Pointer (FP), set in prologue</li>
 *   <li>R7 - Stack Pointer (SP), adjusted for local variables</li>
 *   <li>Stack grows downward (R7 decreases for allocation)</li>
 *   <li>Parameters at positive offsets from R5 (R5+8, R5+12, ...)</li>
 *   <li>Local variables at negative offsets from R5 (R5-4, R5-8, ...)</li>
 * </ul>
 * 
 * <p><b>Stack Frame Layout:</b>
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
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FunctionPrologueEpilogueGenerator {
    
    /**
     * Generates the function prologue (save frame pointer, allocate local variables).
     * 
     * <p><b>FRISC Code Sequence:</b>
     * <ol>
     *   <li>Save old frame pointer: {@code PUSH R5}</li>
     *   <li>Set new frame pointer: {@code MOVE R7, R5}</li>
     *   <li>Allocate locals: {@code SUB R7, %D K, R7} (if K > 0)</li>
     * </ol>
     * 
     * <p><b>FRISC Semantics:</b>
     * <ul>
     *   <li>PUSH saves R5 on stack (at current R7), then decrements R7</li>
     *   <li>MOVE sets R5 = R7 (R5 now points to saved old R5)</li>
     *   <li>SUB allocates space by moving R7 downward (decreasing address)</li>
     *   <li>If no locals (K = 0), allocation step is skipped</li>
     * </ul>
     * 
     * @param functionContext the function's code generation context
     * @param activationRecord the function's activation record (contains local variable size)
     */
    public void generatePrologue(CodeGenContext functionContext, ActivationRecord activationRecord) {
        Objects.requireNonNull(functionContext, "functionContext must not be null");
        Objects.requireNonNull(activationRecord, "activationRecord must not be null");
        
        int localSize = activationRecord.getLocalVariablesSize();
        
        // Always save old frame pointer (even if no locals)
        // This is required for proper function call/return semantics
        functionContext.emitter().emitInstruction("PUSH", "R5", null, "save old frame pointer");
        
        // Set R5 = R7 (current stack pointer)
        // R5 now points to the saved old R5 value
        functionContext.emitter().emitInstruction("MOVE", "R7", "R5", "R5 = current SP -> base of frame");
        
        // Allocate space for local variables
        // Stack grows downward, so we subtract from R7
        if (localSize > 0) {
            functionContext.emitter().emitInstruction("SUB", "R7", "%D " + localSize, "R7", 
                                                    "allocate " + (localSize / 4) + " local variables");
        }
    }
    
    /**
     * Generates the function epilogue (deallocate locals, restore frame pointer, return).
     * 
     * <p><b>FRISC Code Sequence:</b>
     * <ol>
     *   <li>Deallocate locals: {@code ADD R7, %D K, R7} (if K > 0)</li>
     *   <li>Restore frame pointer: {@code POP R5}</li>
     *   <li>Return: {@code RET} (pops return address and jumps)</li>
     * </ol>
     * 
     * <p><b>FRISC Semantics:</b>
     * <ul>
     *   <li>ADD moves R7 back up (increasing address) to deallocate locals</li>
     *   <li>POP restores old R5 from stack, then increments R7</li>
     *   <li>RET pops return address from stack and jumps to it</li>
     *   <li>After RET, stack is back to caller's state</li>
     * </ul>
     * 
     * <p><b>Note:</b> This epilogue is shared by all return paths. Return statements
     * jump to the exit label (which precedes this epilogue) to avoid duplicate code.
     * 
     * @param functionContext the function's code generation context
     * @param activationRecord the function's activation record (contains local variable size)
     */
    public void generateEpilogue(CodeGenContext functionContext, ActivationRecord activationRecord) {
        Objects.requireNonNull(functionContext, "functionContext must not be null");
        Objects.requireNonNull(activationRecord, "activationRecord must not be null");
        
        int localSize = activationRecord.getLocalVariablesSize();
        
        // Deallocate local variables
        // Move R7 back up (increasing address) to free the local variable space
        if (localSize > 0) {
            functionContext.emitter().emitInstruction("ADD", "R7", "%D " + localSize, "R7", 
                                                    "deallocate local variables");
        }
        
        // Restore old frame pointer
        // POP restores R5 from stack (where we saved it in prologue), then increments R7
        functionContext.emitter().emitInstruction("POP", "R5", null, "restore old frame pointer");
        
        // Return to caller
        // RET pops return address from stack and jumps to it
        // After RET, stack is back to caller's state (arguments still on stack, caller cleans up)
        functionContext.emitter().emitInstruction("RET", null, null, "return to caller");
    }
}

