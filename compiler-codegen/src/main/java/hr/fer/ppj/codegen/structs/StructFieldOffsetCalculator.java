package hr.fer.ppj.codegen.structs;

import hr.fer.ppj.codegen.types.TypeSizeCalculator;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Calculates field offsets for struct types.
 *
 * <p>This class provides methods to compute the byte offsets of fields within struct types,
 * including handling of nested structs and arrays within structs. It implements the <b>struct field
 * offset calculation algorithm</b> used throughout code generation.
 *
 * <p><b>Algorithm: Field Offset Calculation</b>
 *
 * <p>The algorithm works as follows:
 *
 * <ol>
 *   <li><b>Field Iteration:</b> Iterate through fields in declaration order
 *   <li><b>Offset Assignment:</b> Assign offset = current_offset, then increment by field size
 *   <li><b>Field Size Calculation:</b> Calculate size of each field's type:
 *       <ul>
 *         <li>Arrays: element_size × array_length (from arraySizes map)
 *         <li>Nested structs: recursively calculate struct size
 *         <li>Other types: delegate to {@link TypeSizeCalculator}
 *       </ul>
 * </ol>
 *
 * <p><b>Layout Rules:</b>
 *
 * <ul>
 *   <li><b>No Padding:</b> Fields are laid out back-to-back with no padding
 *   <li><b>Declaration Order:</b> Fields are laid out in declaration order
 *   <li><b>Nested Structs:</b> Nested structs are laid out inline (no special handling)
 *   <li><b>Arrays:</b> Arrays are laid out as contiguous sequences of elements
 * </ul>
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * struct Point {
 *     int x;      // offset 0, size 4
 *     int y;      // offset 4, size 4
 * };              // total size: 8 bytes
 *
 * struct Outer {
 *     struct Point p;  // offset 0, size 8
 *     int arr[3];      // offset 8, size 12
 *     char c;          // offset 20, size 4
 * };                   // total size: 24 bytes
 * </pre>
 *
 * <p><b>Complexity Analysis:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(n) where n is the number of fields (including nested structs)
 *   <li><b>Space Complexity:</b> O(n) for storing field offsets
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class StructFieldOffsetCalculator {

  /** Private constructor to prevent instantiation. */
  private StructFieldOffsetCalculator() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Calculates field offsets for a struct type.
   *
   * <p>Returns a map from field name to byte offset from the start of the struct. Fields are laid
   * out in declaration order with no padding.
   *
   * @param structType the struct type to calculate offsets for
   * @return a map from field name to byte offset (in declaration order)
   * @throws NullPointerException if structType is null
   */
  public static Map<String, Integer> calculateFieldOffsets(StructType structType) {
    return calculateFieldOffsets(structType, null, null);
  }

  /**
   * Calculates field offsets for a struct type, with optional array size information.
   *
   * <p>Returns a map from field name to byte offset from the start of the struct. Fields are laid
   * out in declaration order with no padding.
   *
   * <p>If array sizes are not provided, this method can still calculate offsets for fields before
   * arrays or the array field itself. It will fail only when trying to calculate offsets for fields
   * after arrays.
   *
   * @param structType the struct type to calculate offsets for
   * @param arraySizes optional map from field name to array length (for array fields)
   * @return a map from field name to byte offset (in declaration order)
   * @throws NullPointerException if structType is null
   * @throws IllegalArgumentException if array sizes are needed but not provided
   */
  public static Map<String, Integer> calculateFieldOffsets(
      StructType structType, Map<String, Integer> arraySizes) {
    return calculateFieldOffsets(structType, arraySizes, null);
  }

  /**
   * Calculates field offsets for a struct type, with optional array size information for nested
   * structs.
   *
   * <p>This overload allows providing array sizes for nested structs that contain arrays. The
   * nestedStructArraySizes map should map struct tag names to their array sizes.
   *
   * <p><b>Critical Design Decision:</b> If a struct tag exists in nestedStructArraySizes, those
   * array sizes are used instead of the arraySizes parameter. This ensures that when calculating
   * offsets for nested structs (like Inner inside Middle), we use the correct array sizes for the
   * nested struct itself, not the parent struct.
   *
   * @param structType the struct type to calculate offsets for
   * @param arraySizes optional map from field name to array length (for array fields in this
   *     struct)
   * @param nestedStructArraySizes optional map from struct tag to array sizes map (for nested
   *     structs with arrays)
   * @return a map from field name to byte offset (in declaration order)
   * @throws NullPointerException if structType is null
   * @throws IllegalArgumentException if array sizes are needed but not provided
   */
  public static Map<String, Integer> calculateFieldOffsets(
      StructType structType,
      Map<String, Integer> arraySizes,
      Map<String, Map<String, Integer>> nestedStructArraySizes) {
    Objects.requireNonNull(structType, "structType must not be null");

    // CRITICAL FIX: If this struct has array sizes in nestedStructArraySizes,
    // use those instead of the arraySizes parameter. This ensures that when
    // calculating offsets for nested structs (like Inner inside Middle), we use
    // the correct array sizes for the nested struct itself, not the parent struct.
    // We check if the struct tag exists in nestedStructArraySizes (even if empty),
    // because if it exists, it means we explicitly extracted array sizes for this struct.
    String structTag = structType.tag();
    if (structTag != null
        && nestedStructArraySizes != null
        && nestedStructArraySizes.containsKey(structTag)) {
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
        int elementSize =
            TypeSizeCalculator.calculateTypeSize(arrayType.elementType(), nestedStructArraySizes);
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
              "Cannot calculate field offset for '"
                  + fieldName
                  + "': array field before it has unknown size. Array sizes must be provided via arraySizes parameter.");
        }
        // For nested structs, use nestedStructArraySizes if available
        currentOffset += TypeSizeCalculator.calculateTypeSize(fieldType, nestedStructArraySizes);
      }
    }

    return offsets;
  }

  /**
   * Gets the offset of a specific field in a struct.
   *
   * <p>This method calculates offsets without array size information. If the struct contains
   * arrays, this may fail for fields after arrays. For structs with arrays, use {@link
   * #getFieldOffset(StructType, String, Map)} instead.
   *
   * @param structType the struct type
   * @param fieldName the field name
   * @return the byte offset of the field, or null if field doesn't exist
   * @throws NullPointerException if structType or fieldName is null
   * @throws IllegalArgumentException if struct contains arrays and array sizes are needed
   */
  public static Integer getFieldOffset(StructType structType, String fieldName) {
    return getFieldOffset(structType, fieldName, null, null);
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
  public static Integer getFieldOffset(
      StructType structType, String fieldName, Map<String, Integer> arraySizes) {
    return getFieldOffset(structType, fieldName, arraySizes, null);
  }

  /**
   * Gets the offset of a specific field in a struct, with optional array size information for
   * nested structs.
   *
   * @param structType the struct type
   * @param fieldName the field name
   * @param arraySizes optional map from field name to array length (for array fields in this
   *     struct)
   * @param nestedStructArraySizes optional map from struct tag to array sizes map (for nested
   *     structs with arrays)
   * @return the byte offset of the field, or null if field doesn't exist
   * @throws NullPointerException if structType or fieldName is null
   */
  public static Integer getFieldOffset(
      StructType structType,
      String fieldName,
      Map<String, Integer> arraySizes,
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
    if (structTag != null
        && nestedStructArraySizes != null
        && nestedStructArraySizes.containsKey(structTag)) {
      Map<String, Integer> nestedArraySizes = nestedStructArraySizes.get(structTag);
      // Use array sizes from nestedStructArraySizes for this struct (even if empty)
      // This ensures we use the correct array sizes for nested structs
      arraySizes = nestedArraySizes != null ? nestedArraySizes : new java.util.HashMap<>();
    }

    Map<String, Integer> offsets =
        calculateFieldOffsets(structType, arraySizes, nestedStructArraySizes);
    return offsets.get(fieldName);
  }
}
