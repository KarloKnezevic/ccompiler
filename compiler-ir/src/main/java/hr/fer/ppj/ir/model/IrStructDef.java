package hr.fer.ppj.ir.model;

import hr.fer.ppj.ir.types.IrType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Struct type definition: .type struct Name { field1:type@offset, ... }.
 *
 * @param name the struct tag name
 * @param fields the struct fields with their types and offsets
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record IrStructDef(String name, Map<String, Field> fields) {

  public IrStructDef {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(fields, "fields must not be null");
    fields = Map.copyOf(fields);
  }

  /**
   * Struct field with type and offset.
   */
  public record Field(IrType type, int offset) {
    public Field {
      Objects.requireNonNull(type, "type must not be null");
      if (offset < 0) {
        throw new IllegalArgumentException("Offset must be non-negative");
      }
    }
  }

  /**
   * Creates a new struct definition builder.
   */
  public static Builder builder(String name) {
    return new Builder(name);
  }

  /**
   * Builder for constructing struct definitions incrementally.
   */
  public static final class Builder {
    private final String name;
    private final Map<String, Field> fields = new LinkedHashMap<>();

    private Builder(String name) {
      this.name = Objects.requireNonNull(name, "name must not be null");
    }

    public Builder addField(String fieldName, IrType fieldType, int offset) {
      fields.put(
          Objects.requireNonNull(fieldName, "fieldName must not be null"),
          new Field(
              Objects.requireNonNull(fieldType, "fieldType must not be null"), offset));
      return this;
    }

    public IrStructDef build() {
      return new IrStructDef(name, fields);
    }
  }
}

