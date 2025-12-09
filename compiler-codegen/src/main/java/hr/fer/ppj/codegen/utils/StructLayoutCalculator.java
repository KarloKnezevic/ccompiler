package hr.fer.ppj.codegen.utils;

import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PointerType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Utility class for calculating struct sizes and field offsets.
 * 
 * <p>This class provides methods to compute the memory layout of struct types,
 * including total size and field offsets. It implements the <b>struct layout
 * calculation algorithm</b> used throughout code generation.
 * 
 * <p><b>Algorithm: Struct Layout Calculation</b>
 * 
 * <p>The algorithm works as follows:
 * <ol>
 *   <li><b>Field Iteration:</b> Iterate through fields in declaration order</li>
 *   <li><b>Field Size Calculation:</b> Calculate size of each field's type:
 *       <ul>
 *         <li>char: 1 byte</li>
 *         <li>int: 4 bytes</li>
 *         <li>float: 4 bytes</li>
 *         <li>pointer: 4 bytes</li>
 *         <li>array: element_size × array_length</li>
 *         <li>struct: recursively calculate struct size</li>
 *       </ul>
 *   </li>
 *   <li><b>Field Offset Assignment:</b> Assign offset = current_offset, then increment by field size</li>
 *   <li><b>Total Size:</b> Sum of all field sizes (no padding, tightly packed)</li>
 * </ol>
 * 
 * <p><b>Layout Rules:</b>
 * <ul>
 *   <li><b>No Padding:</b> Structs are tightly packed (fields laid out back-to-back)</li>
 *   <li><b>Declaration Order:</b> Fields are laid out in declaration order</li>
 *   <li><b>Nested Structs:</b> Nested structs are laid out inline (no special handling)</li>
 *   <li><b>Arrays:</b> Arrays are laid out as contiguous sequences of elements</li>
 * </ul>
 * 
 * <p><b>Type Sizes:</b>
 * <ul>
 *   <li>char: 1 byte</li>
 *   <li>int: 4 bytes</li>
 *   <li>float: 4 bytes</li>
 *   <li>pointer: 4 bytes (even if pointer semantics not implemented yet)</li>
 *   <li>array T[n]: n × sizeof(T) bytes</li>
 *   <li>struct S: SUM(sizeof(field)) for all fields</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>
 * struct Point {
 *     int x;      // offset 0, size 4
 *     int y;      // offset 4, size 4
 * };              // total size: 8 bytes
 * 
 * struct Outer {
 *     struct Point p;  // offset 0, size 8
 *     int arr[3];      // offset 8, size 12
 *     char c;          // offset 20, size 1
 * };                   // total size: 21 bytes
 * </pre>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(n) where n is the number of fields (including nested structs)</li>
 *   <li><b>Space Complexity:</b> O(n) for storing field offsets</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class StructLayoutCalculator {
    
    /**
     * Private constructor to prevent instantiation.
     */
    private StructLayoutCalculator() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * Calculates the total size of a struct type in bytes.
     * 
     * <p>This method recursively calculates the size by summing the sizes
     * of all fields in declaration order.
     * 
     * @param structType the struct type to calculate size for
     * @return the total size in bytes
     * @throws NullPointerException if structType is null
     */
    public static int calculateStructSize(StructType structType) {
        return calculateStructSize(structType, null);
    }
    
    /**
     * Calculates the total size of a struct type in bytes, with optional array size information.
     * 
     * <p>This overload allows providing array sizes for struct fields that are arrays.
     * Array sizes are provided as a map from field name to array length.
     * 
     * @param structType the struct type to calculate size for
     * @param arraySizes optional map from field name to array length (for array fields)
     * @return the total size in bytes
     * @throws NullPointerException if structType is null
     * @throws IllegalArgumentException if struct contains array fields without size information
     */
    public static int calculateStructSize(StructType structType, Map<String, Integer> arraySizes) {
        return calculateStructSize(structType, arraySizes, null);
    }
    
    /**
     * Calculates the total size of a struct type in bytes, with optional array size information for nested structs.
     * 
     * <p>This overload allows providing array sizes for nested structs that contain arrays.
     * The nestedStructArraySizes map should map struct tag names to their array sizes.
     * 
     * @param structType the struct type to calculate size for
     * @param arraySizes optional map from field name to array length (for array fields in this struct)
     * @param nestedStructArraySizes optional map from struct tag to array sizes map (for nested structs with arrays)
     * @return the total size in bytes
     * @throws NullPointerException if structType is null
     * @throws IllegalArgumentException if struct contains array fields without size information
     */
    public static int calculateStructSize(StructType structType, Map<String, Integer> arraySizes, 
                                         Map<String, Map<String, Integer>> nestedStructArraySizes) {
        Objects.requireNonNull(structType, "structType must not be null");
        
        // CRITICAL FIX: If this struct has array sizes in nestedStructArraySizes,
        // use those instead of the arraySizes parameter. This ensures that when
        // calculating size for nested structs (like Inner inside Middle), we use
        // the correct array sizes for the nested struct itself, not the parent struct.
        // We check if the struct tag exists in nestedStructArraySizes (even if empty),
        // because if it exists, it means we explicitly extracted array sizes for this struct.
        String structTag = structType.tag();
        if (structTag != null && nestedStructArraySizes != null && nestedStructArraySizes.containsKey(structTag)) {
            Map<String, Integer> nestedArraySizes = nestedStructArraySizes.get(structTag);
            // Use array sizes from nestedStructArraySizes for this struct (even if empty)
            // This ensures we use the correct array sizes for nested structs
            arraySizes = nestedArraySizes != null ? nestedArraySizes : new java.util.HashMap<>();
        }
        
        int totalSize = 0;
        for (Map.Entry<String, Type> field : structType.fields().entrySet()) {
            String fieldName = field.getKey();
            Type fieldType = TypeSystem.stripConst(field.getValue());
            
            // Handle arrays specially - ArrayType doesn't store size
            if (fieldType instanceof ArrayType arrayType) {
                int elementSize = calculateTypeSize(arrayType.elementType(), nestedStructArraySizes);
                Integer arrayLength = arraySizes != null ? arraySizes.get(fieldName) : null;
                if (arrayLength != null && arrayLength > 0) {
                    totalSize += elementSize * arrayLength;
                } else {
                    // Array size not provided - this is a limitation of the current type system
                    // For now, throw a helpful error
                    throw new IllegalArgumentException(
                        "Cannot calculate struct size: array field '" + fieldName + 
                        "' has unknown size. Array sizes must be provided via arraySizes parameter.");
                }
            } else {
                // For nested structs, use nestedStructArraySizes if available
                totalSize += calculateTypeSize(fieldType, nestedStructArraySizes);
            }
        }
        return totalSize;
    }
    
    /**
     * Calculates field offsets for a struct type.
     * 
     * <p>Returns a map from field name to byte offset from the start of the struct.
     * Fields are laid out in declaration order with no padding.
     * 
     * @param structType the struct type to calculate offsets for
     * @return a map from field name to byte offset (in declaration order)
     * @throws NullPointerException if structType is null
     */
    public static Map<String, Integer> calculateFieldOffsets(StructType structType) {
        return calculateFieldOffsets(structType, null);
    }
    
    /**
     * Calculates field offsets for a struct type, with optional array size information.
     * 
     * <p>Returns a map from field name to byte offset from the start of the struct.
     * Fields are laid out in declaration order with no padding.
     * 
     * <p>If array sizes are not provided, this method can still calculate offsets
     * for fields before arrays or the array field itself. It will fail only when
     * trying to calculate offsets for fields after arrays.
     * 
     * @param structType the struct type to calculate offsets for
     * @param arraySizes optional map from field name to array length (for array fields)
     * @return a map from field name to byte offset (in declaration order)
     * @throws NullPointerException if structType is null
     * @throws IllegalArgumentException if array sizes are needed but not provided
     */
    public static Map<String, Integer> calculateFieldOffsets(StructType structType, Map<String, Integer> arraySizes) {
        return calculateFieldOffsets(structType, arraySizes, null);
    }
    
    /**
     * Calculates field offsets for a struct type, with optional array size information for nested structs.
     * 
     * <p>This overload allows providing array sizes for nested structs that contain arrays.
     * The nestedStructArraySizes map should map struct tag names to their array sizes.
     * 
     * @param structType the struct type to calculate offsets for
     * @param arraySizes optional map from field name to array length (for array fields in this struct)
     * @param nestedStructArraySizes optional map from struct tag to array sizes map (for nested structs with arrays)
     * @return a map from field name to byte offset (in declaration order)
     * @throws NullPointerException if structType is null
     * @throws IllegalArgumentException if array sizes are needed but not provided
     */
    public static Map<String, Integer> calculateFieldOffsets(StructType structType, Map<String, Integer> arraySizes, 
                                                              Map<String, Map<String, Integer>> nestedStructArraySizes) {
        Objects.requireNonNull(structType, "structType must not be null");
        
        // CRITICAL FIX: If this struct has array sizes in nestedStructArraySizes,
        // use those instead of the arraySizes parameter. This ensures that when
        // calculating offsets for nested structs (like Inner inside Middle), we use
        // the correct array sizes for the nested struct itself, not the parent struct.
        // We check if the struct tag exists in nestedStructArraySizes (even if empty),
        // because if it exists, it means we explicitly extracted array sizes for this struct.
        String structTag = structType.tag();
        if (structTag != null && nestedStructArraySizes != null && nestedStructArraySizes.containsKey(structTag)) {
            Map<String, Integer> nestedArraySizes = nestedStructArraySizes.get(structTag);
            // Use array sizes from nestedStructArraySizes for this struct (even if empty)
            // This ensures we use the correct array sizes for nested structs
            arraySizes = nestedArraySizes != null ? nestedArraySizes : new java.util.HashMap<>();
        }
        
        Map<String, Integer> offsets = new LinkedHashMap<>();
        int currentOffset = 0;
        boolean encounteredArrayWithoutSize = false;
        
        for (Map.Entry<String, Type> field : structType.fields().entrySet()) {
            String fieldName = field.getKey();
            Type fieldType = TypeSystem.stripConst(field.getValue());
            
            offsets.put(fieldName, currentOffset);
            
            // Handle arrays specially - ArrayType doesn't store size
            if (fieldType instanceof ArrayType arrayType) {
                int elementSize = calculateTypeSize(arrayType.elementType(), nestedStructArraySizes);
                Integer arrayLength = arraySizes != null ? arraySizes.get(fieldName) : null;
                if (arrayLength != null && arrayLength > 0) {
                    currentOffset += elementSize * arrayLength;
                } else {
                    // Array size not provided - mark that we encountered an array
                    // We can still return offsets for fields up to this point
                    encounteredArrayWithoutSize = true;
                    // Don't increment currentOffset - we can't calculate offsets after this
                    // But we've already stored the offset for this field, which is fine
                }
            } else {
                // For non-array fields after an array without size, we can't calculate offset
                if (encounteredArrayWithoutSize) {
                    throw new IllegalArgumentException(
                        "Cannot calculate field offset for '" + fieldName + 
                        "': array field before it has unknown size. Array sizes must be provided via arraySizes parameter.");
                }
                // For nested structs, use nestedStructArraySizes if available
                currentOffset += calculateTypeSize(fieldType, nestedStructArraySizes);
            }
        }
        
        return offsets;
    }
    
    /**
     * Gets the offset of a specific field in a struct.
     * 
     * <p>This method calculates offsets without array size information.
     * If the struct contains arrays, this may fail for fields after arrays.
     * For structs with arrays, use {@link #getFieldOffset(StructType, String, Map)} instead.
     * 
     * @param structType the struct type
     * @param fieldName the field name
     * @return the byte offset of the field, or null if field doesn't exist
     * @throws NullPointerException if structType or fieldName is null
     * @throws IllegalArgumentException if struct contains arrays and array sizes are needed
     */
    public static Integer getFieldOffset(StructType structType, String fieldName) {
        return getFieldOffset(structType, fieldName, null);
    }
    
    /**
     * Gets the offset of a specific field in a struct, with optional array size information.
     * 
     * @param structType the struct type
     * @param fieldName the field name
     * @param arraySizes optional map from field name to array length (for array fields)
     * @return the byte offset of the field, or null if field doesn't exist
     * @throws NullPointerException if structType or fieldName is null
     */
    public static Integer getFieldOffset(StructType structType, String fieldName, Map<String, Integer> arraySizes) {
        return getFieldOffset(structType, fieldName, arraySizes, null);
    }
    
    /**
     * Gets the offset of a specific field in a struct, with optional array size information for nested structs.
     * 
     * @param structType the struct type
     * @param fieldName the field name
     * @param arraySizes optional map from field name to array length (for array fields in this struct)
     * @param nestedStructArraySizes optional map from struct tag to array sizes map (for nested structs with arrays)
     * @return the byte offset of the field, or null if field doesn't exist
     * @throws NullPointerException if structType or fieldName is null
     */
    public static Integer getFieldOffset(StructType structType, String fieldName, Map<String, Integer> arraySizes,
                                         Map<String, Map<String, Integer>> nestedStructArraySizes) {
        Objects.requireNonNull(structType, "structType must not be null");
        Objects.requireNonNull(fieldName, "fieldName must not be null");
        
        // CRITICAL FIX: If this struct has array sizes in nestedStructArraySizes,
        // use those instead of the arraySizes parameter. This ensures that when
        // calculating offsets for nested structs (like Inner inside Middle), we use
        // the correct array sizes for the nested struct itself, not the parent struct.
        // We check if the struct tag exists in nestedStructArraySizes (even if empty),
        // because if it exists, it means we explicitly extracted array sizes for this struct.
        String structTag = structType.tag();
        if (structTag != null && nestedStructArraySizes != null && nestedStructArraySizes.containsKey(structTag)) {
            Map<String, Integer> nestedArraySizes = nestedStructArraySizes.get(structTag);
            // Use array sizes from nestedStructArraySizes for this struct (even if empty)
            // This ensures we use the correct array sizes for nested structs
            arraySizes = nestedArraySizes != null ? nestedArraySizes : new java.util.HashMap<>();
        }
        
        Map<String, Integer> offsets = calculateFieldOffsets(structType, arraySizes, nestedStructArraySizes);
        return offsets.get(fieldName);
    }
    
    /**
     * Calculates the size of a type in bytes.
     * 
     * <p>This method handles all type kinds:
     * <ul>
     *   <li>Primitive types: char (1), int (4), float (4)</li>
     *   <li>Pointer types: 4 bytes</li>
     *   <li>Array types: element_size × array_length</li>
     *   <li>Struct types: recursively calculated</li>
     * </ul>
     * 
     * @param type the type to calculate size for
     * @return the size in bytes
     * @throws NullPointerException if type is null
     * @throws IllegalArgumentException if type is not supported
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
     * @param type the type to calculate size for
     * @param nestedStructArraySizes optional map from struct tag to array sizes map (for nested structs with arrays)
     * @return the size in bytes
     * @throws NullPointerException if type is null
     * @throws IllegalArgumentException if type is not supported
     */
    public static int calculateTypeSize(Type type, Map<String, Map<String, Integer>> nestedStructArraySizes) {
        Objects.requireNonNull(type, "type must not be null");
        
        Type stripped = TypeSystem.stripConst(type);
        
        if (stripped instanceof PrimitiveType primitiveType) {
            return switch (primitiveType) {
                case CHAR -> 4;  // char is 4 bytes (same as int and float)
                case INT -> 4;
                case FLOAT -> 4;
                case VOID -> throw new IllegalArgumentException("Cannot calculate size of void type");
            };
        } else if (stripped instanceof PointerType) {
            return 4; // Pointers are 4 bytes
        } else if (stripped instanceof ArrayType) {
            // ArrayType doesn't store size - size is stored in semantic attributes
            // For codegen purposes, we need to calculate size from element type
            // This is a limitation - we can't know array size from type alone
            // Arrays inside structs should have their size available from semantic attributes
            // For now, throw an exception - callers should provide size from semantic attributes
            throw new IllegalArgumentException("Cannot calculate array size from ArrayType alone - size must be provided from semantic attributes");
        } else if (stripped instanceof StructType structType) {
            // For nested structs, check if we have array sizes for this struct
            Map<String, Integer> arraySizes = null;
            if (nestedStructArraySizes != null) {
                String structTag = structType.tag();
                arraySizes = nestedStructArraySizes.get(structTag);
                // If not found, try to get empty map (struct might not have arrays, but we still need to check)
                if (arraySizes == null) {
                    arraySizes = new java.util.HashMap<>();
                }
            }
            // Pass nestedStructArraySizes through so deeper nested structs can also be calculated
            return calculateStructSize(structType, arraySizes, nestedStructArraySizes);
        } else {
            throw new IllegalArgumentException("Unsupported type for size calculation: " + stripped.getClass().getSimpleName());
        }
    }
}
