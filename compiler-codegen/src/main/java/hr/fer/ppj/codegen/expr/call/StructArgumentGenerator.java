package hr.fer.ppj.codegen.expr.call;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.structs.StructArraySizeExtractor;
import hr.fer.ppj.codegen.structs.StructSizeCalculator;
import hr.fer.ppj.codegen.utils.LValueAddressGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.types.StructType;
import java.util.Map;
import java.util.Objects;

/**
 * Generates code for passing struct arguments to functions.
 * 
 * <p>This class handles the generation of code for passing struct arguments
 * to functions, implementing the <b>struct argument passing algorithm</b>
 * that copies structs word-by-word onto the stack.
 * 
 * <p><b>Algorithm: Struct Argument Passing</b>
 * 
 * <p>The algorithm works as follows:
 * <ol>
 *   <li><b>Size Calculation:</b> Calculate struct size (may require array size extraction)</li>
 *   <li><b>Address Resolution:</b> Compute address of source struct</li>
 *   <li><b>Word-by-Word Copy:</b> Copy struct onto stack word-by-word using a loop</li>
 * </ol>
 * 
 * <p><b>Calling Convention:</b>
 * 
 * <p>Struct arguments are passed by value, which means the entire struct
 * is copied onto the stack. The struct is pushed word-by-word starting
 * from offset 0, so that:
 * <ul>
 *   <li>First word (offset 0) is pushed first → ends up at lowest address (R5+8)</li>
 *   <li>Last word (offset N) is pushed last → ends up at highest address</li>
 * </ul>
 * 
 * <p>This ensures that when the callee accesses the struct via positive offsets
 * from R5, field 0 is at the lowest address, matching the struct layout.
 * 
 * <p><b>FRISC Code Pattern:</b>
 * <pre>
 * ; Struct argument: foo(p) where p is a struct
 * 
 * ; Compute source address (p)
 * MOVE R5, R2                    ; frame pointer
 * ADD R2, -20, R2                 ; source struct offset
 * 
 * ; Push loop
 * MOVE %D 24, R4                  ; struct size (24 bytes)
 * L_LOOP:
 *     CMP R4, %D 0
 *     JP_EQ L_END
 *     LOAD R0, (R2)               ; load word from source
 *     PUSH R0                     ; push word onto stack
 *     ADD R2, %D 4, R2            ; increment source pointer
 *     SUB R4, %D 4, R4            ; decrement counter
 *     JP L_LOOP
 * L_END:
 * </pre>
 * 
 * <p><b>Array Size Extraction:</b>
 * 
 * <p>For structs containing arrays, array sizes must be extracted from the
 * parse tree to correctly calculate struct size. This class uses
 * {@link StructArraySizeExtractor} to extract array sizes for the current
 * struct and all nested structs.
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(n) where n is struct size in bytes (word-by-word copy)</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only registers</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class StructArgumentGenerator {
    
    private final CodeGenContext context;
    private final LValueAddressGenerator addressGenerator;
    
    /**
     * Creates a new struct argument generator.
     * 
     * @param context the code generation context
     * @param addressGenerator the address generator for computing struct addresses
     */
    public StructArgumentGenerator(CodeGenContext context, LValueAddressGenerator addressGenerator) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.addressGenerator = Objects.requireNonNull(addressGenerator, "addressGenerator must not be null");
    }
    
    /**
     * Generates code to pass a struct argument by copying it onto the stack.
     * 
     * <p>Struct arguments are passed by value, so we need to copy the entire struct
     * onto the stack word-by-word. We push words individually starting from offset 0
     * and moving forward through the struct.
     * 
     * <p>The callee expects the struct with field 0 at the lowest address (R5+8 for
     * the first parameter). When we push from offset 0 forward:
     * <ul>
     *   <li>First word (offset 0) is pushed first → ends up at lowest address (R5+8) ✓</li>
     *   <li>Last word (offset N) is pushed last → ends up at highest address ✓</li>
     * </ul>
     * 
     * @param argExpr the argument expression (must evaluate to a struct)
     * @param structType the struct type
     * @param arraySizes optional map from field name to array length (for array fields in this struct)
     * @param nestedStructArraySizes optional map from struct tag to array sizes map (for nested structs with arrays)
     */
    public void generateStructArgument(NonTerminalNode argExpr, StructType structType, 
                                      Map<String, Integer> arraySizes, 
                                      Map<String, Map<String, Integer>> nestedStructArraySizes) {
        Objects.requireNonNull(argExpr, "argExpr must not be null");
        Objects.requireNonNull(structType, "structType must not be null");
        
        // Step 1: Calculate struct size using provided array sizes
        // This size is needed to know how many bytes to copy onto the stack
        // For structs with arrays, array sizes must be provided to calculate the total size
        int structSize = StructSizeCalculator.calculateStructSize(structType, arraySizes, nestedStructArraySizes);
        
        context.emitter().emitComment("Struct argument: push " + structSize + " bytes onto stack");
        
        // Step 2: Compute source address (struct argument address)
        // The argExpr can be:
        //   - A struct variable: p (local or global)
        //   - A struct field access: o.inner (nested struct)
        //   - An array element: arr[0] where element type is struct
        // The addressGenerator handles all these cases recursively
        addressGenerator.generateAddress(argExpr, "R0");
        
        // Move source address to R2 (we'll use R2 as the source pointer in the loop)
        // R0 will be used for loading words, R2 for the source pointer
        context.emitter().emitInstruction("MOVE", "R0", "R2", "source addr (struct arg)");
        
        // Step 3: Push struct word-by-word from beginning (offset 0) forward
        // We push words individually starting from offset 0, so that:
        //   - First word (offset 0) is pushed first → ends up at lowest address (R5+8 for first param)
        //   - Last word (offset N) is pushed last → ends up at highest address
        // This ensures the callee sees the struct with field 0 at the lowest address
        String loopLabel = context.labelGenerator().generateLabel();
        String endLabel = context.labelGenerator().generateLabel();
        
        // Initialize loop counter: R4 = remaining bytes
        // We'll decrement this by 4 (word size) each iteration
        context.emitter().emitInstruction("MOVE", "%D " + structSize, "R4", "remaining bytes");
        
        context.emitter().emitLabel(loopLabel, "struct arg push loop");
        
        // Check if counter is zero (all bytes copied)
        context.emitter().emitInstruction("CMP", "R4", "%D 0", null);
        context.emitter().emitInstruction("JP_EQ", endLabel, "done if counter == 0");
        
        // Load word from source: R0 = *R2 (starting from offset 0)
        // R2 points to the current position in the source struct
        context.emitter().emitInstruction("LOAD", "R0", "(R2)", "load word from source");
        
        // Push word onto stack
        // This pushes the word onto the stack, which will be accessed by the callee
        // at positive offsets from R5 (e.g., R5+8 for first parameter)
        context.emitter().emitInstruction("PUSH", "R0", null, "push struct word");
        
        // Increment source pointer: R2 += 4 (move forward through struct)
        // Move to the next word in the source struct
        context.emitter().emitInstruction("ADD", "R2", "%D 4", "R2", "increment source pointer");
        
        // Decrement counter: R4 -= 4
        // Track remaining bytes to copy
        context.emitter().emitInstruction("SUB", "R4", "%D 4", "R4", "decrement remaining bytes");
        
        // Loop back to continue copying
        context.emitter().emitInstruction("JP", loopLabel, "continue push loop");
        
        context.emitter().emitLabel(endLabel, "end struct arg push");
    }
}
