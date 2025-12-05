package hr.fer.ppj.semantics.types;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a struct type with named fields.
 *
 * <p>Structs in PPJ-C can be either:
 * <ul>
 *   <li><strong>Tagged structs</strong>: {@code struct Tag { ... } } - can be forward
 *       declared and referenced by tag name</li>
 *   <li><strong>Anonymous structs</strong>: {@code struct { ... } } - cannot be referenced
 *       later, used only for immediate variable declarations</li>
 * </ul>
 *
 * <p>Struct semantics:
 * <ul>
 *   <li><strong>Not scalar</strong>: Structs cannot be used in arithmetic operations</li>
 *   <li><strong>Field access</strong>: Fields are accessed using dot notation: {@code struct.field}</li>
 *   <li><strong>Assignment</strong>: Structs can be assigned if they have the same type
 *       (tagged structs compared by tag, anonymous structs by structure)</li>
 *   <li><strong>Self-reference</strong>: Structs can contain pointers to themselves,
 *       enabling recursive data structures (e.g., linked lists, trees)</li>
 * </ul>
 *
 * <p>Forward declarations:
 * <ul>
 *   <li>Tagged structs can be forward declared with an empty field list</li>
 *   <li>This allows self-referential structures: {@code struct Node { struct Node *next; } }</li>
 *   <li>The forward declaration is replaced with the full definition when fields are processed</li>
 * </ul>
 *
 * <p>Field types:
 * <ul>
 *   <li>Fields can be any type: primitives, arrays, pointers, other structs</li>
 *   <li>Field names must be unique within a struct</li>
 *   <li>Fields are stored in declaration order (using {@link LinkedHashMap})</li>
 * </ul>
 *
 * <p>Type equality:
 * <ul>
 *   <li>Tagged structs: Compared by tag name (enables forward declarations)</li>
 *   <li>Anonymous structs: Compared by field structure (field names and types must match)</li>
 * </ul>
 *
 * @param tag the struct tag name, or {@code null} for anonymous structs
 * @param fields immutable map from field name to field type. Field order is preserved.
 *
 * @see Type for the base type interface
 * @see TypeSystem for struct type compatibility checking
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record StructType(String tag, Map<String, Type> fields) implements Type {

  /**
   * Constructs a struct type.
   *
   * <p>Creates a defensive copy of the fields map to ensure immutability.
   *
   * @param tag the struct tag name, or {@code null} for anonymous structs
   * @param fields map from field name to field type (must not be null)
   * @throws NullPointerException if fields is null
   */
  public StructType {
    Objects.requireNonNull(fields, "fields must not be null");
    // Create a defensive copy to ensure immutability
    fields = new LinkedHashMap<>(fields);
  }

  /**
   * Creates an anonymous struct type with the given fields.
   *
   * <p>Anonymous structs cannot be referenced later and are used only for immediate
   * variable declarations.
   *
   * @param fields map from field name to field type (must not be null)
   * @throws NullPointerException if fields is null
   */
  public StructType(Map<String, Type> fields) {
    this(null, fields);
  }

  /**
   * Gets the type of a field by name.
   *
   * <p>This method is used during semantic analysis to validate field access expressions
   * like {@code struct.field}.
   *
   * @param fieldName the name of the field to look up
   * @return the type of the field, or {@code null} if the field doesn't exist
   */
  public Type getFieldType(String fieldName) {
    return fields.get(fieldName);
  }

  /**
   * Checks if this struct has a field with the given name.
   *
   * <p>This method is used during semantic analysis to validate field access expressions
   * and report errors for non-existent fields.
   *
   * @param fieldName the name of the field to check
   * @return {@code true} if the field exists, {@code false} otherwise
   */
  public boolean hasField(String fieldName) {
    return fields.containsKey(fieldName);
  }

  /**
   * Structs are not scalar types and cannot be used in arithmetic operations.
   *
   * @return {@code false} - structs are never scalar
   */
  @Override
  public boolean isScalar() {
    return false; // Structs are not scalar types
  }

  /**
   * Structs are never the void type.
   *
   * @return {@code false} - structs are never void
   */
  @Override
  public boolean isVoid() {
    return false;
  }
}

