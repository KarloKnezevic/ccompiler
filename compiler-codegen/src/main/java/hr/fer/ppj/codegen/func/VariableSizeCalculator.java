package hr.fer.ppj.codegen.func;

import hr.fer.ppj.codegen.structs.NestedStructArraySizeExtractor;
import hr.fer.ppj.codegen.structs.StructArraySizeExtractor;
import hr.fer.ppj.codegen.structs.StructSizeCalculator;
import hr.fer.ppj.codegen.types.TypeSizeCalculator;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.Map;
import java.util.Objects;

/**
 * Calculates the size in bytes of variables based on their types.
 *
 * <p>This utility class handles size calculation for all variable types:
 *
 * <ul>
 *   <li><b>Primitive types:</b> char (1), int (4), float (4)
 *   <li><b>Array types:</b> element_size × array_length
 *   <li><b>Struct types:</b> Calculated using {@link StructSizeCalculator}
 *   <li><b>Pointer types:</b> 4 bytes
 * </ul>
 *
 * <p><b>Purpose:</b>
 *
 * <p>When processing local variable declarations, we need to know the size of each variable to
 * allocate the correct amount of stack space. This class provides a centralized way to calculate
 * variable sizes, handling the complexity of:
 *
 * <ul>
 *   <li>Structs with array fields (requires array size extraction)
 *   <li>Nested structs with arrays (requires recursive extraction)
 *   <li>Arrays of structs (requires struct size calculation)
 * </ul>
 *
 * <p><b>Struct Size Calculation:</b>
 *
 * <p>For struct types, this class:
 *
 * <ol>
 *   <li>Extracts array sizes for struct fields (if the struct has array fields)
 *   <li>Recursively extracts array sizes for nested structs
 *   <li>Calculates the total struct size using {@link StructSizeCalculator}
 * </ol>
 *
 * <p><b>Array Size Calculation:</b>
 *
 * <p>For array types:
 *
 * <ul>
 *   <li>If array size is provided: {@code arraySize × elementSize}
 *   <li>If array size is 0: Uses type system to calculate element size
 * </ul>
 *
 * <p><b>Fallback Behavior:</b>
 *
 * <p>If type information is not available:
 *
 * <ul>
 *   <li>Arrays: {@code arraySize × elementSize} (default elementSize = 4)
 *   <li>Non-arrays: 4 bytes (default for scalars)
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class VariableSizeCalculator {

  private final StructArraySizeExtractor arraySizeExtractor;

  /**
   * Creates a new variable size calculator.
   *
   * @param arraySizeExtractor the extractor for array sizes in structs (may be null)
   */
  public VariableSizeCalculator(StructArraySizeExtractor arraySizeExtractor) {
    this.arraySizeExtractor = arraySizeExtractor;
  }

  /**
   * Calculates the size in bytes of a variable based on its type.
   *
   * <p>This method handles:
   *
   * <ul>
   *   <li>Primitive types: char (1), int (4), float (4)
   *   <li>Array types: element_size × array_length
   *   <li>Struct types: calculated using {@link StructSizeCalculator}
   *   <li>Pointer types: 4 bytes
   * </ul>
   *
   * <p>If type is null, falls back to default sizes:
   *
   * <ul>
   *   <li>Arrays: arraySize × elementSize
   *   <li>Non-arrays: 4 bytes
   * </ul>
   *
   * @param variableType the variable type (from semantic attributes, may be null)
   * @param arraySize the array size (if array, 0 otherwise)
   * @param elementSize the element size in bytes (fallback, always 4 for this project)
   * @return the size in bytes
   * @throws NullPointerException if arraySize or elementSize are negative (but allows null
   *     variableType)
   */
  public int calculateSize(Type variableType, int arraySize, int elementSize) {
    Objects.requireNonNull(
        arraySize >= 0 ? Integer.valueOf(arraySize) : null, "arraySize must not be negative");
    Objects.requireNonNull(
        elementSize > 0 ? Integer.valueOf(elementSize) : null, "elementSize must be positive");

    if (variableType == null) {
      // Fallback: use default sizes
      if (arraySize > 0) {
        return arraySize * elementSize;
      }
      return 4; // Default for simple variables
    }

    Type strippedType = TypeSystem.stripConst(variableType);

    if (strippedType instanceof StructType structType) {
      // Struct type: calculate struct size
      // For structs with array fields, extract array sizes from struct definition
      // Also extract array sizes for nested structs that contain arrays (recursively)
      String structTag = structType.tag();
      Map<String, Integer> arraySizes = null;
      Map<String, Map<String, Integer>> nestedStructArraySizes = null;

      if (arraySizeExtractor != null) {
        arraySizes = arraySizeExtractor.extractArraySizes(structTag);

        // Extract array sizes for nested struct fields (recursively)
        // CRITICAL: Must recursively extract for ALL nested structs at ALL levels
        // This ensures we get array sizes for Inner when processing Outer
        nestedStructArraySizes = new java.util.HashMap<>();
        NestedStructArraySizeExtractor.extractNestedStructArraySizes(
            structType, arraySizeExtractor, nestedStructArraySizes);
      }

      int structSize =
          StructSizeCalculator.calculateStructSize(structType, arraySizes, nestedStructArraySizes);

      if (arraySize > 0) {
        // Array of structs: structSize × arraySize
        return structSize * arraySize;
      } else {
        // Single struct
        return structSize;
      }
    } else if (strippedType instanceof ArrayType arrayType) {
      // Array type: calculate element size × array size
      Type elementType = arrayType.elementType();
      Type strippedElementType = TypeSystem.stripConst(elementType);

      // Check if element type is a struct (array of structs)
      if (strippedElementType instanceof StructType structElementType) {
        // Array of structs: need to calculate struct size first
        String structTag = structElementType.tag();
        Map<String, Integer> arraySizes = null;
        Map<String, Map<String, Integer>> nestedStructArraySizes = null;

        if (arraySizeExtractor != null) {
          arraySizes = arraySizeExtractor.extractArraySizes(structTag);
          nestedStructArraySizes = new java.util.HashMap<>();
          NestedStructArraySizeExtractor.extractNestedStructArraySizes(
              structElementType, arraySizeExtractor, nestedStructArraySizes);
        }

        int structSize =
            StructSizeCalculator.calculateStructSize(
                structElementType, arraySizes, nestedStructArraySizes);

        if (arraySize > 0) {
          // Use provided array size
          return structSize * arraySize;
        } else {
          // Array size not provided - can't determine size without array size
          // Return struct size as fallback (at least one element)
          return structSize;
        }
      } else {
        // Array of primitive types
        int elemSize = TypeSizeCalculator.calculateTypeSize(strippedElementType);
        if (arraySize > 0) {
          return elemSize * arraySize;
        } else {
          // Array size not provided - use provided elementSize as fallback
          return elementSize;
        }
      }
    } else {
      // Primitive or pointer type
      int baseSize = TypeSizeCalculator.calculateTypeSize(strippedType);
      if (arraySize > 0) {
        // Array of primitives
        return baseSize * arraySize;
      } else {
        // Single primitive or pointer
        return baseSize;
      }
    }
  }
}
