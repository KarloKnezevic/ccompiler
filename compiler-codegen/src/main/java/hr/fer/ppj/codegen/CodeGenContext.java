package hr.fer.ppj.codegen;

import hr.fer.ppj.codegen.emitter.FriscEmitter;
import hr.fer.ppj.codegen.model.ActivationRecord;
import hr.fer.ppj.codegen.util.LabelGenerator;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import java.util.Objects;

/**
 * Context object that carries shared state during code generation.
 * 
 * <p>This immutable record encapsulates the common resources needed throughout the code
 * generation process, including the symbol table, code emitter, label generator,
 * and current activation record for local variable management.
 * 
 * <p><b>Immutability:</b> The context is immutable and thread-safe, making it suitable
 * for use in recursive code generation scenarios. To update context state, use the
 * {@code with*} methods to create new context instances.
 * 
 * <p><b>Context Components:</b>
 * <ul>
 *   <li>{@code globalScope} - Global symbol table from semantic analysis</li>
 *   <li>{@code emitter} - FRISC code emitter for instruction output</li>
 *   <li>{@code labelGenerator} - Generator for unique labels (L1, L2, F_MAIN, etc.)</li>
 *   <li>{@code activationRecord} - Current function's stack frame (null for global scope)</li>
 *   <li>{@code functionExitLabel} - Label for function epilogue (null if not in function)</li>
 *   <li>{@code loopBreakLabel} - Label for break statements (null if not in loop)</li>
 *   <li>{@code loopContinueLabel} - Label for continue statements (null if not in loop)</li>
 * </ul>
 * 
 * <p><b>FRISC Semantics:</b> The activation record tracks:
 * <ul>
 *   <li>Local variable offsets (negative from R5)</li>
 *   <li>Parameter offsets (positive from R5)</li>
 *   <li>Total stack frame size</li>
 *   <li>Array variable sizes (for array parameter detection)</li>
 * </ul>
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
