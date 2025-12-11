package hr.fer.ppj.codegen.func;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.model.ActivationRecord;
import java.util.Objects;

/**
 * Generates FRISC function prologue and epilogue code.
 *
 * <p>This class is responsible for generating the standard FRISC function prologue (entry code) and
 * epilogue (exit code) sequences that manage the stack frame and calling convention. It implements
 * the <b>stack frame management algorithm</b> used by all functions in the generated code.
 *
 * <p><b>Algorithm: Stack Frame Management</b>
 *
 * <p>This class implements the standard stack frame management algorithm used in compiler code
 * generation:
 *
 * <ol>
 *   <li><b>Prologue (Function Entry):</b>
 *       <ul>
 *         <li>Save the caller's frame pointer (R5) on the stack
 *         <li>Set the current frame pointer (R5) to the current stack pointer (R7)
 *         <li>Allocate space for local variables by decrementing R7
 *       </ul>
 *   <li><b>Function Body:</b>
 *       <ul>
 *         <li>Function executes with R5 pointing to the saved old R5
 *         <li>Parameters accessed via positive offsets from R5 (R5+8, R5+12, ...)
 *         <li>Local variables accessed via negative offsets from R5 (R5-4, R5-8, ...)
 *       </ul>
 *   <li><b>Epilogue (Function Exit):</b>
 *       <ul>
 *         <li>Deallocate local variables by incrementing R7
 *         <li>Restore the caller's frame pointer (R5) from the stack
 *         <li>Return to the caller (pops return address and jumps)
 *       </ul>
 * </ol>
 *
 * <p><b>FRISC Function Prologue:</b>
 *
 * <pre>
 * PUSH R5                ; Save old frame pointer on stack, decrement R7
 * MOVE R7, R5            ; R5 = current SP (new frame pointer)
 * SUB  R7, %D K, R7      ; Allocate K bytes for local variables (stack grows down)
 * </pre>
 *
 * <p><b>FRISC Function Epilogue:</b>
 *
 * <pre>
 * ADD  R7, %D K, R7      ; Deallocate local variables (move R7 back up)
 * POP  R5                 ; Restore old frame pointer from stack, increment R7
 * RET                     ; Return to caller (pops return address and jumps)
 * </pre>
 *
 * <p><b>Why This Algorithm?</b>
 *
 * <p>The stack frame management algorithm provides several benefits:
 *
 * <ul>
 *   <li><b>Stable Reference Point:</b> R5 (frame pointer) remains fixed during function execution,
 *       providing a stable base for accessing parameters and local variables
 *   <li><b>Dynamic Stack Growth:</b> R7 (stack pointer) can grow/shrink during function execution
 *       (e.g., for temporary values), while R5 stays fixed
 *   <li><b>Nested Function Calls:</b> Each function call creates its own stack frame, allowing
 *       recursive and nested function calls
 *   <li><b>Automatic Cleanup:</b> The epilogue automatically restores the stack to its pre-call
 *       state, ensuring proper stack management
 * </ul>
 *
 * <p><b>FRISC Calling Convention:</b>
 *
 * <ul>
 *   <li><b>R5 (Frame Pointer):</b> Points to the saved old R5 in the current frame. Fixed during
 *       function execution, provides stable base for variable access.
 *   <li><b>R7 (Stack Pointer):</b> Points to the top of the stack (lowest allocated address).
 *       Adjusted in prologue for local variables, restored in epilogue.
 *   <li><b>Stack Growth:</b> Stack grows downward (R7 decreases for allocation, increases for
 *       deallocation). This is the standard convention for most architectures.
 *   <li><b>Parameters:</b> At positive offsets from R5 (R5+8, R5+12, ...). The first parameter is
 *       at R5+8 because R5+0 contains saved old R5 and R5+4 contains the return address (saved by
 *       CALL instruction).
 *   <li><b>Local Variables:</b> At negative offsets from R5 (R5-4, R5-8, ...). Allocated downward
 *       from R5 to allow for variable-sized arrays and dynamic allocation.
 * </ul>
 *
 * <p><b>Stack Frame Layout:</b>
 *
 * <pre>
 * Higher addresses (lower memory addresses in FRISC)
 * +----------------+
 * | Parameter n    | R5 + (8 + (n-1)*4)  ; Last parameter
 * | ...            |
 * | Parameter 2    | R5 + 12              ; Second parameter
 * | Parameter 1    | R5 + 8               ; First parameter
 * | Return address | R5 + 4                ; Saved by CALL instruction
 * | Old R5         | R5 + 0                ; Saved by current function (prologue)
 * +----------------+ <- R5 (frame pointer, fixed during function execution)
 * | Local var 1    | R5 - 4                ; First local variable
 * | Local var 2    | R5 - 8                ; Second local variable
 * | ...            |
 * | Local var n    | R5 - (n*4)            ; Last local variable
 * +----------------+ <- R7 (current stack pointer, after local allocation)
 * Lower addresses (higher memory addresses in FRISC)
 * </pre>
 *
 * <p><b>Epilogue Sharing:</b>
 *
 * <p>All return paths in a function share the same epilogue code. Return statements jump to the
 * exit label (which precedes the epilogue) to avoid duplicate code:
 *
 * <pre>
 * if (condition) {
 *     return 1;  // Jumps to L_EXIT
 * }
 * return 0;      // Jumps to L_EXIT
 *
 * L_EXIT:        ; Single epilogue for all return paths
 *     ADD R7, %D K, R7
 *     POP R5
 *     RET
 * </pre>
 *
 * <p><b>Complexity:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) - constant number of instructions
 *   <li><b>Space Complexity:</b> O(1) - fixed stack overhead (saved R5, return address)
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FunctionPrologueEpilogueGenerator {

  /**
   * Generates the function prologue (save frame pointer, allocate local variables).
   *
   * <p><b>FRISC Code Sequence:</b>
   *
   * <ol>
   *   <li>Save old frame pointer: {@code PUSH R5}
   *   <li>Set new frame pointer: {@code MOVE R7, R5}
   *   <li>Allocate locals: {@code SUB R7, %D K, R7} (if K > 0)
   * </ol>
   *
   * <p><b>FRISC Semantics:</b>
   *
   * <ul>
   *   <li>PUSH saves R5 on stack (at current R7), then decrements R7
   *   <li>MOVE sets R5 = R7 (R5 now points to saved old R5)
   *   <li>SUB allocates space by moving R7 downward (decreasing address)
   *   <li>If no locals (K = 0), allocation step is skipped
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
    functionContext
        .emitter()
        .emitInstruction("MOVE", "R7", "R5", "R5 = current SP -> base of frame");

    // Allocate space for local variables
    // Stack grows downward, so we subtract from R7
    if (localSize > 0) {
      functionContext
          .emitter()
          .emitInstruction(
              "SUB",
              "R7",
              "%D " + localSize,
              "R7",
              "allocate " + (localSize / 4) + " local variables");
    }
  }

  /**
   * Generates the function epilogue (deallocate locals, restore frame pointer, return).
   *
   * <p><b>FRISC Code Sequence:</b>
   *
   * <ol>
   *   <li>Deallocate locals: {@code ADD R7, %D K, R7} (if K > 0)
   *   <li>Restore frame pointer: {@code POP R5}
   *   <li>Return: {@code RET} (pops return address and jumps)
   * </ol>
   *
   * <p><b>FRISC Semantics:</b>
   *
   * <ul>
   *   <li>ADD moves R7 back up (increasing address) to deallocate locals
   *   <li>POP restores old R5 from stack, then increments R7
   *   <li>RET pops return address from stack and jumps to it
   *   <li>After RET, stack is back to caller's state
   * </ul>
   *
   * <p><b>Note:</b> This epilogue is shared by all return paths. Return statements jump to the exit
   * label (which precedes this epilogue) to avoid duplicate code.
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
      functionContext
          .emitter()
          .emitInstruction("ADD", "R7", "%D " + localSize, "R7", "deallocate local variables");
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
