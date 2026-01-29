package hr.fer.ppj.semantics.types;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the memory layout of a struct type.
 *
 * <p>This class provides deterministic field ordering, offsets, size, and alignment
 * information for struct types. This is essential for IR generation, which needs to
 * emit field offsets in {@code addr_field} operations.
 *
 * <p>Layout computation follows standard C struct layout rules:
 * <ul>
 *   <li>Fields are stored in declaration order</li>
 *   <li>Each field is aligned to its natural alignment requirement</li>
 *   <li>Struct size is rounded up to the struct's alignment requirement</li>
 *   <li>Struct alignment is the maximum alignment of all fields</li>
 * </ul>
 *
 * <p>Type sizes and alignments (for 32-bit target):
 * <ul>
 *   <li>{@code char}: size=1, align=1</li>
 *   <li>{@code int}: size=4, align=4</li>
 *   <li>{@code float}: size=4, align=4</li>
 *   <li>{@code T*}: size=4, align=4 (pointer size)</li>
 *   <li>{@code array<T,n>}: size=n*sizeof(T), align=alignof(T)</li>
 *   <li>{@code struct S}: size and align from StructLayout</li>
 * </ul>
 *
 * <p>This class is immutable and can be safely shared.
 *
 * @param structTag the struct tag name (null for anonymous structs)
 * @param fields immutable map from field name to field layout information
 * @param size total size of the struct in bytes
 * @param alignment alignment requirement of the struct in bytes
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record StructLayout(
    String structTag,
    Map<String, FieldLayout> fields,
    int size,
    int alignment) {

  /**
   * Constructs a struct layout.
   *
   * @param structTag the struct tag name (null for anonymous structs)
   * @param fields map from field name to field layout (must not be null)
   * @param size total size of the struct in bytes (must be positive)
   * @param alignment alignment requirement in bytes (must be positive)
   * @throws NullPointerException if fields is null
   * @throws IllegalArgumentException if size or alignment is non-positive
   */
  public StructLayout {
    Objects.requireNonNull(fields, "fields must not be null");
    if (size <= 0) {
      throw new IllegalArgumentException("Struct size must be positive: " + size);
    }
    if (alignment <= 0) {
      throw new IllegalArgumentException("Struct alignment must be positive: " + alignment);
    }
    // Create defensive copy to ensure immutability
    fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
  }

  /**
   * Gets the offset of a field by name.
   *
   * @param fieldName the field name
   * @return the field offset in bytes, or empty if field doesn't exist
   */
  public Optional<Integer> getFieldOffset(String fieldName) {
    FieldLayout field = fields.get(fieldName);
    return field != null ? Optional.of(field.offset()) : Optional.empty();
  }

  /**
   * Gets the type of a field by name.
   *
   * @param fieldName the field name
   * @return the field type, or empty if field doesn't exist
   */
  public Optional<Type> getFieldType(String fieldName) {
    FieldLayout field = fields.get(fieldName);
    return field != null ? Optional.of(field.type()) : Optional.empty();
  }

  /**
   * Checks if a field exists in this struct.
   *
   * @param fieldName the field name
   * @return true if the field exists, false otherwise
   */
  public boolean hasField(String fieldName) {
    return fields.containsKey(fieldName);
  }

  /**
   * Represents the layout of a single struct field.
   *
   * @param name the field name
   * @param type the field type
   * @param offset the byte offset of the field within the struct
   * @param size the size of the field in bytes
   * @param alignment the alignment requirement of the field in bytes
   */
  public record FieldLayout(
      String name,
      Type type,
      int offset,
      int size,
      int alignment) {

    /**
     * Constructs a field layout.
     *
     * @param name the field name (must not be null)
     * @param type the field type (must not be null)
     * @param offset the byte offset (must be non-negative)
     * @param size the size in bytes (must be positive)
     * @param alignment the alignment in bytes (must be positive)
     */
    public FieldLayout {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(type, "type must not be null");
      if (offset < 0) {
        throw new IllegalArgumentException("Field offset must be non-negative: " + offset);
      }
      if (size <= 0) {
        throw new IllegalArgumentException("Field size must be positive: " + size);
      }
      if (alignment <= 0) {
        throw new IllegalArgumentException("Field alignment must be positive: " + alignment);
      }
    }
  }

  /**
   * Computes the layout for a struct type.
   *
   * <p>This method computes field offsets, struct size, and alignment according to
   * standard C struct layout rules.
   *
   * @param structType the struct type to compute layout for
   * @param typeSizeCalculator function to compute size of a type
   * @param typeAlignmentCalculator function to compute alignment of a type
   * @return the computed struct layout
   * @throws NullPointerException if any argument is null
   */
  public static StructLayout compute(
      StructType structType,
      TypeSizeCalculator typeSizeCalculator,
      TypeAlignmentCalculator typeAlignmentCalculator) {
    Objects.requireNonNull(structType, "structType must not be null");
    Objects.requireNonNull(typeSizeCalculator, "typeSizeCalculator must not be null");
    Objects.requireNonNull(typeAlignmentCalculator, "typeAlignmentCalculator must not be null");

    Map<String, FieldLayout> fieldLayouts = new LinkedHashMap<>();
    int currentOffset = 0;
    int maxAlignment = 1; // Minimum alignment is 1

    // Process fields in declaration order
    for (Map.Entry<String, Type> entry : structType.fields().entrySet()) {
      String fieldName = entry.getKey();
      Type fieldType = entry.getValue();

      int fieldSize = typeSizeCalculator.getSize(fieldType);
      int fieldAlign = typeAlignmentCalculator.getAlignment(fieldType);
      maxAlignment = Math.max(maxAlignment, fieldAlign);

      // Align current offset to field alignment
      currentOffset = alignUp(currentOffset, fieldAlign);

      FieldLayout fieldLayout = new FieldLayout(fieldName, fieldType, currentOffset, fieldSize, fieldAlign);
      fieldLayouts.put(fieldName, fieldLayout);

      currentOffset += fieldSize;
    }

    // Align struct size to struct alignment
    int structSize = alignUp(currentOffset, maxAlignment);

    return new StructLayout(structType.tag(), fieldLayouts, structSize, maxAlignment);
  }

  /**
   * Aligns a value up to the nearest multiple of alignment.
   *
   * @param value the value to align
   * @param alignment the alignment requirement (must be positive)
   * @return the aligned value
   */
  private static int alignUp(int value, int alignment) {
    if (alignment <= 0) {
      throw new IllegalArgumentException("Alignment must be positive: " + alignment);
    }
    return ((value + alignment - 1) / alignment) * alignment;
  }

  /**
   * Functional interface for computing type sizes.
   */
  @FunctionalInterface
  public interface TypeSizeCalculator {
    /**
     * Gets the size of a type in bytes.
     *
     * @param type the type
     * @return the size in bytes (must be positive)
     */
    int getSize(Type type);
  }

  /**
   * Functional interface for computing type alignments.
   */
  @FunctionalInterface
  public interface TypeAlignmentCalculator {
    /**
     * Gets the alignment requirement of a type in bytes.
     *
     * @param type the type
     * @return the alignment in bytes (must be positive)
     */
    int getAlignment(Type type);
  }
}

