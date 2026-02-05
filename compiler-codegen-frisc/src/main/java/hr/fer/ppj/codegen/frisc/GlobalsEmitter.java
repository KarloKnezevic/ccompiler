package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.frisc.analysis.Scratch;
import hr.fer.ppj.codegen.frisc.emitter.FriscEmitter;
import hr.fer.ppj.codegen.frisc.frame.StructLayoutRegistry;
import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.codegen.frisc.lowering.LoweringSupport;
import hr.fer.ppj.codegen.frisc.util.LabelGenerator;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrStructType;
import hr.fer.ppj.ir.types.IrType;
import java.util.ArrayList;
import java.util.List;

/**
 * Emits global data declarations and auxiliary scratch data.
 */
final class GlobalsEmitter {

  private final LabelGenerator labelGenerator;

  GlobalsEmitter(LabelGenerator labelGenerator) {
    this.labelGenerator = labelGenerator;
  }

  void emitGlobals(List<IrProgramModel.GlobalVar> globals, FriscEmitter emitter, StructLayoutRegistry structLayouts) {
    for (IrProgramModel.GlobalVar global : globals) {
      String label = labelGenerator.globalLabel(global.name());
      IrType type = global.type();
      IrConst initializer = global.initializer();

      if (type instanceof IrArrayType arrayType) {
        emitGlobalArray(label, arrayType, initializer, emitter, structLayouts);
      } else if (type instanceof IrStructType structType) {
        emitGlobalStruct(label, structType, initializer, emitter, structLayouts);
      } else {
        emitGlobalScalar(label, type, initializer, emitter);
      }
    }
  }

  void emitPointerScratch(List<Scratch> scratches, FriscEmitter emitter) {
    for (Scratch scratch : scratches) {
      emitter.emitData(scratch.label(), "`DS", String.valueOf(scratch.size()),
          scratch.comment(), scratch.size(), scratch.alignment());
    }
  }

  private void emitGlobalScalar(String label, IrType type, IrConst initializer, FriscEmitter emitter) {
    if (initializer == null) {
      if (LoweringSupport.isChar(type)) {
        emitter.emitData(label, "DB", "0", "char", 1, LoweringSupport.alignmentOf(type));
      } else {
        emitter.emitData(label, "DW", "0", "scalar", 4, LoweringSupport.alignmentOf(type));
      }
      return;
    }

    if (initializer instanceof IrConst.IntConst intConst) {
      emitter.emitData(label, "DW", LoweringSupport.formatImmediate(intConst.value()), "int", 4, LoweringSupport.alignmentOf(type));
      return;
    }
    if (initializer instanceof IrConst.CharConst charConst) {
      emitter.emitData(label, "DB", LoweringSupport.formatImmediate(charConst.value()), "char", 1, LoweringSupport.alignmentOf(type));
      return;
    }
    if (initializer instanceof IrConst.FloatConst floatConst) {
      int raw = LoweringSupport.floatToQ16_16(floatConst.value());
      emitter.emitData(label, "DW", LoweringSupport.formatImmediate(raw), "float", 4, LoweringSupport.alignmentOf(type));
      return;
    }
    if (initializer instanceof IrConst.NullConst) {
      emitter.emitData(label, "DW", "0", "null", 4, LoweringSupport.alignmentOf(type));
      return;
    }

    throw new CodeGenerationException("Unsupported global initializer: " + initializer);
  }

  private void emitGlobalStruct(
      String label,
      IrStructType structType,
      IrConst initializer,
      FriscEmitter emitter,
      StructLayoutRegistry structLayouts) {
    int size = LoweringSupport.sizeOf(structType, structLayouts);
    if (initializer == null) {
      emitter.emitData(label, "`DS", String.valueOf(size), "struct", size, LoweringSupport.alignmentOf(structType));
      return;
    }
    throw new CodeGenerationException("Unsupported struct initializer for " + label);
  }

  private void emitGlobalArray(
      String label,
      IrArrayType arrayType,
      IrConst initializer,
      FriscEmitter emitter,
      StructLayoutRegistry structLayouts) {

    int elemSize = LoweringSupport.sizeOf(arrayType.elementType(), structLayouts);
    int totalSize = arrayType.size() * elemSize;

    if (initializer == null) {
      emitter.emitData(label, "`DS", String.valueOf(totalSize), "uninitialized array", totalSize,
          LoweringSupport.alignmentOf(arrayType.elementType()));
      return;
    }

    if (!(initializer instanceof IrConst.ArrayConst arrayConst)) {
      throw new CodeGenerationException("Array initializer mismatch for " + label);
    }

    if (LoweringSupport.isChar(arrayType.elementType())) {
      List<String> bytes = new ArrayList<>();
      for (IrConst element : arrayConst.elements()) {
        if (element instanceof IrConst.CharConst charConst) {
          bytes.add(LoweringSupport.formatImmediate(charConst.value()));
        } else if (element instanceof IrConst.IntConst intConst) {
          bytes.add(LoweringSupport.formatImmediate(intConst.value()));
        } else {
          throw new CodeGenerationException("Invalid char array element: " + element);
        }
      }
      emitter.emitData(label, "DB", String.join(", ", bytes), "char array", bytes.size(),
          LoweringSupport.alignmentOf(arrayType.elementType()));
      return;
    }

    List<String> words = new ArrayList<>();
    for (IrConst element : arrayConst.elements()) {
      if (element instanceof IrConst.IntConst intConst) {
        words.add(LoweringSupport.formatImmediate(intConst.value()));
      } else if (element instanceof IrConst.NullConst) {
        words.add("0");
      } else if (element instanceof IrConst.FloatConst floatConst) {
        int raw = LoweringSupport.floatToQ16_16(floatConst.value());
        words.add(LoweringSupport.formatImmediate(raw));
      } else {
        throw new CodeGenerationException("Unsupported array element: " + element);
      }
    }
    emitter.emitData(label, "DW", String.join(", ", words), "array", words.size() * 4,
        LoweringSupport.alignmentOf(arrayType.elementType()));
  }
}
