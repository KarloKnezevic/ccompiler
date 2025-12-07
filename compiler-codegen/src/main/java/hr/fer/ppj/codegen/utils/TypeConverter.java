package hr.fer.ppj.codegen.utils;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.emitter.FriscEmitter;
import java.util.Objects;

/**
 * Generates code for type conversions between int, char, and float.
 * 
 * <p>This utility class encapsulates the common patterns for type conversions
 * in FRISC assembly, including calls to helper functions for float conversions.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TypeConverter {
    
    private final FriscEmitter emitter;
    
    /**
     * Creates a new type converter.
     * 
     * @param context the code generation context
     */
    public TypeConverter(CodeGenContext context) {
        Objects.requireNonNull(context, "context must not be null");
        this.emitter = context.emitter();
    }
    
    /**
     * Converts an integer value in R0 to float (Q16.16) format.
     * 
     * <p>Calls F_I2F helper function to perform the conversion.
     */
    public void convertIntToFloat() {
        emitter.markIntToFloatNeeded();
        emitter.emitInstruction("PUSH", "R0", null, "push integer value");
        emitter.emitInstruction("CALL", "F_I2F", null, "convert int to float");
        emitter.emitInstruction("ADD", "R7", "%D 4", "R7", "cleanup argument");
        emitter.emitInstruction("MOVE", "R6", "R0", "move float result to R0");
    }
    
    /**
     * Converts a float value in R0 to integer format (truncates).
     * 
     * <p>Calls F_F2I helper function to perform the conversion.
     */
    public void convertFloatToInt() {
        emitter.markFloatToIntNeeded();
        emitter.emitInstruction("PUSH", "R0", null, "push float value");
        emitter.emitInstruction("CALL", "F_F2I", null, "convert float to int");
        emitter.emitInstruction("ADD", "R7", "%D 4", "R7", "cleanup argument");
        emitter.emitInstruction("MOVE", "R6", "R0", "move integer result to R0");
    }
}

