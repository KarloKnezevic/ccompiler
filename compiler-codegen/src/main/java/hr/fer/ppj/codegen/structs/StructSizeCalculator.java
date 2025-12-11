package hr.fer.ppj.codegen.structs;

import hr.fer.ppj.codegen.types.TypeSizeCalculator;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.Map;
import java.util.Objects;

/**
 * Calculates the total size of struct types in bytes.
 *
 * <p>This class provides methods to compute the memory size of struct types, including handling of
 * nested structs and arrays within structs. It implements the <b>struct size calculation
 * algorithm</b> used throughout code generation.
 *
 * <p><b>Algorithm: Struct Size Calculation</b>
 *
 * <p>The algorithm works as follows:
 *
 * <ol>
 *   <li><b>Field Iteration:</b> Iterate through fields in declaration order
 *   <li><b>Field Size Calculation:</b> Calculate size of each field's type:
 *       <ul>
 *         <li>Arrays: element_size × array_length (from arraySizes map)
 *         <li>Nested structs: recursively calculate struct size
 *         <li>Other types: delegate to {@link TypeSizeCalculator}
 *       </ul>
 *   <li><b>Total Size:</b> Sum of all field sizes (no padding, tightly packed)
 * </ol>
 *
 * <p><b>Layout Rules:</b>
 *
 * <ul>
 *   <li><b>No Padding:</b> Structs are tightly packed (fields laid out back-to-back)
 *   <li><b>Declaration Order:</b> Fields are laid out in declaration order
 *   <li><b>Nested Structs:</b> Nested structs are laid out inline (no special handling)
 *   <li><b>Arrays:</b> Arrays are laid out as contiguous sequences of elements
 * </ul>
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * struct Point {
 *     int x;      // size 4
 *     int y;      // size 4
 * };              // total size: 8 bytes
 *
 * struct Outer {
 *     struct Point p;  // size 8
 *     int arr[3];      // size 12 (3 * 4)
 *     char c;          // size 4 (char is 4 bytes in FRISC)
 * };                   // total size: 24 bytes
 * </pre>
 *
 * <p><b>Complexity Analysis:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(n) where n is the number of fields (including nested structs)
 *   <li><b>Space Complexity:</b> O(1) - uses only local variables
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class StructSizeCalculator {

  /** Private constructor to prevent instantiation. */
  private StructSizeCalculator() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Calculates the total size of a struct type in bytes.
   *
   * <p>This method recursively calculates the size by summing the sizes of all fields in
   * declaration order. It assumes the struct has no array fields.
   *
   * <p><b>Use Case:</b> Simple structs without arrays (e.g., {@code struct Point { int x; int y;
   * }})
   *
   * <p><b>Limitation:</b> If the struct contains array fields, this method will throw {@link
   * IllegalArgumentException}. Use the overload that accepts array sizes instead.
   *
   * @param structType the struct type to calculate size for
   * @return the total size in bytes
   * @throws NullPointerException if structType is null
   * @throws IllegalArgumentException if struct contains array fields (use overload with arraySizes)
   */
  public static int calculateStructSize(StructType structType) {
    return calculateStructSize(structType, null, null);
  }

  /**
   * Calculates the total size of a struct type in bytes, with optional array size information.
   *
   * <p>This overload allows providing array sizes for struct fields that are arrays. Array sizes
   * are provided as a map from field name to array length.
   *
   * <p><b>Use Case:</b> Structs with array fields (e.g., {@code struct Buffer { int data[10]; }})
   *
   * <p><b>Array Size Map Format:</b>
   *
   * <ul>
   *   <li>Key: Field name (e.g., "data")
   *   <li>Value: Array length (e.g., 10)
   * </ul>
   *
   * <p><b>Example:</b>
   *
   * <pre>
   * struct Buffer {
   *     int data[10];
   *     char name[20];
   * };
   *
   * Map&lt;String, Integer&gt; arraySizes = Map.of(
   *     "data", 10,
   *     "name", 20
   * );
   *
   * int size = StructSizeCalculator.calculateStructSize(bufferType, arraySizes);
   * // Result: 10 * 4 + 20 * 4 = 120 bytes
   * </pre>
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
   * Calculates the total size of a struct type in bytes, with optional array size information for
   * nested structs.
   *
   * <p>This overload allows providing array sizes for nested structs that contain arrays. The
   * nestedStructArraySizes map should map struct tag names to their array sizes.
   *
   * <p><b>Critical Design Decision:</b> If a struct tag exists in nestedStructArraySizes, those
   * array sizes are used instead of the arraySizes parameter. This ensures that when calculating
   * size for nested structs (like Inner inside Middle), we use the correct array sizes for the
   * nested struct itself, not the parent struct.
   *
   * @param structType the struct type to calculate size for
   * @param arraySizes optional map from field name to array length (for array fields in this
   *     struct)
   * @param nestedStructArraySizes optional map from struct tag to array sizes map (for nested
   *     structs with arrays)
   * @return the total size in bytes
   * @throws NullPointerException if structType is null
   * @throws IllegalArgumentException if struct contains array fields without size information
   */
  public static int calculateStructSize(
      StructType structType,
      Map<String, Integer> arraySizes,
      Map<String, Map<String, Integer>> nestedStructArraySizes) {
    Objects.requireNonNull(structType, "structType must not be null");

    // CRITICAL FIX: If this struct has array sizes in nestedStructArraySizes,
    // use those instead of the arraySizes parameter. This ensures that when
    // calculating size for nested structs (like Inner inside Middle), we use
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

    // Initialize total size accumulator
    int totalSize = 0;

    // Iterate through all fields in declaration order
    // Fields are laid out in memory in the order they are declared (no reordering)
    for (Map.Entry<String, Type> field : structType.fields().entrySet()) {
      String fieldName = field.getKey();
      Type fieldType = TypeSystem.stripConst(field.getValue());

      // Handle array fields specially - ArrayType doesn't store size information
      // Array sizes must be extracted from the parse tree and provided via arraySizes map
      if (fieldType instanceof ArrayType arrayType) {
        // Calculate element size (may be primitive, struct, or nested array)
        // For struct elements, nestedStructArraySizes is passed through for nested structs
        int elementSize =
            TypeSizeCalculator.calculateTypeSize(arrayType.elementType(), nestedStructArraySizes);

        // Look up array length from arraySizes map
        // The map structure is: fieldName -> arrayLength
        Integer arrayLength = arraySizes != null ? arraySizes.get(fieldName) : null;

        if (arrayLength != null && arrayLength > 0) {
          // Array size found - calculate total array size
          // Total array size = element_size × array_length
          totalSize += elementSize * arrayLength;
        } else {
          // Array size not provided - this is a limitation of the current type system
          // ArrayType doesn't preserve size information, so we must extract it from
          // the parse tree. If not provided, we cannot calculate the struct size.
          throw new IllegalArgumentException(
              "Cannot calculate struct size: array field '"
                  + fieldName
                  + "' has unknown size. Array sizes must be provided via arraySizes parameter. "
                  + "Use StructArraySizeExtractor to extract array sizes from the parse tree.");
        }
      } else {
        // Non-array field: calculate size using TypeSizeCalculator
        // For nested struct fields, nestedStructArraySizes is passed through so that
        // nested structs with arrays can also be calculated correctly
        // This handles cases like: Outer -> Middle -> Inner (all with arrays)
        totalSize += TypeSizeCalculator.calculateTypeSize(fieldType, nestedStructArraySizes);
      }
    }

    // Return total size (sum of all field sizes)
    return totalSize;
  }
}
