package hr.fer.ppj.codegen.expr.field;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.utils.LValueAddressGenerator;
import hr.fer.ppj.codegen.structs.NestedStructArraySizeExtractor;
import hr.fer.ppj.codegen.structs.StructArraySizeExtractor;
import hr.fer.ppj.codegen.structs.StructFieldOffsetCalculator;
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
 * Generates FRISC assembly code for struct field access operations.
 * 
 * <p>This class handles the generation of code for struct member access
 * (field access) expressions like {@code struct.field}, implementing the
 * <b>field access code generation algorithm</b> that translates C struct
 * field operations into FRISC assembly with proper address calculation.
 * 
 * <p><b>Grammar Rule:</b> Handles field access from {@code <postfiks_izraz>}:
 * <pre>
 * &lt;postfiks_izraz&gt; ::= &lt;postfiks_izraz&gt; TOCKA IDN
 * </pre>
 * 
 * <p><b>Algorithm: Field Access Code Generation</b>
 * 
 * <p>The algorithm works as follows:
 * <ol>
 *   <li><b>Base Address Resolution:</b> Compute the address of the struct object:
 *       <ul>
 *         <li>Local struct: Use frame pointer with offset (e.g., {@code R5 - 20})</li>
 *         <li>Global struct: Use global label (e.g., {@code G_P})</li>
 *         <li>Nested field access: Recursively compute base address</li>
 *         <li>Array element: Compute array element address first</li>
 *       </ul>
 *   </li>
 *   <li><b>Field Offset Lookup:</b> Get the byte offset of the field within the struct</li>
 *   <li><b>Field Address Calculation:</b> Add field offset to base address:
 *       {@code ADD R0, %D offset, R0}</li>
 *   <li><b>Memory Access:</b> Load or store field value using computed address:
 *       <ul>
 *         <li>int/float/pointer fields: Use {@code LOAD} / {@code STORE}</li>
 *         <li>char fields: Use {@code LOADB} / {@code STOREB}</li>
 *       </ul>
 *   </li>
 * </ol>
 * 
 * <p><b>Field Offset Calculation:</b>
 * 
 * <p>Field offsets are calculated using {@link hr.fer.ppj.codegen.structs.StructFieldOffsetCalculator}:
 * <ul>
 *   <li>Fields are laid out in declaration order</li>
 *   <li>No padding between fields (tightly packed)</li>
 *   <li>Offset = sum of sizes of all preceding fields</li>
 * </ul>
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
 * <p><b>Arrays Inside Structs:</b>
 * 
 * <p>For field access like {@code o.arr[i]}:
 * <ol>
 *   <li>Compute address of {@code o}</li>
 *   <li>Add offset of {@code arr} field → base address of array</li>
 *   <li>Calculate array element address: base + index × element_size</li>
 * </ol>
 * 
 * <p><b>FRISC Code Pattern (Field Access as R-value):</b>
 * <pre>
 * ; Field access: p.x
 * 
 * ; Compute base address of struct
 * MOVE R5, R0                    ; local struct
 * ADD R0, -20, R0                ; add struct offset
 * ; OR
 * MOVE G_P, R0                   ; global struct
 * 
 * ; Add field offset
 * ADD R0, %D 0, R0               ; add offset of field 'x' (0 bytes)
 * 
 * ; Load field value
 * LOAD R0, (R0)                  ; load p.x (int field)
 * ; OR
 * LOADB R0, (R0)                 ; load p.c (char field)
 * </pre>
 * 
 * <p><b>FRISC Code Pattern (Field Access as L-value):</b>
 * <pre>
 * ; Field assignment: p.x = value
 * 
 * ; Save value (already in R0)
 * MOVE R0, R1                    ; save value
 * 
 * ; Compute field address (same as above)
 * MOVE R5, R0                    ; base address
 * ADD R0, -20, R0                ; struct offset
 * ADD R0, %D 0, R0               ; field offset
 * 
 * ; Store value
 * STORE R1, (R0)                 ; store to p.x (int field)
 * ; OR
 * STOREB R1, (R0)                ; store to p.c (char field)
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
public final class FieldAccessGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator expressionGenerator;
    private final LValueAddressGenerator addressGenerator;
    
    /**
     * Creates a new field access generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the expression generator for base expression evaluation
     */
    public FieldAccessGenerator(CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
        this.addressGenerator = new LValueAddressGenerator(context, expressionGenerator);
    }
    
    /**
     * Sets the parse tree for extracting struct array sizes.
     * 
     * <p>This propagates the parse tree to the LValueAddressGenerator so it can
     * extract array sizes for nested structs with arrays.
     * 
     * @param parseTree the parse tree from semantic analysis
     */
    public void setParseTree(NonTerminalNode parseTree) {
        if (addressGenerator != null) {
            addressGenerator.setParseTree(parseTree);
        }
    }
    
    /**
     * Generates code for struct field access: base.field.
     * 
     * <p><b>Grammar Rule:</b> Implements {@code <postfiks_izraz> ::= <postfiks_izraz> TOCKA IDN}
     * 
     * <p><b>FRISC Code Sequence:</b>
     * <pre>
     * ; Compute base address (if base is a variable)
     * MOVE base_address, R0       ; or load from stack/global
     * 
     * ; Add field offset
     * ADD R0, %D field_offset, R0  ; field address in R0
     * 
     * ; Load field value
     * LOAD R0, (R0)               ; for int/float/pointer
     * ; OR
     * LOADB R0, (R0)              ; for char
     * </pre>
     * 
     * <p>The result is loaded into register R0.
     * 
     * @param base the base expression (struct object)
     * @param fieldName the field name (IDN lexeme)
     */
    public void generateFieldAccess(NonTerminalNode base, String fieldName) {
        Objects.requireNonNull(base, "base must not be null");
        Objects.requireNonNull(fieldName, "fieldName must not be null");
        
        // Get the type of the base expression
        Type baseType = base.attributes().type();
        if (baseType == null) {
            throw new IllegalStateException("Base expression has no type annotation");
        }
        
        Type strippedBaseType = TypeSystem.stripConst(baseType);
        if (!(strippedBaseType instanceof StructType structType)) {
            throw new IllegalStateException("Base expression is not a struct type: " + strippedBaseType);
        }
        
        // Get field offset - extract array sizes if needed (for structs with array fields)
        Map<String, Integer> arraySizes = null;
        Map<String, Map<String, Integer>> nestedStructArraySizes = null;
        
        if (addressGenerator != null && addressGenerator.getArraySizeExtractor() != null) {
            var arraySizeExtractor = addressGenerator.getArraySizeExtractor();
            String structTag = structType.tag();
            arraySizes = arraySizeExtractor.extractArraySizes(structTag);
            
            // Extract array sizes for nested struct fields recursively
            nestedStructArraySizes = new java.util.HashMap<>();
            
            // First, extract for the current struct itself (in case it's nested in another struct)
            if (structTag != null) {
                Map<String, Integer> currentArraySizes = arraySizeExtractor.extractArraySizes(structTag);
                nestedStructArraySizes.put(structTag, currentArraySizes);
            }
            
            // Then extract recursively for all nested structs at any depth
            NestedStructArraySizeExtractor.extractNestedStructArraySizes(structType, arraySizeExtractor, nestedStructArraySizes);
        }
        
        Integer fieldOffset = StructFieldOffsetCalculator.getFieldOffset(structType, fieldName, arraySizes, nestedStructArraySizes);
        if (fieldOffset == null) {
            throw new IllegalStateException("Field '" + fieldName + "' not found in struct");
        }
        
        // Get field type
        Type fieldType = structType.getFieldType(fieldName);
        if (fieldType == null) {
            throw new IllegalStateException("Field '" + fieldName + "' has no type");
        }
        
        // Compute address of the field (handles nested field access recursively)
        // This will compute: base_address + offset(inner) + offset(value) for o.inner.value
        generateFieldAddress(base, fieldName, "R0");
        
        // Load field value
        // Note: char, int, and float are all 4 bytes, so use word LOAD for all types
        context.emitter().emitInstruction("LOAD", "R0", "(R0)", "load field '" + fieldName + "'");
    }
    
    /**
     * Generates code to compute the address of a field (for l-value use in assignments).
     * 
     * <p>This method computes the address of a field without loading its value,
     * which is needed for assignments like {@code p.x = value}.
     * 
     * <p>For nested field access like {@code o.inner.value}, this method:
     * <ol>
     *   <li>Uses the address generator to recursively compute the base address</li>
     *   <li>Adds the field offset to get the final field address</li>
     * </ol>
     * 
     * @param base the base expression (struct object)
     * @param fieldName the field name
     * @param targetRegister the register to store the field address in
     */
    public void generateFieldAddress(NonTerminalNode base, String fieldName, String targetRegister) {
        Objects.requireNonNull(base, "base must not be null");
        Objects.requireNonNull(fieldName, "fieldName must not be null");
        Objects.requireNonNull(targetRegister, "targetRegister must not be null");
        
        // Get the type of the base expression
        Type baseType = base.attributes() != null ? base.attributes().type() : null;
        if (baseType == null) {
            throw new IllegalStateException("Base expression has no type annotation");
        }
        
        Type strippedBaseType = TypeSystem.stripConst(baseType);
        if (!(strippedBaseType instanceof StructType structType)) {
            throw new IllegalStateException("Base expression is not a struct type: " + strippedBaseType);
        }
        
        // Get field offset - extract array sizes if needed (for structs with array fields)
        Map<String, Integer> arraySizes = null;
        Map<String, Map<String, Integer>> nestedStructArraySizes = null;
        
        if (addressGenerator != null && addressGenerator.getArraySizeExtractor() != null) {
            var arraySizeExtractor = addressGenerator.getArraySizeExtractor();
            String structTag = structType.tag();
            arraySizes = arraySizeExtractor.extractArraySizes(structTag);
            
            // Extract array sizes for nested struct fields recursively
            nestedStructArraySizes = new java.util.HashMap<>();
            
            // First, extract for the current struct itself (in case it's nested in another struct)
            if (structTag != null) {
                Map<String, Integer> currentArraySizes = arraySizeExtractor.extractArraySizes(structTag);
                nestedStructArraySizes.put(structTag, currentArraySizes);
            }
            
            // Then extract recursively for all nested structs at any depth
            NestedStructArraySizeExtractor.extractNestedStructArraySizes(structType, arraySizeExtractor, nestedStructArraySizes);
        }
        
        Integer fieldOffset = StructFieldOffsetCalculator.getFieldOffset(structType, fieldName, arraySizes, nestedStructArraySizes);
        if (fieldOffset == null) {
            throw new IllegalStateException("Field '" + fieldName + "' not found in struct");
        }
        
        // Use the address generator to compute the base address
        // This handles nested field access, arrays, and simple variables recursively
        addressGenerator.generateAddress(base, targetRegister);
        
        // Add field offset to base address
        if (fieldOffset != 0) {
            context.emitter().emitInstruction("ADD", targetRegister, "%D " + fieldOffset, targetRegister, 
                "add field offset for '" + fieldName + "'");
        }
    }
    
    /**
     * Generates code to store a value into a field.
     * 
     * @param base the base expression (struct object)
     * @param fieldName the field name
     * @param sourceRegister the register containing the value to store
     */
    public void generateFieldStore(NonTerminalNode base, String fieldName, String sourceRegister) {
        Objects.requireNonNull(base, "base must not be null");
        Objects.requireNonNull(fieldName, "fieldName must not be null");
        Objects.requireNonNull(sourceRegister, "sourceRegister must not be null");
        
        // Save source value
        String tempRegister = "R1";
        if (sourceRegister.equals("R1")) {
            tempRegister = "R2";
        }
        context.emitter().emitInstruction("MOVE", sourceRegister, tempRegister, "save value to store");
        
        // Compute field address (handles nested field access recursively)
        // This will compute: base_address + offset(inner) + offset(value) for o.inner.value
        generateFieldAddress(base, fieldName, "R0");
        
        // Store value
        // Note: char, int, and float are all 4 bytes, so use word STORE
        context.emitter().emitInstruction("STORE", tempRegister, "(R0)", "store field '" + fieldName + "'");
    }
    
    
}
