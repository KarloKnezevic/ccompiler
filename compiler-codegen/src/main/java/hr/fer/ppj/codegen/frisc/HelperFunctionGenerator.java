package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.emitter.FriscEmitter;
import java.util.Objects;

/**
 * Orchestrates generation of FRISC helper functions for operations not directly supported by the architecture.
 * 
 * <p>FRISC architecture does not have native MUL (multiplication) or DIV (division) instructions.
 * This class coordinates the generation of helper functions by delegating to specialized generators.
 * It acts as a <b>facade</b> that manages the generation of all integer helper functions based on
 * which operations are actually needed by the generated code.
 * 
 * <p><b>Design Pattern: Facade</b>
 * 
 * <p>This class implements the <b>facade pattern</b>, providing a simplified interface for
 * generating multiple related helper functions. It:
 * <ul>
 *   <li>Hides the complexity of managing multiple generators</li>
 *   <li>Provides a single entry point for helper function generation</li>
 *   <li>Only generates helpers that are actually needed (lazy generation)</li>
 *   <li>Manages dependencies between helpers (e.g., float helpers may need integer helpers)</li>
 * </ul>
 * 
 * <p><b>Helper Functions Generated:</b>
 * 
 * <p>This class delegates to specialized generators:
 * <ul>
 *   <li><b>{@link MultiplicationHelperGenerator}:</b> Generates F_MUL for 32-bit signed integer
 *       multiplication using the Russian peasant algorithm (O(32) complexity)</li>
 *   <li><b>{@link DivisionHelperGenerator}:</b> Generates F_DIV for 32-bit signed integer
 *       division using binary long division (O(32) complexity)</li>
 *   <li><b>{@link Mul64HelperGenerator}:</b> Generates F_MUL64 for 64-bit unsigned integer
 *       multiplication using extended Russian peasant algorithm (O(32) complexity)</li>
 * </ul>
 * 
 * <p><b>Why Helper Functions?</b>
 * 
 * <p>FRISC architecture lacks native multiplication and division instructions because:
 * <ul>
 *   <li><b>Hardware Simplicity:</b> FRISC is designed as a simple, educational architecture</li>
 *   <li><b>Cost Reduction:</b> Multiplication and division hardware is expensive</li>
 *   <li><b>Instruction Set Simplicity:</b> Keeps the instruction set small and easy to understand</li>
 * </ul>
 * 
 * <p>Therefore, these operations must be implemented in software using basic instructions
 * (ADD, SUB, SHL, SHR, etc.). The helper functions provide efficient implementations using
 * well-known algorithms from compiler literature.
 * 
 * <p><b>FRISC Calling Convention:</b>
 * 
 * <p>All helper functions follow the standard FRISC calling convention:
 * <ul>
 *   <li><b>Argument Passing:</b> Arguments are pushed right-to-left on the stack
 *       (C convention). For example, to call F_MUL(a, b):
 *       <pre>
 *       PUSH b    ; second argument (right operand)
 *       PUSH a    ; first argument (left operand)
 *       CALL F_MUL
 *       </pre>
 *   </li>
 *   <li><b>Return Value:</b> Return value is placed in register R6</li>
 *   <li><b>Stack Cleanup:</b> Caller cleans up arguments from the stack:
 *       <pre>
 *       ADD R7, %D 8, R7  ; remove 2 arguments (2 × 4 bytes)
 *       </pre>
 *   </li>
 *   <li><b>Register Preservation:</b> Helper functions may use R0-R4, but must preserve
 *       R5 (frame pointer) and R7 (stack pointer)</li>
 * </ul>
 * 
 * <p><b>Lazy Generation Strategy:</b>
 * 
 * <p>Helpers are only generated if they are actually needed:
 * <ol>
 *   <li>During expression code generation, operations call methods like
 *       {@code emitter.markMulNeeded()} to mark which helpers are needed</li>
 *   <li>After processing the translation unit, the main code generator queries
 *       which helpers are needed</li>
 *   <li>This class generates only the needed helpers, avoiding unnecessary code bloat</li>
 * </ol>
 * 
 * <p><b>Dependency Management:</b>
 * 
 * <p>Some helpers have dependencies:
 * <ul>
 *   <li><b>F_MUL64:</b> Used by float multiplication helper (F_FMUL)</li>
 *   <li><b>F_MUL and F_DIV:</b> Used directly by integer operations in user code</li>
 * </ul>
 * 
 * <p>The float helpers mark these integer helpers as needed during their generation,
 * ensuring the correct generation order in the pipeline.
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(n) where n is the number of helpers to generate
 *       (typically 0-4 helpers, each O(32) to O(64) depending on the helper)</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only a few generator objects</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class HelperFunctionGenerator {
    
    private final MultiplicationHelperGenerator mulGenerator = new MultiplicationHelperGenerator();
    private final DivisionHelperGenerator divGenerator = new DivisionHelperGenerator();
    private final Mul64HelperGenerator mul64Generator = new Mul64HelperGenerator();
    
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
            mulGenerator.generate(context);
        }
        
        if (needsDiv) {
            divGenerator.generate(context);
        }
    }
    
    /**
     * Generates the F_MUL64 helper function for 64-bit unsigned integer multiplication.
     * 
     * <p>This function is used by float multiplication to compute the full 64-bit product
     * of two 32-bit Q16.16 fixed-point values.
     * 
     * @param context the code generation context
     */
    public void generateMul64Helper(CodeGenContext context) {
        mul64Generator.generate(context);
    }
}

