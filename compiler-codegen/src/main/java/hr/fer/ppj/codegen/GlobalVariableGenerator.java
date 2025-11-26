package hr.fer.ppj.codegen;

import hr.fer.ppj.semantics.symbols.Symbol;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import java.util.Objects;

/**
 * Generates FRISC assembly declarations for global variables.
 * 
 * <p>This class processes the global symbol table and generates appropriate
 * FRISC data declarations (DW, DH, DB) for global variables and constants.
 * Global variables are placed at the end of the program, after all function
 * definitions.
 * 
 * <p>Each global variable gets a unique label following the pattern
 * {@code G_<VARIABLE_NAME>} and is initialized with its declared value
 * or zero if no initializer is provided.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class GlobalVariableGenerator {
    
    private final CodeGenContext context;
    
    /**
     * Creates a new global variable generator.
     * 
     * @param context the code generation context
     */
    public GlobalVariableGenerator(CodeGenContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
    }
    
    /**
     * Generates FRISC data declarations for all global variables.
     * 
     * <p>This method examines the global symbol table and generates
     * appropriate data declarations for each global variable found.
     */
    public void generateGlobalVariables() {
        context.emitter().emitComment("Global variables");
        
        // Process all symbols in the global scope
        for (Symbol symbol : context.globalScope().entries().values()) {
            if (symbol instanceof VariableSymbol varSymbol) {
                generateGlobalVariable(varSymbol);
            }
        }
        
        context.emitter().emitNewline();
    }
    
    /**
     * Generates a FRISC data declaration for a single global variable.
     * 
     * @param variable the variable symbol to generate code for
     */
    private void generateGlobalVariable(VariableSymbol variable) {
        String label = context.labelGenerator().getGlobalVariableLabel(variable.name());
        
        // For now, initialize all global variables to 0
        // TODO: Handle actual initializer values from the parse tree
        String comment = "global " + (variable.isConst() ? "const " : "") + 
                        variable.type() + " " + variable.name();
        
        context.emitter().emitData(label, "DW", "%D 0", comment);
    }
}
