package hr.fer.ppj.ir.model;

import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import java.util.Objects;

/**
 * Constant value: #42:int32, #'a':char, #3.14:float, null:ptr<int32>.
 *
 * <p>Constants can be integers, characters, floats, or null pointers.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public sealed interface IrConst extends IrValue {
  String toIrString();

  /**
   * Integer constant: #42:int32
   */
  record IntConst(int value, IrType type) implements IrConst {
    public IntConst {
      Objects.requireNonNull(type, "type must not be null");
    }

    @Override
    public String toIrString() {
      return "#" + value + ":" + type.toIrString();
    }
  }

  /**
   * Character constant: #'a':char
   */
  record CharConst(char value) implements IrConst {
    @Override
    public IrType type() {
      return IrPrimitiveType.CHAR;
    }

    @Override
    public String toIrString() {
      // Escape special characters
      return switch (value) {
        case '\n' -> "#'\\n':char";
        case '\t' -> "#'\\t':char";
        case '\'' -> "#'\\'':char";
        case '\\' -> "#'\\\\':char";
        default -> "#'" + value + "':char";
      };
    }
  }

  /**
   * Float constant: #3.14:float
   */
  record FloatConst(float value) implements IrConst {
    @Override
    public IrType type() {
      return IrPrimitiveType.FLOAT;
    }

    @Override
    public String toIrString() {
      // Format float without scientific notation if possible
      String str = Float.toString(value);
      if (str.contains("E") || str.contains("e")) {
        // Use printf-style formatting for scientific notation
        return String.format("#%.6f:float", value).replaceAll("0+$", "").replaceAll("\\.$", "");
      }
      return "#" + str + ":float";
    }
  }

  /**
   * Null pointer constant: null:ptr<T>
   */
  record NullConst(IrType type) implements IrConst {
    public NullConst {
      Objects.requireNonNull(type, "type must not be null");
      if (!(type instanceof hr.fer.ppj.ir.types.IrPointerType)) {
        throw new IllegalArgumentException("Null constant must have pointer type");
      }
    }

    @Override
    public String toIrString() {
      return "null:" + type.toIrString();
    }
  }

  /**
   * Array constant: { #'a':char, #'b':char, ... } : array<char,5>
   */
  record ArrayConst(java.util.List<IrConst> elements, hr.fer.ppj.ir.types.IrArrayType arrayType) implements IrConst {
    public ArrayConst {
      Objects.requireNonNull(elements, "elements must not be null");
      Objects.requireNonNull(arrayType, "arrayType must not be null");
    }

    @Override
    public IrType type() {
      return arrayType;
    }

    @Override
    public String toIrString() {
      StringBuilder sb = new StringBuilder();
      sb.append("{ ");
      for (int i = 0; i < elements.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(elements.get(i).toIrString());
      }
      sb.append(" } : ").append(arrayType.toIrString());
      return sb.toString();
    }
  }
}

