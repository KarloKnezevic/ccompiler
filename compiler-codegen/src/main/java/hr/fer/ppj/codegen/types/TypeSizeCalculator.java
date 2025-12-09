package hr.fer.ppj.codegen.types;

import hr.fer.ppj.codegen.structs.StructSizeCalculator;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PointerType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.Map;
import java.util.Objects;

/**
 * Calculates the size of types in bytes.
 * 
 * <p>This class provides methods to compute the memory size of all supported types,
 * including primitive types, pointers, arrays, and structs. It implements the
 * <b>type size calculation algorithm</b> used throughout code generation.
 * 
 * <p><b>Type Sizes:</b>
 * <ul>
 *   <li><b>char:</b> 4 bytes (same as int and float in FRISC)</li>
 *   <li><b>int:</b> 4 bytes</li>
 *   <li><b>float:</b> 4 bytes (Q16.16 fixed-point format)</li>
 *   <li><b>pointer:</b> 4 bytes</li>
 *   <li><b>array T[n]:</b> n × sizeof(T) bytes (size must be provided from semantic attributes)</li>
 *   <li><b>struct S:</b> SUM(sizeof(field)) for all fields (delegates to {@link StructSizeCalculator})</li>
 * </ul>
 * 
 * <p><b>Algorithm: Type Size Calculation</b>
 * 
 * <p>The algorithm works as follows:
 * <ol>
 *   <li><b>Type Classification:</b> Determine the type kind (primitive, pointer, array, struct)</li>
 *   <li><b>Size Lookup/Calculation:</b>
 *       <ul>
 *         <li>Primitive types: Fixed size (char/int/float = 4 bytes)</li>
 *         <li>Pointer types: Fixed size (4 bytes)</li>
 *         <li>Array types: Cannot calculate from type alone (size must be provided)</li>
 *         <li>Struct types: Recursively calculate using {@link StructSizeCalculator}</li>
 *       </ul>
 *   </li>
 * </ol>
 * 
 * <p><b>Struct Type Support:</b>
 * 
 * <p>For struct types, this class delegates to {@link StructSizeCalculator}, which handles:
 * <ul>
 *   <li><b>Simple structs:</b> Sum of all field sizes</li>
 *   <li><b>Structs with arrays:</b> Requires array sizes to be provided (extracted from parse tree)</li>
 *   <li><b>Nested structs:</b> Recursively calculates nested struct sizes</li>
 *   <li><b>Nested structs with arrays:</b> Requires array sizes for all nested structs at all levels</li>
 * </ul>
 * 
 * <p>The {@code nestedStructArraySizes} parameter allows providing array sizes for nested structs:
 * <ul>
 *   <li>Key: Struct tag name (e.g., "Inner")</li>
 *   <li>Value: Map from field name to array length (e.g., {"arr": 10})</li>
 * </ul>
 * 
 * <p><b>Example (Nested Structs with Arrays):</b>
 * <pre>
 * struct Inner {
 *     int arr[10];  // Array field
 * };
 * 
 * struct Outer {
 *     struct Inner inner;  // Nested struct
 * };
 * 
 * // To calculate size of Outer, provide array sizes:
 * Map&lt;String, Map&lt;String, Integer&gt;&gt; nestedSizes = new HashMap&lt;&gt;();
 * nestedSizes.put("Inner", Map.of("arr", 10));
 * 
 * int size = TypeSizeCalculator.calculateTypeSize(outerType, nestedSizes);
 * // Result: 4 (size of Inner struct: 10 * 4 = 40 bytes)
 * </pre>
 * 
 * <p><b>Limitations:</b>
 * <ul>
 *   <li><b>Array Types:</b> ArrayType doesn't store size information. Array sizes must be
 *       provided from semantic attributes or extracted from the parse tree.</li>
 *   <li><b>Nested Structs:</b> For structs containing arrays, array sizes must be provided
 *       via the nestedStructArraySizes parameter.</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>
 * TypeSizeCalculator.calculateTypeSize(PrimitiveType.INT);  // 4
 * TypeSizeCalculator.calculateTypeSize(PrimitiveType.CHAR); // 4
 * TypeSizeCalculator.calculateTypeSize(new PointerType(...)); // 4
 * // ArrayType: throws IllegalArgumentException (size must be provided)
 * // StructType: delegates to StructSizeCalculator
 * </pre>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) for primitive/pointer types, O(n) for struct types
 *       where n is the number of fields</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only local variables</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TypeSizeCalculator {
    
    /**
     * Private constructor to prevent instantiation.
     */
    private TypeSizeCalculator() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * Calculates the size of a type in bytes.
     * 
     * <p>This method handles all type kinds:
     * <ul>
     *   <li>Primitive types: char (4), int (4), float (4)</li>
     *   <li>Pointer types: 4 bytes</li>
     *   <li>Array types: throws IllegalArgumentException (size must be provided)</li>
     *   <li>Struct types: recursively calculated via {@link StructSizeCalculator}</li>
     * </ul>
     * 
     * @param type the type to calculate size for
     * @return the size in bytes
     * @throws NullPointerException if type is null
     * @throws IllegalArgumentException if type is not supported or is an array (size must be provided)
     */
    public static int calculateTypeSize(Type type) {
        return calculateTypeSize(type, null);
    }
    
    /**
     * Calculates the size of a type in bytes, with optional array sizes for nested structs.
     * 
     * <p>This overload allows providing array sizes for nested structs that contain arrays.
     * The map should map struct tag names to their array sizes (field name -> array length).
     * 
     * <p><b>Note:</b> This method still cannot calculate array sizes from ArrayType alone.
     * Array sizes must be provided from semantic attributes or extracted from the parse tree.
     * 
     * @param type the type to calculate size for
     * @param nestedStructArraySizes optional map from struct tag to array sizes map (for nested structs with arrays)
     * @return the size in bytes
     * @throws NullPointerException if type is null
     * @throws IllegalArgumentException if type is not supported or is an array (size must be provided)
     */
    public static int calculateTypeSize(Type type, Map<String, Map<String, Integer>> nestedStructArraySizes) {
        Objects.requireNonNull(type, "type must not be null");
        
        // Strip const qualification (const doesn't affect size)
        Type stripped = TypeSystem.stripConst(type);
        
        // Handle primitive types (char, int, float, void)
        if (stripped instanceof PrimitiveType primitiveType) {
            return switch (primitiveType) {
                case CHAR -> 4;  // char is 4 bytes (same as int and float in FRISC)
                case INT -> 4;   // int is 4 bytes
                case FLOAT -> 4; // float is 4 bytes (Q16.16 fixed-point format)
                case VOID -> throw new IllegalArgumentException("Cannot calculate size of void type");
            };
        } 
        // Handle pointer types
        else if (stripped instanceof PointerType) {
            return 4; // Pointers are 4 bytes (32-bit addresses)
        } 
        // Handle array types
        else if (stripped instanceof ArrayType) {
            // ArrayType doesn't store size - size is stored in semantic attributes
            // For codegen purposes, we need to calculate size from element type
            // This is a limitation - we can't know array size from type alone
            // Arrays inside structs should have their size available from semantic attributes
            // For now, throw an exception - callers should provide size from semantic attributes
            throw new IllegalArgumentException("Cannot calculate array size from ArrayType alone - size must be provided from semantic attributes");
        } 
        // Handle struct types (delegates to StructSizeCalculator)
        else if (stripped instanceof StructType structType) {
            // Extract array sizes for this struct from nestedStructArraySizes map
            // The map structure is: structTag -> (fieldName -> arrayLength)
            Map<String, Integer> arraySizes = null;
            if (nestedStructArraySizes != null) {
                String structTag = structType.tag();
                arraySizes = nestedStructArraySizes.get(structTag);
                // If not found, use empty map (struct might not have arrays)
                // This allows StructSizeCalculator to try calculating without array sizes first
                if (arraySizes == null) {
                    arraySizes = new java.util.HashMap<>();
                }
            }
            // Delegate to StructSizeCalculator, passing through nestedStructArraySizes
            // so that deeper nested structs (e.g., Outer -> Middle -> Inner) can also
            // have their array sizes resolved
            return StructSizeCalculator.calculateStructSize(structType, arraySizes, nestedStructArraySizes);
        } 
        // Unsupported type
        else {
            throw new IllegalArgumentException("Unsupported type for size calculation: " + stripped.getClass().getSimpleName());
        }
    }
}
