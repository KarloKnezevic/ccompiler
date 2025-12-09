package hr.fer.ppj.codegen.structs;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.utils.LValueAddressGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates code to compute addresses of struct fields.
 * 
 * <p>This class handles the generation of code for computing addresses of struct fields,
 * including nested field access. It implements the <b>struct field address calculation
 * algorithm</b> used throughout code generation.
 * 
 * <p><b>Algorithm: Field Address Calculation</b>
 * 
 * <p>The algorithm works as follows:
 * <ol>
 *   <li><b>Base Address Resolution:</b> Recursively compute the address of the base expression
 *       (handles nested field access like {@code o.middle.inner})</li>
 *   <li><b>Field Offset Lookup:</b> Get the byte offset of the field within the struct using
 *       {@link StructFieldOffsetCalculator}</li>
 *   <li><b>Address Calculation:</b> Add field offset to base address:
 *       {@code ADD targetRegister, %D fieldOffset, targetRegister}</li>
 * </ol>
 * 
 * <p><b>Nested Field Access:</b>
 * 
 * <p>For nested field access like {@code o.inner.value}:
 * <ol>
 *   <li>Compute address of {@code o} (struct Outer object)</li>
 *   <li>Add offset of {@code inner} field → address of inner struct</li>
 *   <li>Add offset of {@code value} field → final field address</li>
 * </ol>
 * 
 * <p>This is handled recursively by delegating to the main {@link LValueAddressGenerator}
 * for the base expression, which may itself be a field access.
 * 
 * <p><b>Array Size Extraction:</b>
 * 
 * <p>For structs containing arrays, array sizes must be extracted from the parse tree
 * to correctly calculate field offsets. This class uses {@link StructArraySizeExtractor}
 * to extract array sizes for the current struct and all nested structs.
 * 
 * <p><b>FRISC Code Pattern:</b>
 * <pre>
 * ; Field access: p.x
 * 
 * ; Compute base address of struct (delegated to LValueAddressGenerator)
 * MOVE R5, R0                    ; local struct
 * ADD R0, -20, R0                ; add struct offset
 * 
 * ; Add field offset
 * ADD R0, %D 0, R0               ; add offset of field 'x' (0 bytes)
 * ; R0 now contains address of p.x
 * </pre>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) for code generation (constant number of instructions)</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only registers</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class StructFieldAddressGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator expressionGenerator;
    private final LValueAddressGenerator addressGenerator;
    private final StructArraySizeExtractor arraySizeExtractor;
    
    /**
     * Creates a new struct field address generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the expression generator for base expression evaluation
     * @param addressGenerator the main address generator for recursive base address computation
     * @param arraySizeExtractor the extractor for array sizes in structs
     */
    public StructFieldAddressGenerator(CodeGenContext context, 
                                      ExpressionCodeGenerator expressionGenerator,
                                      LValueAddressGenerator addressGenerator,
                                      StructArraySizeExtractor arraySizeExtractor) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
        this.addressGenerator = Objects.requireNonNull(addressGenerator, "addressGenerator must not be null");
        this.arraySizeExtractor = arraySizeExtractor; // May be null if parse tree not set
    }
    
    /**
     * Generates code to compute the address of a struct field.
     * 
     * <p>Handles nested field access recursively:
     * <ul>
     *   <li>{@code p.x}: compute address of p, add offset of x</li>
     *   <li>{@code o.inner.value}: recursively compute address of o.inner, add offset of value</li>
     *   <li>{@code o.middle.inner.arr}: recursively handles arbitrary nesting depth</li>
     * </ul>
     * 
     * <p><b>Grammar Rule:</b> Handles field access from {@code <postfiks_izraz>}:
     * <pre>
     * &lt;postfiks_izraz&gt; ::= &lt;postfiks_izraz&gt; TOCKA IDN
     * </pre>
     * 
     * @param fieldAccessNode the field access node (<postfiks_izraz> TOCKA IDN)
     * @param targetRegister the register to store the field address in
     * @throws IllegalStateException if the node structure is invalid or field is not found
     */
    public void generateFieldAddress(NonTerminalNode fieldAccessNode, String targetRegister) {
        Objects.requireNonNull(fieldAccessNode, "fieldAccessNode must not be null");
        Objects.requireNonNull(targetRegister, "targetRegister must not be null");
        
        // Pattern: <postfiks_izraz> TOCKA IDN
        // child 0: base expression (<postfiks_izraz>)
        // child 1: TOCKA
        // child 2: IDN (field name)
        List<ParseNode> children = fieldAccessNode.children();
        if (children.size() != 3) {
            throw new IllegalStateException("Invalid field access node structure: expected 3 children, got " + children.size() + 
                " for node " + fieldAccessNode.symbol());
        }
        
        NonTerminalNode base = (NonTerminalNode) children.get(0);
        TerminalNode fieldIdn = (TerminalNode) children.get(2);
        String fieldName = fieldIdn.lexeme();
        
        // Step 1: Recursively compute address of base expression
        // This handles deeply nested expressions like o.middle.inner by recursively
        // processing each level:
        //   - For o.middle.inner.value: recursively processes o -> o.middle -> o.middle.inner
        //   - Each recursive call adds the appropriate field offset
        //   - The final address in targetRegister is the address of the base struct
        // This delegation to addressGenerator allows handling:
        //   - Simple variables: o (local or global)
        //   - Nested field access: o.middle.inner (recursive)
        //   - Array elements: o.arr[0] (handled by ArrayElementAddressGenerator)
        addressGenerator.generateAddress(base, targetRegister);
        
        // Step 2: Get type of base expression (must be a StructType)
        // The semantic analyzer sets the type on each node during semantic analysis.
        // For deeply nested expressions, each intermediate node has the correct type:
        //   - base = o.middle.inner should have type Inner (the innermost struct)
        //   - base = o.middle should have type Middle
        //   - base = o should have type Outer
        // This type information is essential for calculating field offsets correctly.
        Type baseType = base.attributes() != null ? base.attributes().type() : null;
        if (baseType == null) {
            throw new IllegalStateException("Base expression has no type annotation. " +
                "Node: " + base.symbol() + ", field: " + fieldName);
        }
        
        // Strip const qualification (const doesn't affect field offsets)
        Type strippedBaseType = TypeSystem.stripConst(baseType);
        if (!(strippedBaseType instanceof StructType structType)) {
            throw new IllegalStateException("Field access base is not a struct type: " + strippedBaseType + 
                " (field: " + fieldName + ")");
        }
        
        // Step 3: Extract array sizes for struct and nested structs (if needed)
        // This is critical for structs containing arrays, as array sizes are needed
        // to calculate field offsets correctly. For example:
        //   struct Outer {
        //       struct Inner inner;  // Inner might have arrays
        //       int arr[10];         // This struct has arrays
        //   };
        // When accessing outer.inner.value, we need:
        //   - Array sizes for Inner (to calculate Inner's size)
        //   - Array sizes for Outer (to calculate offset of 'inner' field)
        Map<String, Integer> arraySizes = null;
        Map<String, Map<String, Integer>> nestedStructArraySizes = null;
        
        if (arraySizeExtractor != null) {
            // Extract array sizes for the current struct
            // This handles cases where the struct itself has array fields
            String structTag = structType.tag();
            arraySizes = arraySizeExtractor.extractArraySizes(structTag);
            
            // Extract array sizes for all nested structs recursively
            // This is needed when calculating field offsets, as nested struct sizes
            // need array sizes if those nested structs contain arrays.
            // Example: Outer -> Middle -> Inner (all with arrays)
            nestedStructArraySizes = new java.util.HashMap<>();
            
            // First, extract for the current struct itself
            // This handles the case where the struct is nested in another struct
            // and we need its array sizes for offset calculation
            if (structTag != null) {
                Map<String, Integer> currentArraySizes = arraySizeExtractor.extractArraySizes(structTag);
                nestedStructArraySizes.put(structTag, currentArraySizes);
            }
            
            // Then extract recursively for all nested structs at any depth
            // This ensures we get array sizes for Inner when processing Outer
            NestedStructArraySizeExtractor.extractNestedStructArraySizes(structType, arraySizeExtractor, nestedStructArraySizes);
        }
        
        // Step 4: Get field offset using StructFieldOffsetCalculator
        // This calculates the byte offset of the field within the struct, taking into account:
        //   - Field declaration order (fields are laid out in declaration order)
        //   - Field sizes (including arrays and nested structs)
        //   - Array sizes (if provided)
        Integer fieldOffset = StructFieldOffsetCalculator.getFieldOffset(structType, fieldName, arraySizes, nestedStructArraySizes);
        if (fieldOffset == null) {
            throw new IllegalStateException("Field '" + fieldName + "' not found in struct " + structType.tag() + 
                " (type: " + structType + ")");
        }
        
        // Step 5: Add field offset to the base address
        // Final address = base_address + field_offset
        // Even if offset is 0, we could emit the instruction, but it's a no-op, so skip it
        // This optimization avoids unnecessary ADD instructions for the first field
        if (fieldOffset != 0) {
            context.emitter().emitInstruction("ADD", targetRegister, "%D " + fieldOffset, targetRegister,
                "add offset for field '" + fieldName + "' in struct " + structType.tag());
        }
    }
    
}
