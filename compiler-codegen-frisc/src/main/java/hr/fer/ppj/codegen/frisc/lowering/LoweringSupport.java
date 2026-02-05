package hr.fer.ppj.codegen.frisc.lowering;

import hr.fer.ppj.codegen.frisc.frame.StructLayoutRegistry;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrStructType;
import hr.fer.ppj.ir.types.IrType;
import java.util.Locale;

/**
 * Shared helper utilities for FRISC lowering.
 */
public final class LoweringSupport {

  private LoweringSupport() {
  }

  public static boolean isChar(IrType type) {
    return type == IrPrimitiveType.CHAR || type == IrPrimitiveType.UCHAR;
  }

  public static boolean isFloat(IrType type) {
    return type == IrPrimitiveType.FLOAT;
  }

  public static boolean isAggregate(IrType type) {
    return type instanceof IrArrayType || type instanceof IrStructType;
  }

  public static int floatToQ16_16(float value) {
    return Math.round(value * 65536.0f);
  }

  public static int sizeOf(IrType type, StructLayoutRegistry structLayouts) {
    if (type == null) {
      return 0;
    }
    if (type instanceof IrPrimitiveType prim) {
      return switch (prim) {
        case INT32, FLOAT, BOOL -> 4;
        case CHAR, UCHAR -> 1;
      };
    }
    if (type instanceof IrPointerType) {
      return 4;
    }
    if (type instanceof IrArrayType arr) {
      return arr.size() * sizeOf(arr.elementType(), structLayouts);
    }
    if (type instanceof IrStructType structType) {
      return structLayouts.getStructSize(structType.name());
    }
    return 4;
  }

  public static int alignmentOf(IrType type) {
    if (type == null) {
      return 1;
    }
    if (type instanceof IrPrimitiveType prim) {
      return switch (prim) {
        case INT32, FLOAT, BOOL -> 4;
        case CHAR, UCHAR -> 1;
      };
    }
    if (type instanceof IrPointerType) {
      return 4;
    }
    if (type instanceof IrArrayType arr) {
      return alignmentOf(arr.elementType());
    }
    return 4;
  }

  public static int alignTo(int value, int alignment) {
    if (alignment <= 1) {
      return value;
    }
    int mod = value % alignment;
    if (mod == 0) {
      return value;
    }
    return value + (alignment - mod);
  }

  public static String formatImmediate(int value) {
    if (value == 0) {
      return "0";
    }
    if (value < 0) {
      return "-" + formatHexMagnitude(-value);
    }
    return formatHexMagnitude(value);
  }

  public static boolean fitsSigned20(int value) {
    return value >= -0x80000 && value <= 0x7FFFF;
  }

  private static String formatHexMagnitude(int value) {
    String hex = Integer.toHexString(value).toUpperCase(Locale.ROOT);
    char first = hex.charAt(0);
    if (first >= '0' && first <= '9') {
      return hex;
    }
    return "0" + hex;
  }
}
