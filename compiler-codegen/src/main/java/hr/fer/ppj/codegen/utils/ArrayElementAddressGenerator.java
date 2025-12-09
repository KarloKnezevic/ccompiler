package hr.fer.ppj.codegen.utils;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.structs.NestedStructArraySizeExtractor;
import hr.fer.ppj.codegen.structs.StructArraySizeExtractor;
import hr.fer.ppj.codegen.structs.StructSizeCalculator;
import hr.fer.ppj.codegen.types.TypeSizeCalculator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.Map;
import java.util.Objects;

/**
 * Generates code to compute addresses of array elements.
 * 
 * <p>This class handles the generation of code for computing addresses of array elements,
 * including arrays within structs and nested structs. It implements the <b>array element
 * address calculation algorithm</b> used throughout code generation.
 * 
 * <p><b>Algorithm: Array Element Address Calculation</b>
 * 
 * <p>The algorithm works as follows:
 * <ol>
 *   <li><b>Index Evaluation:</b> Evaluate the index expression (result in R0)</li>
 *   <li><b>Index Preservation:</b> Save index on stack before computing base address
 *       (recursive base computation may use R0)</li>
 *   <li><b>Base Address Resolution:</b> Recursively compute the base address of the array
 *       (handles field access, nested structs, etc.)</li>
 *   <li><b>Element Size Calculation:</b> Calculate element size (may require array size
 *       extraction for struct elements with arrays)</li>
 *   <li><b>Index Multiplication:</b> Multiply index by element size (optimized for powers of 2)</li>
 *   <li><b>Address Calculation:</b> Add index offset to base address</li>
 * </ol>
 * 
 * <p><b>Grammar Rule:</b> Handles array indexing from {@code <postfiks_izraz>}:
 * <pre>
 * &lt;postfiks_izraz&gt; ::= &lt;postfiks_izraz&gt; L_UGL_ZAGRADA &lt;izraz&gt; D_UGL_ZAGRADA
 * </pre>
 * 
 * <p><b>Nested Array Access:</b>
 * 
 * <p>For nested array access like {@code o.arr[i]}:
 * <ol>
 *   <li>Evaluate index {@code i}</li>
 *   <li>Save index on stack</li>
 *   <li>Compute base address of {@code o.arr} (recursively handles field access)</li>
 *   <li>Restore index from stack</li>
 *   <li>Multiply index by element size</li>
 *   <li>Add to base address</li>
 * </ol>
 * 
 * <p><b>Element Size Optimization:</b>
 * 
 * <p>Element size multiplication is optimized:
 * <ul>
 *   <li><b>4 bytes (most common):</b> Use {@code SHL index, %D 2, index} (left shift by 2)</li>
 *   <li><b>Power of 2:</b> Use {@code SHL} with appropriate shift amount</li>
 *   <li><b>Other sizes:</b> Use repeated {@code ADD} operations</li>
 * </ul>
 * 
 * <p><b>FRISC Code Pattern:</b>
 * <pre>
 * ; Array access: a[i]
 * 
 * ; Evaluate index
 * ... (evaluate i, result in R0) ...
 * PUSH R0                      ; save index
 * 
 * ; Compute base address (delegated to LValueAddressGenerator)
 * MOVE G_A, R0                 ; base address
 * 
 * ; Restore index
 * POP R1                       ; index in R1
 * 
 * ; Multiply index by element size (4 bytes)
 * SHL R1, %D 2, R1             ; index * 4
 * 
 * ; Add to base address
 * ADD R0, R1, R0               ; element address
 * </pre>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) for code generation (constant number of instructions)</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only registers and stack for index</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ArrayElementAddressGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator expressionGenerator;
    private final LValueAddressGenerator addressGenerator;
    private final StructArraySizeExtractor arraySizeExtractor;
    
    /**
     * Creates a new array element address generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the expression generator for evaluating index expressions
     * @param addressGenerator the main address generator for recursive base address computation
     * @param arraySizeExtractor the extractor for array sizes in structs (may be null)
     */
    public ArrayElementAddressGenerator(CodeGenContext context,
                                       ExpressionCodeGenerator expressionGenerator,
                                       LValueAddressGenerator addressGenerator,
                                       StructArraySizeExtractor arraySizeExtractor) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
        this.addressGenerator = Objects.requireNonNull(addressGenerator, "addressGenerator must not be null");
        this.arraySizeExtractor = arraySizeExtractor; // May be null if parse tree not set
    }
    
    /**
     * Generates code to compute the address of an array element.
     * 
     * <p>Pattern: <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
     * 
     * <p>This method uses PUSH/POP to save the index calculation, ensuring
     * that recursive base address computation doesn't overwrite it.
     * 
     * <p>Handles deeply nested array access like:
     * <ul>
     *   <li>{@code a[i]}: simple array</li>
     *   <li>{@code o.arr[i]}: array field in struct</li>
     *   <li>{@code o.middle.inner.arr[0]}: deeply nested struct with array field</li>
     * </ul>
     * 
     * @param arrayIndexNode the array indexing node
     * @param targetRegister the register to store the element address in
     * @throws IllegalStateException if the node structure is invalid or element size cannot be calculated
     */
    public void generateArrayElementAddress(NonTerminalNode arrayIndexNode, String targetRegister) {
        Objects.requireNonNull(arrayIndexNode, "arrayIndexNode must not be null");
        Objects.requireNonNull(targetRegister, "targetRegister must not be null");
        
        // Pattern: <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
        var children = arrayIndexNode.children();
        if (children.size() != 4) {
            throw new IllegalStateException("Invalid array indexing node structure: expected 4 children, got " + 
                children.size() + " for node " + arrayIndexNode.symbol());
        }
        
        NonTerminalNode base = (NonTerminalNode) children.get(0);
        NonTerminalNode indexExpr = (NonTerminalNode) children.get(2);
        
        // 1) Evaluate index expression (result in R0)
        expressionGenerator.generateExpression(indexExpr);
        
        // 2) Save index on stack before computing base address
        // This is critical: the recursive generateAddress() call may use R0, R1, R2
        // for computing nested field/array addresses, so we must save the index first
        context.emitter().emitInstruction("PUSH", "R0", null, "save array index");
        
        // 3) Compute base address recursively (handles field access, nested structs, etc.)
        // For o.middle.inner.arr[0], this recursively processes:
        //   o -> o.middle -> o.middle.inner -> o.middle.inner.arr
        // Each level adds its offset, so the final address in targetRegister is the
        // base address of the array (before indexing)
        // This may use R0, R1, R2 freely, but won't touch the stack
        addressGenerator.generateAddress(base, targetRegister);
        
        // 4) Restore index from stack
        context.emitter().emitInstruction("POP", "R1", null, "restore array index");
        
        // 5) Get the type of the base expression to determine element size
        // For deeply nested expressions like o.middle.inner.arr, the semantic analyzer
        // should have set the type to ArrayType on the base node
        Type baseType = base.attributes() != null ? base.attributes().type() : null;
        if (baseType == null) {
            throw new IllegalStateException("Base expression has no type annotation for array indexing. " +
                "Node: " + base.symbol());
        }
        
        Type strippedBaseType = TypeSystem.stripConst(baseType);
        if (!(strippedBaseType instanceof ArrayType arrayType)) {
            throw new IllegalStateException("Array indexing base is not an array type: " + strippedBaseType + 
                " (node: " + base.symbol() + ")");
        }
        
        // 6) Get element type and compute element size
        Type elementType = arrayType.elementType();
        
        // Calculate element size - handle structs with arrays by extracting array sizes
        int elementSize;
        Type strippedElementType = TypeSystem.stripConst(elementType);
        
        // Extract nested struct array sizes if element type is a struct with nested structs
        Map<String, Map<String, Integer>> nestedStructArraySizes = null;
        if (strippedElementType instanceof StructType structElementType && arraySizeExtractor != null) {
            nestedStructArraySizes = new java.util.HashMap<>();
            NestedStructArraySizeExtractor.extractNestedStructArraySizes(structElementType, arraySizeExtractor, nestedStructArraySizes);
        }
        
        try {
            elementSize = TypeSizeCalculator.calculateTypeSize(strippedElementType, nestedStructArraySizes);
        } catch (IllegalArgumentException e) {
            // Element type might be a struct with array fields - need to extract array sizes
            if (strippedElementType instanceof StructType structElementType && arraySizeExtractor != null) {
                String structTag = structElementType.tag();
                Map<String, Integer> arraySizes = arraySizeExtractor.extractArraySizes(structTag);
                
                // nestedStructArraySizes already extracted above
                if (nestedStructArraySizes == null) {
                    nestedStructArraySizes = new java.util.HashMap<>();
                    NestedStructArraySizeExtractor.extractNestedStructArraySizes(structElementType, arraySizeExtractor, nestedStructArraySizes);
                }
                
                elementSize = StructSizeCalculator.calculateStructSize(structElementType, arraySizes, nestedStructArraySizes);
            } else {
                // Re-throw if we can't handle it
                throw new IllegalStateException("Cannot calculate element size for array indexing: " + e.getMessage() + 
                    " (elementType: " + elementType + ", stripped: " + strippedElementType + ")");
            }
        }
        
        // 7) Multiply index by element size (index is in R1)
        multiplyIndexByElementSize(elementSize);
        
        // 8) Add index offset to base address
        // targetRegister contains base address of array, R1 contains index * elementSize
        // Final address = base + (index * elementSize)
        context.emitter().emitInstruction("ADD", targetRegister, "R1", targetRegister,
            "element address = base + (index * " + elementSize + ")");
    }
    
    /**
     * Multiplies the index (in R1) by the element size.
     * 
     * <p>Uses optimized instructions based on element size:
     * <ul>
     *   <li>4 bytes: {@code SHL R1, %D 2, R1} (left shift by 2)</li>
     *   <li>Power of 2: {@code SHL} with appropriate shift amount</li>
     *   <li>Other sizes: Repeated {@code ADD} operations</li>
     * </ul>
     * 
     * @param elementSize the element size in bytes
     */
    private void multiplyIndexByElementSize(int elementSize) {
        if (elementSize == 4) {
            // Optimize for 4-byte elements (most common case: int, float, pointer)
            context.emitter().emitInstruction("SHL", "R1", "%D 2", "R1", "index * 4 (element size)");
        } else if (elementSize == 1) {
            // For 1-byte elements, no multiplication needed (index already in R1)
            // Note: In this project, char is 4 bytes, so this case may not occur
        } else if ((elementSize & (elementSize - 1)) == 0) {
            // Power of 2: use SHL
            int shiftAmount = Integer.numberOfTrailingZeros(elementSize);
            context.emitter().emitInstruction("SHL", "R1", "%D " + shiftAmount, "R1", 
                "index * " + elementSize + " (element size)");
        } else {
            // Generic case: use repeated ADD
            // R1 contains index, multiply by elementSize
            String tempReg = "R2";
            context.emitter().emitInstruction("MOVE", "R1", tempReg, "copy index");
            for (int i = 1; i < elementSize; i++) {
                context.emitter().emitInstruction("ADD", tempReg, "R1", tempReg, 
                    "index * " + (i + 1));
            }
            context.emitter().emitInstruction("MOVE", tempReg, "R1", "index * elementSize");
        }
    }
    
}
