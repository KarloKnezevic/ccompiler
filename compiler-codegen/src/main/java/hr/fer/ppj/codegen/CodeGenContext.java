package hr.fer.ppj.codegen;

import hr.fer.ppj.codegen.emitter.FriscEmitter;
import hr.fer.ppj.codegen.model.ActivationRecord;
import hr.fer.ppj.codegen.util.LabelGenerator;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import java.util.Objects;

/**
 * Immutable context object that carries shared state during code generation.
 * 
 * <p>This record encapsulates all the common resources and state needed throughout the code
 * generation process. It serves as a <b>context parameter</b> that is passed down through
 * the recursive tree traversal, allowing all code generation methods to access shared
 * infrastructure without global state.
 * 
 * <p><b>Design Pattern: Immutable Context</b>
 * 
 * <p>This class implements the <b>immutable context pattern</b>, which provides several benefits:
 * <ul>
 *   <li><b>Thread Safety:</b> Immutability ensures the context can be safely shared across
 *       multiple code generation passes or threads</li>
 *   <li><b>Predictable State:</b> Since the context cannot be modified, there are no hidden
 *       side effects from context mutations</li>
 *   <li><b>Functional Style:</b> The {@code with*} methods create new contexts, following
 *       a functional programming style that makes state transitions explicit</li>
 *   <li><b>Recursive Safety:</b> When traversing the parse tree recursively, each level can
 *       create a new context without affecting parent contexts</li>
 * </ul>
 * 
 * <p><b>Context Components:</b>
 * 
 * <ul>
 *   <li><b>{@code globalScope}:</b> Global symbol table from semantic analysis.
 *       Contains all global variables, functions, and type definitions.
 *       Used to resolve global identifiers during code generation.</li>
 *       
 *   <li><b>{@code emitter}:</b> FRISC code emitter for instruction output.
 *       All generated FRISC instructions are emitted through this object.
 *       The emitter buffers code in memory until {@code writeToFile()} is called.</li>
 *       
 *   <li><b>{@code labelGenerator}:</b> Generator for unique labels (L1, L2, F_MAIN, etc.).
 *       Ensures all generated labels are unique by maintaining separate counters
 *       for different label types (functions, control flow, loops, etc.).</li>
 *       
 *   <li><b>{@code activationRecord}:</b> Current function's stack frame (null for global scope).
 *       Tracks local variables and parameters with their stack offsets relative to R5.
 *       Used to generate correct memory addresses for local variable access.</li>
 *       
 *   <li><b>{@code functionExitLabel}:</b> Label for function epilogue (null if not in function).
 *       Used by return statements to jump to the function exit code.
 *       Set when entering a function, cleared when exiting.</li>
 *       
 *   <li><b>{@code loopBreakLabel}:</b> Label for break statements (null if not in loop).
 *       Used by break statements to exit the innermost loop.
 *       Set when entering a loop (while, for), cleared when exiting.</li>
 *       
 *   <li><b>{@code loopContinueLabel}:</b> Label for continue statements (null if not in loop).
 *       Used by continue statements to jump to the loop condition check.
 *       Set when entering a loop, cleared when exiting.</li>
 * </ul>
 * 
 * <p><b>Context Lifecycle:</b>
 * 
 * <ol>
 *   <li><b>Initial Context:</b> Created at the start of code generation with global scope
 *       only (no activation record, no function/loop labels)</li>
 *   <li><b>Function Entry:</b> When entering a function, a new context is created with:
 *       - A new activation record for the function's stack frame
 *       - A function exit label for return statements</li>
 *   <li><b>Loop Entry:</b> When entering a loop, a new context is created with:
 *       - Loop break and continue labels
 *       - Preserves the function context (activation record, exit label)</li>
 *   <li><b>Context Exit:</b> When exiting a function or loop, the previous context is restored
 *       (by returning from the recursive call)</li>
 * </ol>
 * 
 * <p><b>FRISC Semantics:</b>
 * 
 * <p>The activation record (when present) tracks:
 * <ul>
 *   <li><b>Local Variable Offsets:</b> Negative offsets from R5 (e.g., R5-4, R5-8)</li>
 *   <li><b>Parameter Offsets:</b> Positive offsets from R5 (e.g., R5+8, R5+12)</li>
 *   <li><b>Total Stack Frame Size:</b> Used to allocate/deallocate stack space in prologue/epilogue</li>
 *   <li><b>Array Variable Sizes:</b> Used to detect array parameters (size > 4 bytes)</li>
 * </ul>
 * 
 * <p><b>Usage Example:</b>
 * <pre>
 * // Create initial context (global scope)
 * CodeGenContext globalContext = new CodeGenContext(globalScope, emitter, labelGen, 
 *     null, null, null, null);
 * 
 * // Enter function: create new context with activation record
 * ActivationRecord ar = new ActivationRecord();
 * ar.addParameter("x");
 * ar.addLocalVariable("y");
 * String exitLabel = labelGen.getUniqueLabel("F_EXIT");
 * CodeGenContext funcContext = globalContext
 *     .withActivationRecord(ar)
 *     .withFunctionExitLabel(exitLabel);
 * 
 * // Enter loop: create new context with loop labels
 * LoopLabelSet loopLabels = labelGen.generateLoopLabels();
 * CodeGenContext loopContext = funcContext
 *     .withLoopLabels(loopLabels.breakLabel(), loopLabels.continueLabel());
 * </pre>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record CodeGenContext(
    SymbolTable globalScope,
    FriscEmitter emitter,
    LabelGenerator labelGenerator,
    ActivationRecord activationRecord,
    String functionExitLabel,
    String loopBreakLabel,
    String loopContinueLabel
) {
    
    /**
     * Creates a new code generation context.
     * 
     * @param globalScope the global symbol table from semantic analysis
     * @param emitter the FRISC code emitter
     * @param labelGenerator the label generator for unique labels
     * @param activationRecord the current function's activation record (may be null for global scope)
     * @param functionExitLabel the label for function exit (may be null)
     * @param loopBreakLabel the label for loop break (may be null)
     * @param loopContinueLabel the label for loop continue (may be null)
     */
    public CodeGenContext {
        Objects.requireNonNull(globalScope, "globalScope must not be null");
        Objects.requireNonNull(emitter, "emitter must not be null");
        Objects.requireNonNull(labelGenerator, "labelGenerator must not be null");
        // Other parameters may be null
    }
    
    /**
     * Creates a new context with a different activation record.
     * 
     * @param newActivationRecord the new activation record
     * @return a new context with the updated activation record
     */
    public CodeGenContext withActivationRecord(ActivationRecord newActivationRecord) {
        return new CodeGenContext(globalScope, emitter, labelGenerator, newActivationRecord, 
                                functionExitLabel, loopBreakLabel, loopContinueLabel);
    }
    
    /**
     * Creates a new context with function exit label.
     * 
     * @param exitLabel the function exit label
     * @return a new context with the updated exit label
     */
    public CodeGenContext withFunctionExitLabel(String exitLabel) {
        return new CodeGenContext(globalScope, emitter, labelGenerator, activationRecord, 
                                exitLabel, loopBreakLabel, loopContinueLabel);
    }
    
    /**
     * Creates a new context with loop labels.
     * 
     * @param breakLabel the loop break label
     * @param continueLabel the loop continue label
     * @return a new context with the updated loop labels
     */
    public CodeGenContext withLoopLabels(String breakLabel, String continueLabel) {
        return new CodeGenContext(globalScope, emitter, labelGenerator, activationRecord, 
                                functionExitLabel, breakLabel, continueLabel);
    }
    
    /**
     * Checks if we are currently in a function (have an activation record).
     * 
     * @return true if in function scope, false if in global scope
     */
    public boolean isInFunction() {
        return activationRecord != null;
    }
}
