package hr.fer.ppj.ir.model;

import java.util.Objects;

/**
 * Symbol reference used in addr_of_symbol: local:x, param:x, global:x.
 *
 * <p>This is NOT a Value (only Temp and Const are Values).
 * SymbolRef is only used inside addr_of_symbol RHS operations.
 *
 * @param kind the kind of symbol (local, param, or global)
 * @param name the symbol name
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record IrSymbolRef(Kind kind, String name) {

  public IrSymbolRef {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(name, "name must not be null");
  }

  public enum Kind {
    LOCAL("local"),
    PARAM("param"),
    GLOBAL("global");

    private final String irString;

    Kind(String irString) {
      this.irString = irString;
    }

    public String toIrString() {
      return irString;
    }
  }

  public String toIrString() {
    return kind.toIrString() + ":" + name;
  }
}

