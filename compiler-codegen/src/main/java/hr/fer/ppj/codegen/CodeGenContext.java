package hr.fer.ppj.codegen;

import hr.fer.ppj.codegen.func.ActivationRecord;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import java.util.Objects;

/**
 * Context object that carries shared state during code generation.
 * 
 * <p>This class encapsulates the common resources needed throughout the code
 * generation process, including the symbol table, code emitter, label generator,
 * and current activation record for local variable management.
 * 
 * <p>The context is immutable and thread-safe, making it suitable for use in
 * recursive code generation scenarios.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record CodeGenContext(
    SymbolTable globalScope,
    FriscEmitter emitter,
    LabelGenerator labelGenerator,
    ActivationRecord activationRecord
) {
    
    /**
     * Creates a new code generation context.
     * 
     * @param globalScope the global symbol table from semantic analysis
     * @param emitter the FRISC code emitter
     * @param labelGenerator the label generator for unique labels
     * @param activationRecord the current function's activation record (may be null for global scope)
     */
    public CodeGenContext {
        Objects.requireNonNull(globalScope, "globalScope must not be null");
        Objects.requireNonNull(emitter, "emitter must not be null");
        Objects.requireNonNull(labelGenerator, "labelGenerator must not be null");
        // activationRecord may be null for global scope
    }
    
    /**
     * Creates a new context with a different activation record.
     * 
     * @param newActivationRecord the new activation record
     * @return a new context with the updated activation record
     */
    public CodeGenContext withActivationRecord(ActivationRecord newActivationRecord) {
        return new CodeGenContext(globalScope, emitter, labelGenerator, newActivationRecord);
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
