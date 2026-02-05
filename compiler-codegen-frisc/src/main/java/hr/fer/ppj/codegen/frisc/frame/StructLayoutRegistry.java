package hr.fer.ppj.codegen.frisc.frame;

import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrStructType;
import hr.fer.ppj.ir.types.IrType;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry for struct layouts and computed field offsets.
 *
 * <p>This class mirrors the IR struct layout assumptions used by codegen. When
 * layout info is missing, conservative defaults are used to preserve behavior.
 */
public final class StructLayoutRegistry {
  private final Map<String, StructLayout> layouts = new HashMap<>();

  public void register(IrProgramModel.StructDef def) {
    StructLayout layout = new StructLayout(def.name());
    for (IrProgramModel.StructField field : def.fields()) {
      layout.fields.put(field.name(), new StructFieldInfo(field.offset(), field.type()));
    }
    layouts.put(def.name(), layout);
  }

  public int getFieldOffset(String structName, String fieldName) {
    StructLayout layout = layouts.get(structName);
    if (layout == null) {
      layout = new StructLayout(structName);
      layouts.put(structName, layout);
    }
    StructFieldInfo field = layout.fields.get(fieldName);
    if (field == null) {
      int nextOffset = layout.nextOffset(this);
      field = new StructFieldInfo(nextOffset, IrPrimitiveType.INT32);
      layout.fields.put(fieldName, field);
    }
    return field.offset;
  }

  public int getStructSize(String structName) {
    StructLayout layout = layouts.get(structName);
    if (layout == null) {
      return 4;
    }
    return layout.computeSize(this);
  }

  private int sizeOf(IrType type) {
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
      return arr.size() * sizeOf(arr.elementType());
    }
    if (type instanceof IrStructType structType) {
      return getStructSize(structType.name());
    }
    return 4;
  }

  private int alignmentOf(IrType type) {
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

  private static int alignTo(int value, int alignment) {
    if (alignment <= 1) {
      return value;
    }
    int mod = value % alignment;
    if (mod == 0) {
      return value;
    }
    return value + (alignment - mod);
  }

  private static final class StructLayout {
    private final String name;
    private final Map<String, StructFieldInfo> fields = new LinkedHashMap<>();

    private StructLayout(String name) {
      this.name = name;
    }

    private int nextOffset(StructLayoutRegistry registry) {
      int offset = 0;
      for (StructFieldInfo field : fields.values()) {
        int align = registry.alignmentOf(field.type);
        offset = alignTo(offset, align);
        offset += registry.sizeOf(field.type);
      }
      return offset;
    }

    private int computeSize(StructLayoutRegistry registry) {
      int offset = 0;
      int maxAlign = 1;
      for (StructFieldInfo field : fields.values()) {
        int align = registry.alignmentOf(field.type);
        maxAlign = Math.max(maxAlign, align);
        offset = alignTo(offset, align);
        offset += registry.sizeOf(field.type);
      }
      return alignTo(offset, maxAlign);
    }
  }

  private record StructFieldInfo(int offset, IrType type) {
  }
}
