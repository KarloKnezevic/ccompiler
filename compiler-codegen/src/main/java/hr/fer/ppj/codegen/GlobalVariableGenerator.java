package hr.fer.ppj.codegen;

import hr.fer.ppj.codegen.global.ArraySizeExtractor;
import hr.fer.ppj.codegen.global.InitializerExtractor;
import hr.fer.ppj.semantics.symbols.Symbol;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.List;
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
    private InitializerExtractor initializerExtractor;
    private ArraySizeExtractor arraySizeExtractor;
    
    /**
     * Creates a new global variable generator.
     * 
     * @param context the code generation context
     */
    public GlobalVariableGenerator(CodeGenContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
    }
    
    /**
     * Sets the parse tree for extracting initializer values and array sizes.
     * 
     * @param parseTree the parse tree from semantic analysis
     */
    public void setParseTree(NonTerminalNode parseTree) {
        this.initializerExtractor = new InitializerExtractor(parseTree);
        this.arraySizeExtractor = new ArraySizeExtractor(parseTree);
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
        Type varType = TypeSystem.stripConst(variable.type());
        
        // Check if it's an array
        if (varType instanceof ArrayType arrayType) {
            generateArrayVariable(label, variable, arrayType);
            return;
        }
        
        // Check if it's a char type
        boolean isChar = varType == PrimitiveType.CHAR;
        
        // Try to find initializer value from parse tree
        String initValue = null;
        if (initializerExtractor != null) {
            initValue = initializerExtractor.findInitializerValue(variable.name(), isChar);
        }
        if (initValue == null) {
            initValue = "0"; // Default initialization
        }
        
        String comment = "global " + (variable.isConst() ? "const " : "") + 
                        variable.type() + " " + variable.name() + 
                        (initValue.equals("0") ? "" : " = " + initValue);
        
        // Always use %D prefix for C integer constants (including char values)
        // According to FRISC rules: C integer constants must use %D prefix
        String dataValue = "%D " + initValue;
        
        context.emitter().emitData(label, "DW", dataValue, comment);
    }
    
    /**
     * Generates FRISC data declaration for an array variable.
     * 
     * <p>For this project, both int and char arrays use element size 4 bytes.
     * Uninitialized arrays use the `DS directive (backtick DS) to reserve space.
     * Initialized arrays use DW with comma-separated values.
     */
    private void generateArrayVariable(String label, VariableSymbol variable, ArrayType arrayType) {
        // Try to find array initializer from parse tree
        List<String> initValues = null;
        if (initializerExtractor != null) {
            initValues = initializerExtractor.findArrayInitializer(variable.name(), arrayType);
        }
        
        // For this project: both int and char arrays use element size 4 bytes
        // We treat chars as 4-byte elements and use LOAD instead of LOADB
        int elementSize = 4;
        
        if (initValues == null || initValues.isEmpty()) {
            // No initializer - use `DS directive (backtick DS) to allocate space
            // Extract array size from parse tree
            int arraySize = 0;
            if (arraySizeExtractor != null) {
                arraySize = arraySizeExtractor.extractArraySize(variable.name());
            }
            if (arraySize > 0) {
                int totalBytes = arraySize * elementSize;
                String comment = "global " + variable.type() + " " + variable.name();
                // Use backtick DS: `DS %D N
                context.emitter().emitData(label, "`DS", "%D " + totalBytes, comment);
            } else {
                // Fallback: allocate space for at least 1 element
                String comment = "global " + variable.type() + " " + variable.name();
                context.emitter().emitData(label, "`DS", "%D " + elementSize, comment);
            }
            return;
        }
        
        // Generate array with initializer values using DW
        StringBuilder dataValue = new StringBuilder();
        boolean first = true;
        for (String value : initValues) {
            if (!first) {
                dataValue.append(", ");
            }
            dataValue.append(value);
            first = false;
        }
        
        String comment = "global " + variable.type() + " " + variable.name();
        context.emitter().emitData(label, "DW", dataValue.toString(), comment);
    }
    
}
