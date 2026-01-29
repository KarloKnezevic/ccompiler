package hr.fer.ppj.ir.model;

import hr.fer.ppj.ir.types.IrType;
import java.util.Objects;

/**
 * Slot entry in function frame: param/local/spill name@offset:type.
 *
 * @param kind the slot kind (param, local, or spill)
 * @param name the slot name
 * @param offset the byte offset in the frame
 * @param type the slot type
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record IrSlot(Kind kind, String name, int offset, IrType type) {

  public IrSlot {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(type, "type must not be null");
    if (offset < 0) {
      throw new IllegalArgumentException("Offset must be non-negative");
    }
  }

  public enum Kind {
    PARAM("param"),
    LOCAL("local"),
    SPILL("spill");

    private final String irString;

    Kind(String irString) {
      this.irString = irString;
    }

    public String toIrString() {
      return irString;
    }
  }
}

