package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.frisc.emitter.FriscEmitter;
import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.codegen.frisc.ir.IrTextParser;
import hr.fer.ppj.codegen.frisc.util.LabelGenerator;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrStructType;
import hr.fer.ppj.ir.types.IrType;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FriscCodeGenerator {

  private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final IrTextParser parser = new IrTextParser();
  private final LabelGenerator labelGenerator = new LabelGenerator();

  public void generate(String irText, Path outputFile, String sourceName) {
    Objects.requireNonNull(irText, "irText must not be null");
    Objects.requireNonNull(outputFile, "outputFile must not be null");
    IrProgramModel program = parser.parse(irText);
    FriscEmitter emitter = new FriscEmitter();
    emitProgram(program, emitter, sourceName);
    emitter.writeToFile(outputFile);
  }

  private void emitProgram(IrProgramModel program, FriscEmitter emitter, String sourceName) {
    String src = sourceName == null || sourceName.isBlank() ? "unknown" : sourceName;
    String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);

    emitter.emitComment("===========================================================================");
    emitter.emitComment("PPJ Compiler - Generated FRISC Assembly Code");
    emitter.emitComment("Source: " + src);
    emitter.emitComment("Generated: " + timestamp);
    emitter.emitComment("===========================================================================");

    emitter.emitSectionHeader("Program entry point and initialization");
    emitter.emitInstruction("MOVE", List.of("40000", "R7"), "Initialize stack pointer (SP)");
    emitter.emitInstruction("CALL", List.of(functionLabel("main")), "Call main");
    emitter.emitInstruction("HALT", List.of(), "Program end");

    emitter.emitSectionHeader("Function Definitions");

    StructLayoutRegistry structLayouts = new StructLayoutRegistry();
    for (IrProgramModel.StructDef structDef : program.structDefs()) {
      structLayouts.register(structDef);
    }

    Map<String, String> functionLabels = new HashMap<>();
    for (IrProgramModel.Function func : program.functions()) {
      functionLabels.put(func.name(), functionLabel(func.name()));
    }

    Map<String, IrType> globalTypes = new HashMap<>();
    for (IrProgramModel.GlobalVar global : program.globals()) {
      globalTypes.put(global.name(), global.type());
    }

    PointerScratch pointerScratch = collectPointerScratch(program, structLayouts);
    Map<String, ParamLayout> paramLayouts = buildParamLayouts(program, structLayouts);

    for (IrProgramModel.Function function : program.functions()) {
      Map<String, String> scratchLabels = pointerScratch.labelsByFunction().getOrDefault(function.name(), Map.of());
      emitFunction(function, emitter, structLayouts, globalTypes, functionLabels, paramLayouts, scratchLabels);
    }

    emitHelpers(emitter);

    emitter.emitSectionHeader("Global Variables");
    emitter.beginDataSection();
    emitGlobals(program.globals(), emitter, structLayouts);
    emitPointerScratch(pointerScratch.scratches(), emitter);
    emitter.endDataSection();
  }

  private PointerScratch collectPointerScratch(IrProgramModel program, StructLayoutRegistry structLayouts) {
    Map<String, Map<String, String>> labelsByFunction = new LinkedHashMap<>();
    List<Scratch> scratches = new ArrayList<>();

    for (IrProgramModel.Function function : program.functions()) {
      Map<String, IrProgramModel.Slot> localSlots = new HashMap<>();
      for (IrProgramModel.Slot slot : function.slots()) {
        if (slot.kind() == IrProgramModel.SlotKind.LOCAL) {
          localSlots.put(slot.name(), slot);
        }
      }

      Map<String, IrPointerType> pointerLocals = new LinkedHashMap<>();
      for (IrProgramModel.Slot slot : localSlots.values()) {
        if (slot.type() instanceof IrPointerType pointerType) {
          pointerLocals.put(slot.name(), pointerType);
        }
      }
      if (pointerLocals.isEmpty()) {
        continue;
      }

      Map<Integer, String> addrTemps = new HashMap<>();
      for (IrProgramModel.Block block : function.blocks()) {
        for (IrProgramModel.Instruction instruction : block.instructions()) {
          if (instruction instanceof IrProgramModel.Assign assign
              && assign.rhs() instanceof IrProgramModel.AddrOfSymbol addr
              && addr.symbolRef().kind() == IrProgramModel.SymbolRefKind.LOCAL
              && pointerLocals.containsKey(addr.symbolRef().name())) {
            addrTemps.put(assign.dest().index(), addr.symbolRef().name());
          }
        }
      }

      java.util.Set<String> assignedPointerLocals = new java.util.HashSet<>();
      for (IrProgramModel.Block block : function.blocks()) {
        for (IrProgramModel.Instruction instruction : block.instructions()) {
          if (instruction instanceof IrProgramModel.Store store && store.address() instanceof IrProgramModel.Temp temp) {
            String localName = addrTemps.get(temp.index());
            if (localName != null) {
              assignedPointerLocals.add(localName);
            }
          }
        }
      }

      Map<String, String> scratchLabels = new LinkedHashMap<>();
      for (Map.Entry<String, IrPointerType> entry : pointerLocals.entrySet()) {
        String localName = entry.getKey();
        if (assignedPointerLocals.contains(localName)) {
          continue;
        }
        String label = pointerScratchLabel(function.name(), localName);
        IrPointerType pointerType = entry.getValue();
        int size = sizeOf(pointerType.baseType(), structLayouts);
        if (size <= 0) {
          size = 4;
        }
        int alignment = alignmentOf(pointerType.baseType());
        scratchLabels.put(localName, label);
        scratches.add(new Scratch(label, size, alignment,
            "scratch for " + function.name() + "." + localName));
      }

      if (!scratchLabels.isEmpty()) {
        labelsByFunction.put(function.name(), scratchLabels);
      }
    }

    return new PointerScratch(labelsByFunction, scratches);
  }

  private String pointerScratchLabel(String functionName, String localName) {
    return "G_SCRATCH_" + functionName.toUpperCase(Locale.ROOT) + "_" + localName.toUpperCase(Locale.ROOT);
  }

  private void emitPointerScratch(List<Scratch> scratches, FriscEmitter emitter) {
    for (Scratch scratch : scratches) {
      emitter.emitData(scratch.label(), "`DS", String.valueOf(scratch.size()),
          scratch.comment(), scratch.size(), scratch.alignment());
    }
  }

  private Map<String, ParamLayout> buildParamLayouts(IrProgramModel program, StructLayoutRegistry structLayouts) {
    Map<String, ParamLayout> layouts = new HashMap<>();
    for (IrProgramModel.Function function : program.functions()) {
      Map<String, IrProgramModel.Slot> paramSlots = new HashMap<>();
      for (IrProgramModel.Slot slot : function.slots()) {
        if (slot.kind() == IrProgramModel.SlotKind.PARAM) {
          paramSlots.put(slot.name(), slot);
        }
      }

      List<ParamInfo> params = new ArrayList<>();
      int totalBytes = 0;
      for (IrProgramModel.Parameter parameter : function.parameters()) {
        IrProgramModel.Slot slot = paramSlots.get(parameter.name());
        if (slot == null) {
          throw new CodeGenerationException("Missing param slot for " + function.name() + "." + parameter.name());
        }
        int offset = slot.offset();
        params.add(new ParamInfo(parameter.type(), offset));
        int size = sizeOf(parameter.type(), structLayouts);
        totalBytes = Math.max(totalBytes, offset + size);
      }
      totalBytes = alignTo(totalBytes, 4);
      layouts.put(function.name(), new ParamLayout(params, totalBytes));
    }
    return layouts;
  }

  private String functionLabel(String name) {
    return labelGenerator.functionLabel(name);
  }

  private void emitFunction(
      IrProgramModel.Function function,
      FriscEmitter emitter,
      StructLayoutRegistry structLayouts,
      Map<String, IrType> globalTypes,
      Map<String, String> functionLabels,
      Map<String, ParamLayout> functionParamLayouts,
      Map<String, String> scratchLabels) {

    String funcLabel = functionLabel(function.name());
    String exitLabel = labelGenerator.newLabel("L_EXIT_" + funcLabel);

    Map<String, IrProgramModel.Slot> localSlots = new HashMap<>();
    Map<String, IrProgramModel.Slot> paramSlots = new HashMap<>();
    for (IrProgramModel.Slot slot : function.slots()) {
      if (slot.kind() == IrProgramModel.SlotKind.LOCAL || slot.kind() == IrProgramModel.SlotKind.SPILL) {
        localSlots.put(slot.name(), slot);
      } else if (slot.kind() == IrProgramModel.SlotKind.PARAM) {
        paramSlots.put(slot.name(), slot);
      }
    }

    TempAnalysis tempAnalysis = analyzeTemps(function, localSlots, paramSlots, globalTypes);
    Set<Integer> addrIndexNeedsCheck = analyzeAddrIndexChecks(function);
    int tempCount = tempAnalysis.maxTempIndex + 1;
    int argScratchCount = tempAnalysis.maxCallArgs;

    int tempAreaSize = tempCount * 4;
    int argScratchSize = argScratchCount * 4;
    int frameSize = alignTo(function.localsBytes() + tempAreaSize + argScratchSize, 4);

    FunctionContext ctx = new FunctionContext(
        function,
        emitter,
        structLayouts,
        globalTypes,
        localSlots,
        paramSlots,
        functionLabels,
        functionParamLayouts,
        addrIndexNeedsCheck,
        tempAnalysis.tempTypes,
        frameSize,
        function.localsBytes(),
        tempCount,
        argScratchCount,
        exitLabel,
        buildBlockLabelMap(function));

    emitter.emitLabel(funcLabel, "Function: " + function.name());
    emitter.emitInstruction("PUSH", List.of("R5"), "Save old FP");
    emitter.emitInstruction("MOVE", List.of("R7", "R5"), "Set FP");
    if (frameSize > 0) {
      emitter.emitInstruction("SUB", List.of("R7", formatImmediate(frameSize), "R7"), "Allocate locals/temps");
      int wordCount = frameSize / 4;
      if (wordCount > 0) {
        emitLoadImmediate(wordCount, ctx, "R1", "Zero words");
        ctx.emitter.emitInstruction("MOVE", List.of("R7", "R0"), "Zero ptr");
        emitLoadImmediate(0, ctx, "R2", "Zero");
        String zeroLoop = labelGenerator.newLabel("L_ZERO");
        ctx.emitter.emitLabel(zeroLoop, null);
        ctx.emitter.emitInstruction("STORE", List.of("R2", "(R0)"), "Clear");
        ctx.emitter.emitInstruction("ADD", List.of("R0", "4", "R0"), null);
        ctx.emitter.emitInstruction("SUB", List.of("R1", "1", "R1"), null);
        ctx.emitter.emitInstruction("JP_NE", List.of(zeroLoop), null);
      }
    }

    if (!scratchLabels.isEmpty()) {
      ctx.emitter.emitComment("Initialize pointer locals to scratch storage");
      for (Map.Entry<String, String> entry : scratchLabels.entrySet()) {
        IrProgramModel.SymbolRef symbolRef =
            new IrProgramModel.SymbolRef(IrProgramModel.SymbolRefKind.LOCAL, entry.getKey());
        emitAddrOfSymbol(symbolRef, ctx, "R0");
        ctx.emitter.emitInstruction("MOVE", List.of(entry.getValue(), "R1"), "Pointer scratch");
        ctx.emitter.emitInstruction("STORE", List.of("R1", "(R0)"), "Init pointer local");
      }
    }

    for (IrProgramModel.Block block : function.blocks()) {
      String blockLabel = ctx.blockLabels.get(block.label());
      emitter.emitLabel(blockLabel, null);
      for (IrProgramModel.Instruction instruction : block.instructions()) {
        emitInstruction(instruction, ctx);
      }
      emitTerminator(block.terminator(), ctx);
    }

    emitter.emitLabel(exitLabel, "Function epilogue");
    if (frameSize > 0) {
      emitter.emitInstruction("ADD", List.of("R7", formatImmediate(frameSize), "R7"), "Deallocate locals/temps");
    }
    emitter.emitInstruction("POP", List.of("R5"), "Restore FP");
    emitter.emitInstruction("RET", List.of(), "Return");
  }

  private Map<String, String> buildBlockLabelMap(IrProgramModel.Function function) {
    Map<String, String> map = new LinkedHashMap<>();
    for (IrProgramModel.Block block : function.blocks()) {
      map.put(block.label(), labelGenerator.blockLabel(function.name(), block.label()));
    }
    return map;
  }

  private void emitInstruction(IrProgramModel.Instruction instruction, FunctionContext ctx) {
    if (instruction instanceof IrProgramModel.Assign assign) {
      emitRhs(assign.rhs(), ctx, assign.dest().index());
      storeTemp(assign.dest().index(), ctx);
      return;
    }
    if (instruction instanceof IrProgramModel.Store store) {
      emitStore(store, ctx);
      return;
    }
    if (instruction instanceof IrProgramModel.VoidCall call) {
      emitCall(call.funcName(), call.args(), null, ctx);
      return;
    }
    throw new CodeGenerationException("Unsupported instruction: " + instruction);
  }

  private void emitStore(IrProgramModel.Store store, FunctionContext ctx) {
    if (isAggregate(store.storeType())) {
      emitValue(store.address(), ctx, "R0");
      emitValue(store.value(), ctx, "R1");
      int size = sizeOf(store.storeType(), ctx.structLayouts);
      emitMemCopy("R1", "R0", size, ctx, "Copy struct");
      return;
    }

    emitValue(store.address(), ctx, "R0");
    ctx.emitter.emitInstruction("PUSH", List.of("R0"), "Save address");
    emitValue(store.value(), ctx, "R0");
    ctx.emitter.emitInstruction("POP", List.of("R1"), "Restore address");

    if (isChar(store.storeType())) {
      ctx.emitter.emitInstruction("STOREB", List.of("R0", "(R1)"), "Store byte");
    } else {
      ctx.emitter.emitInstruction("STORE", List.of("R0", "(R1)"), "Store word");
    }
  }

  private void emitTerminator(IrProgramModel.Terminator terminator, FunctionContext ctx) {
    if (terminator instanceof IrProgramModel.Br br) {
      emitValue(br.condition(), ctx, "R0");
      ctx.emitter.emitInstruction("CMP", List.of("R0", "0"), "Branch on condition");
      String trueLabel = ctx.blockLabels.get(br.trueLabel());
      String falseLabel = ctx.blockLabels.get(br.falseLabel());
      ctx.emitter.emitInstruction("JP_NE", List.of(trueLabel), null);
      ctx.emitter.emitInstruction("JP", List.of(falseLabel), null);
      return;
    }
    if (terminator instanceof IrProgramModel.Jmp jmp) {
      String target = ctx.blockLabels.get(jmp.targetLabel());
      ctx.emitter.emitInstruction("JP", List.of(target), null);
      return;
    }
    if (terminator instanceof IrProgramModel.Ret ret) {
      if (ret.value() == null) {
        ctx.emitter.emitInstruction("MOVE", List.of("0", "R6"), "Return 0 (void)");
      } else {
        emitValue(ret.value(), ctx, "R0");
        ctx.emitter.emitInstruction("MOVE", List.of("R0", "R6"), "Set return value");
      }
      ctx.emitter.emitInstruction("JP", List.of(ctx.exitLabel), null);
      return;
    }
    throw new CodeGenerationException("Unsupported terminator: " + terminator);
  }

  private void emitRhs(IrProgramModel.Rhs rhs, FunctionContext ctx, Integer destTemp) {
    if (rhs instanceof IrProgramModel.ConstRhs constRhs) {
      emitConst(constRhs.constant(), ctx, "R0");
      return;
    }
    if (rhs instanceof IrProgramModel.AddrOfSymbol addr) {
      emitAddrOfSymbol(addr.symbolRef(), ctx, "R0");
      return;
    }
    if (rhs instanceof IrProgramModel.AddrIndex addrIndex) {
      emitAddrIndex(addrIndex, ctx, destTemp);
      return;
    }
    if (rhs instanceof IrProgramModel.AddrField addrField) {
      emitAddrField(addrField, ctx);
      return;
    }
    if (rhs instanceof IrProgramModel.Load load) {
      emitValue(load.address(), ctx, "R0");
      if (isAggregate(load.loadType())) {
        return;
      }
      if (isChar(load.loadType())) {
        ctx.emitter.emitInstruction("LOADB", List.of("R0", "(R0)"), "Load byte");
      } else {
        ctx.emitter.emitInstruction("LOAD", List.of("R0", "(R0)"), "Load word");
      }
      return;
    }
    if (rhs instanceof IrProgramModel.BinOp binOp) {
      emitBinOp(binOp, ctx);
      return;
    }
    if (rhs instanceof IrProgramModel.CmpOp cmpOp) {
      emitCmpOp(cmpOp, ctx);
      return;
    }
    if (rhs instanceof IrProgramModel.Call call) {
      emitCall(call.funcName(), call.args(), call.resultType(), ctx);
      return;
    }
    if (rhs instanceof IrProgramModel.UnaryOp unaryOp) {
      emitUnaryOp(unaryOp, ctx);
      return;
    }
    if (rhs instanceof IrProgramModel.CastOp castOp) {
      emitCastOp(castOp, ctx);
      return;
    }

    throw new CodeGenerationException("Unsupported RHS: " + rhs);
  }

  private void emitAddrIndex(IrProgramModel.AddrIndex addrIndex, FunctionContext ctx, Integer destTemp) {
    emitValue(addrIndex.base(), ctx, "R0");
    ctx.emitter.emitInstruction("PUSH", List.of("R0"), "Save base address");
    emitValue(addrIndex.index(), ctx, "R0");
    ctx.emitter.emitInstruction("MOVE", List.of("R0", "R1"), "Index");
    ctx.emitter.emitInstruction("POP", List.of("R0"), "Restore base");

    IrType baseType = valueType(addrIndex.base(), ctx);
    boolean needsCheck = destTemp != null && ctx.addrIndexNeedsCheck.contains(destTemp);
    if (needsCheck && baseType instanceof IrPointerType pointerType && pointerType.baseType() instanceof IrArrayType arrayType) {
      emitBoundsCheck(arrayType.size(), ctx);
    }

    int elemSize = addrIndex.elemSize();
    if (elemSize == 1) {
      // no-op
    } else if (elemSize == 2) {
      ctx.emitter.emitInstruction("SHL", List.of("R1", "1", "R1"), "Index * 2");
    } else if (elemSize == 4) {
      ctx.emitter.emitInstruction("SHL", List.of("R1", "2", "R1"), "Index * 4");
    } else {
      ctx.emitter.emitInstruction("PUSH", List.of("R0"), "Save base");
      ctx.emitter.emitInstruction("PUSH", List.of("R1"), "Save index");
      ctx.emitter.emitInstruction("MOVE", List.of(formatImmediate(elemSize), "R0"), "Elem size");
      ctx.emitter.emitInstruction("PUSH", List.of("R0"), "Arg (size)");
      ctx.emitter.emitInstruction("CALL", List.of("F_MUL"), "Index * size");
      ctx.emitter.emitInstruction("ADD", List.of("R7", "8", "R7"), "Clean args");
      ctx.emitter.emitInstruction("POP", List.of("R0"), "Restore base");
      ctx.emitter.emitInstruction("MOVE", List.of("R6", "R1"), "Result");
      ctx.emitter.markMulNeeded();
    }

    ctx.emitter.emitInstruction("ADD", List.of("R0", "R1", "R0"), "Base + offset");
  }

  private void emitAddrField(IrProgramModel.AddrField addrField, FunctionContext ctx) {
    emitValue(addrField.base(), ctx, "R0");
    int offset = ctx.structLayouts.getFieldOffset(addrField.structName(), addrField.fieldName());
    if (offset != 0) {
      ctx.emitter.emitInstruction("ADD", List.of("R0", formatImmediate(offset), "R0"), "Field offset");
    }
  }

  private void emitBoundsCheck(int size, FunctionContext ctx) {
    if (size <= 0) {
      return;
    }
    ctx.emitter.markBoundsCheckNeeded();
    ctx.emitter.emitInstruction("CMP", List.of("R1", "0"), "Bounds check");
    ctx.emitter.emitInstruction("JP_SLT", List.of("L_BOUNDS_ERROR"), null);
    ctx.emitter.emitInstruction("CMP", List.of("R1", formatImmediate(size)), null);
    ctx.emitter.emitInstruction("JP_SGE", List.of("L_BOUNDS_ERROR"), null);
  }

  private IrType valueType(IrProgramModel.Value value, FunctionContext ctx) {
    if (value instanceof IrProgramModel.Temp temp) {
      return ctx.tempTypes.get(temp.index());
    }
    if (value instanceof IrProgramModel.Const c) {
      return c.constant().type();
    }
    return null;
  }

  private void emitMemCopy(String srcReg, String dstReg, int size, FunctionContext ctx, String comment) {
    if (size <= 0) {
      return;
    }
    emitLoadImmediate(size, ctx, "R2", comment);
    String loop = labelGenerator.newLabel("L_MEMCPY");
    ctx.emitter.emitLabel(loop, null);
    ctx.emitter.emitInstruction("LOADB", List.of("R3", "(" + srcReg + ")"), null);
    ctx.emitter.emitInstruction("STOREB", List.of("R3", "(" + dstReg + ")"), null);
    ctx.emitter.emitInstruction("ADD", List.of(srcReg, "1", srcReg), null);
    ctx.emitter.emitInstruction("ADD", List.of(dstReg, "1", dstReg), null);
    ctx.emitter.emitInstruction("SUB", List.of("R2", "1", "R2"), null);
    ctx.emitter.emitInstruction("JP_NE", List.of(loop), null);
  }

  private void emitBinOp(IrProgramModel.BinOp binOp, FunctionContext ctx) {
    emitValue(binOp.left(), ctx, "R0");
    ctx.emitter.emitInstruction("PUSH", List.of("R0"), "Save left");
    emitValue(binOp.right(), ctx, "R0");
    ctx.emitter.emitInstruction("MOVE", List.of("R0", "R1"), "Right");
    ctx.emitter.emitInstruction("POP", List.of("R0"), "Left");

    if (isFloat(binOp.resultType())) {
      emitFloatBinOp(binOp, ctx);
      return;
    }

    switch (binOp.op()) {
      case ADD -> ctx.emitter.emitInstruction("ADD", List.of("R0", "R1", "R0"), null);
      case SUB -> ctx.emitter.emitInstruction("SUB", List.of("R0", "R1", "R0"), null);
      case AND -> ctx.emitter.emitInstruction("AND", List.of("R0", "R1", "R0"), null);
      case OR -> ctx.emitter.emitInstruction("OR", List.of("R0", "R1", "R0"), null);
      case XOR -> ctx.emitter.emitInstruction("XOR", List.of("R0", "R1", "R0"), null);
      case SHL -> ctx.emitter.emitInstruction("SHL", List.of("R0", "R1", "R0"), null);
      case SHR -> ctx.emitter.emitInstruction("SHR", List.of("R0", "R1", "R0"), null);
      case MUL -> {
        emitBinaryHelper("F_MUL", ctx);
        ctx.emitter.markMulNeeded();
      }
      case DIV -> {
        emitBinaryHelper("F_DIV", ctx);
        ctx.emitter.markDivNeeded();
      }
      case MOD -> {
        emitBinaryHelper("F_MOD", ctx);
        ctx.emitter.markModNeeded();
      }
    }
  }

  private void emitFloatBinOp(IrProgramModel.BinOp binOp, FunctionContext ctx) {
    if (binOp.op() == IrProgramModel.BinOpName.MUL) {
      emitBinaryHelper("F_FMUL", ctx);
      ctx.emitter.markFmulNeeded();
      return;
    }
    if (binOp.op() == IrProgramModel.BinOpName.DIV) {
      emitBinaryHelper("F_FDIV", ctx);
      ctx.emitter.markFdivNeeded();
      ctx.emitter.markDivNeeded();
      ctx.emitter.markModNeeded();
      return;
    }
    if (binOp.op() == IrProgramModel.BinOpName.ADD) {
      ctx.emitter.emitInstruction("ADD", List.of("R0", "R1", "R0"), "Float add");
      return;
    }
    if (binOp.op() == IrProgramModel.BinOpName.SUB) {
      ctx.emitter.emitInstruction("SUB", List.of("R0", "R1", "R0"), "Float sub");
      return;
    }
    throw new CodeGenerationException("Unsupported float binop: " + binOp.op());
  }

  private void emitBinaryHelper(String label, FunctionContext ctx) {
    ctx.emitter.emitInstruction("MOVE", List.of("R1", "R2"), "Save right");
    ctx.emitter.emitInstruction("PUSH", List.of("R2"), "Arg right");
    ctx.emitter.emitInstruction("PUSH", List.of("R0"), "Arg left");
    ctx.emitter.emitInstruction("CALL", List.of(label), null);
    ctx.emitter.emitInstruction("ADD", List.of("R7", "8", "R7"), "Clean args");
    ctx.emitter.emitInstruction("MOVE", List.of("R6", "R0"), "Result");
  }

  private void emitCmpOp(IrProgramModel.CmpOp cmpOp, FunctionContext ctx) {
    emitValue(cmpOp.left(), ctx, "R0");
    ctx.emitter.emitInstruction("PUSH", List.of("R0"), "Save left");
    emitValue(cmpOp.right(), ctx, "R0");
    ctx.emitter.emitInstruction("MOVE", List.of("R0", "R1"), "Right");
    ctx.emitter.emitInstruction("POP", List.of("R0"), "Left");

    String trueLabel = labelGenerator.newLabel("L_CMP_TRUE");
    String endLabel = labelGenerator.newLabel("L_CMP_END");

    ctx.emitter.emitInstruction("CMP", List.of("R0", "R1"), null);
    ctx.emitter.emitInstruction(conditionJump(cmpOp.op()), List.of(trueLabel), null);
    ctx.emitter.emitInstruction("MOVE", List.of("0", "R0"), "False");
    ctx.emitter.emitInstruction("JP", List.of(endLabel), null);
    ctx.emitter.emitLabel(trueLabel, null);
    ctx.emitter.emitInstruction("MOVE", List.of("1", "R0"), "True");
    ctx.emitter.emitLabel(endLabel, null);
  }

  private String conditionJump(IrProgramModel.CmpOpName op) {
    return switch (op) {
      case EQ -> "JP_EQ";
      case NE -> "JP_NE";
      case LT -> "JP_SLT";
      case LE -> "JP_SLE";
      case GT -> "JP_SGT";
      case GE -> "JP_SGE";
    };
  }

  private void emitUnaryOp(IrProgramModel.UnaryOp unaryOp, FunctionContext ctx) {
    emitValue(unaryOp.operand(), ctx, "R0");
    if (unaryOp.op() == IrProgramModel.UnaryOpName.NEG) {
      ctx.emitter.emitInstruction("MOVE", List.of("0", "R1"), null);
      ctx.emitter.emitInstruction("SUB", List.of("R1", "R0", "R0"), "Negate");
      return;
    }

    if (unaryOp.op() == IrProgramModel.UnaryOpName.NOT) {
      String trueLabel = labelGenerator.newLabel("L_NOT_TRUE");
      String endLabel = labelGenerator.newLabel("L_NOT_END");
      ctx.emitter.emitInstruction("CMP", List.of("R0", "0"), null);
      ctx.emitter.emitInstruction("JP_EQ", List.of(trueLabel), null);
      ctx.emitter.emitInstruction("MOVE", List.of("0", "R0"), "False");
      ctx.emitter.emitInstruction("JP", List.of(endLabel), null);
      ctx.emitter.emitLabel(trueLabel, null);
      ctx.emitter.emitInstruction("MOVE", List.of("1", "R0"), "True");
      ctx.emitter.emitLabel(endLabel, null);
      return;
    }

    throw new CodeGenerationException("Unsupported unary op: " + unaryOp.op());
  }

  private void emitCastOp(IrProgramModel.CastOp castOp, FunctionContext ctx) {
    emitValue(castOp.operand(), ctx, "R0");
    switch (castOp.op()) {
      case TRUNC -> {
        ctx.emitter.emitInstruction("AND", List.of("R0", formatImmediate(0xFF), "R0"), "Truncate to byte");
      }
      case ZEXT -> {
        ctx.emitter.emitInstruction("AND", List.of("R0", formatImmediate(0xFF), "R0"), "Zero-extend byte");
      }
      case SEXT -> {
        ctx.emitter.emitInstruction("SHL", List.of("R0", "18", "R0"), "Sign-extend (shift)" );
        ctx.emitter.emitInstruction("ASHR", List.of("R0", "18", "R0"), null);
      }
      case PTRCAST -> {
        // no-op
      }
      case ITOF -> {
        emitUnaryHelper("F_I2F", ctx);
        ctx.emitter.markI2fNeeded();
      }
      case FTOI -> {
        emitUnaryHelper("F_F2I", ctx);
        ctx.emitter.markF2iNeeded();
      }
    }
  }

  private void emitUnaryHelper(String label, FunctionContext ctx) {
    ctx.emitter.emitInstruction("PUSH", List.of("R0"), "Arg");
    ctx.emitter.emitInstruction("CALL", List.of(label), null);
    ctx.emitter.emitInstruction("ADD", List.of("R7", "4", "R7"), "Clean args");
    ctx.emitter.emitInstruction("MOVE", List.of("R6", "R0"), "Result");
  }

  private void emitCall(String funcName, List<IrProgramModel.Value> args, IrType resultType, FunctionContext ctx) {
    int argCount = args.size();
    for (int i = 0; i < argCount; i++) {
      emitValue(args.get(i), ctx, "R0");
      storeArgScratch(i, ctx);
    }

    ParamLayout layout = ctx.functionParamLayouts.get(funcName);
    boolean useLayout = layout != null && layout.params().size() == argCount;

    if (useLayout) {
      int totalBytes = layout.totalSize();
      if (totalBytes > 0) {
        ctx.emitter.emitInstruction("SUB", List.of("R7", formatImmediate(totalBytes), "R7"),
            "Allocate args");
      }
      for (int i = 0; i < argCount; i++) {
        ParamInfo param = layout.params().get(i);
        int offset = param.offset();
        IrType paramType = param.type();
        loadArgScratch(i, ctx, "R0");
        if (isAggregate(paramType)) {
          ctx.emitter.emitInstruction("MOVE", List.of("R7", "R1"), "Arg dst");
          if (offset != 0) {
            ctx.emitter.emitInstruction("ADD", List.of("R1", formatImmediate(offset), "R1"), "Arg offset");
          }
          int size = sizeOf(paramType, ctx.structLayouts);
          emitMemCopy("R0", "R1", size, ctx, "Copy arg");
        } else {
          String addr = formatStackOffset(offset);
          if (isChar(paramType)) {
            ctx.emitter.emitInstruction("STOREB", List.of("R0", "(" + addr + ")"), "Store arg byte");
          } else {
            ctx.emitter.emitInstruction("STORE", List.of("R0", "(" + addr + ")"), "Store arg");
          }
        }
      }
    } else {
      for (int i = argCount - 1; i >= 0; i--) {
        loadArgScratch(i, ctx, "R0");
        ctx.emitter.emitInstruction("PUSH", List.of("R0"), "Push arg");
      }
    }

    String label = ctx.functionLabels.get(funcName);
    if (label == null) {
      label = functionLabel(funcName);
    }
    ctx.emitter.emitInstruction("CALL", List.of(label), "Call " + funcName);
    if (argCount > 0) {
      int cleanBytes = useLayout ? layout.totalSize() : argCount * 4;
      if (cleanBytes > 0) {
        ctx.emitter.emitInstruction("ADD", List.of("R7", formatImmediate(cleanBytes), "R7"), "Clean args");
      }
    }
    if (resultType != null) {
      ctx.emitter.emitInstruction("MOVE", List.of("R6", "R0"), "Return value");
    }
  }

  private void emitValue(IrProgramModel.Value value, FunctionContext ctx, String targetReg) {
    if (value instanceof IrProgramModel.Temp temp) {
      loadTemp(temp.index(), ctx, targetReg);
      return;
    }
    if (value instanceof IrProgramModel.Const constant) {
      emitConst(constant.constant(), ctx, targetReg);
      return;
    }
    throw new CodeGenerationException("Unsupported value: " + value);
  }

  private void emitConst(IrConst constant, FunctionContext ctx, String targetReg) {
    if (constant instanceof IrConst.IntConst intConst) {
      emitLoadImmediate(intConst.value(), ctx, targetReg, null);
      return;
    }
    if (constant instanceof IrConst.CharConst charConst) {
      emitLoadImmediate(charConst.value(), ctx, targetReg, null);
      return;
    }
    if (constant instanceof IrConst.NullConst) {
      emitLoadImmediate(0, ctx, targetReg, null);
      return;
    }
    if (constant instanceof IrConst.FloatConst floatConst) {
      int raw = floatToQ16_16(floatConst.value());
      emitLoadImmediate(raw, ctx, targetReg, null);
      return;
    }
    if (constant instanceof IrConst.ArrayConst) {
      throw new CodeGenerationException("Array constant cannot be used as value");
    }
    throw new CodeGenerationException("Unsupported constant: " + constant);
  }

  private void emitLoadImmediate(int value, FunctionContext ctx, String targetReg, String comment) {
    if (fitsSigned20(value)) {
      ctx.emitter.emitInstruction("MOVE", List.of(formatImmediate(value), targetReg), comment);
      return;
    }
    int high = (value >>> 16) & 0xFFFF;
    int low = value & 0xFFFF;
    ctx.emitter.emitInstruction("MOVE", List.of(formatImmediate(high), targetReg), comment);
    ctx.emitter.emitInstruction("SHL", List.of(targetReg, "10", targetReg), "imm << 16");
    if (low != 0) {
      ctx.emitter.emitInstruction("OR", List.of(targetReg, formatImmediate(low), targetReg), "imm low");
    }
  }

  private void emitAddrOfSymbol(IrProgramModel.SymbolRef symbolRef, FunctionContext ctx, String targetReg) {
    switch (symbolRef.kind()) {
      case GLOBAL -> {
        String label = labelGenerator.globalLabel(symbolRef.name());
        ctx.emitter.emitInstruction("MOVE", List.of(label, targetReg), "Address of global");
      }
      case PARAM -> {
        IrProgramModel.Slot slot = ctx.paramSlots.get(symbolRef.name());
        if (slot == null) {
          throw new CodeGenerationException("Unknown param: " + symbolRef.name());
        }
        int offset = 8 + slot.offset();
        ctx.emitter.emitInstruction("MOVE", List.of("R5", targetReg), null);
        ctx.emitter.emitInstruction("ADD", List.of(targetReg, formatImmediate(offset), targetReg), "Param address");
      }
      case LOCAL -> {
        IrProgramModel.Slot slot = ctx.localSlots.get(symbolRef.name());
        if (slot == null) {
          throw new CodeGenerationException("Unknown local: " + symbolRef.name());
        }
        int offset = 4 + slot.offset();
        int size = sizeOf(slot.type(), ctx.structLayouts);
        int align = alignmentOf(slot.type());
        if (size > align) {
          offset += (size - align);
        }
        ctx.emitter.emitInstruction("MOVE", List.of("R5", targetReg), null);
        ctx.emitter.emitInstruction("SUB", List.of(targetReg, formatImmediate(offset), targetReg), "Local address");
      }
    }
  }

  private void loadTemp(int tempIndex, FunctionContext ctx, String targetReg) {
    Integer offset = ctx.tempOffsets.get(tempIndex);
    if (offset == null) {
      throw new CodeGenerationException("Unknown temp: t" + tempIndex);
    }
    String addr = formatFrameOffset(-offset);
    ctx.emitter.emitInstruction("LOAD", List.of(targetReg, "(" + addr + ")"), "Load temp t" + tempIndex);

    IrType type = ctx.tempTypes.get(tempIndex);
    if (isChar(type)) {
      ctx.emitter.emitInstruction("AND", List.of(targetReg, formatImmediate(0xFF), targetReg), "Clamp char");
    }
  }

  private void storeTemp(int tempIndex, FunctionContext ctx) {
    Integer offset = ctx.tempOffsets.get(tempIndex);
    if (offset == null) {
      throw new CodeGenerationException("Unknown temp: t" + tempIndex);
    }
    String addr = formatFrameOffset(-offset);
    ctx.emitter.emitInstruction("STORE", List.of("R0", "(" + addr + ")"), "Store temp t" + tempIndex);
  }

  private void storeArgScratch(int index, FunctionContext ctx) {
    Integer offset = ctx.argOffsets.get(index);
    if (offset == null) {
      throw new CodeGenerationException("Arg scratch missing for index " + index);
    }
    String addr = formatFrameOffset(-offset);
    ctx.emitter.emitInstruction("STORE", List.of("R0", "(" + addr + ")"), "Save arg");
  }

  private void loadArgScratch(int index, FunctionContext ctx, String targetReg) {
    Integer offset = ctx.argOffsets.get(index);
    if (offset == null) {
      throw new CodeGenerationException("Arg scratch missing for index " + index);
    }
    String addr = formatFrameOffset(-offset);
    ctx.emitter.emitInstruction("LOAD", List.of(targetReg, "(" + addr + ")"), "Load arg");
  }

  private String formatFrameOffset(int offset) {
    if (offset == 0) {
      return "R5";
    }
    if (offset > 0) {
      return "R5+" + formatImmediate(offset);
    }
    return "R5-" + formatImmediate(-offset);
  }

  private String formatStackOffset(int offset) {
    if (offset == 0) {
      return "R7";
    }
    if (offset > 0) {
      return "R7+" + formatImmediate(offset);
    }
    return "R7-" + formatImmediate(-offset);
  }

  private void emitGlobals(List<IrProgramModel.GlobalVar> globals, FriscEmitter emitter, StructLayoutRegistry structLayouts) {
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

  private void emitGlobalScalar(String label, IrType type, IrConst initializer, FriscEmitter emitter) {
    if (initializer == null) {
      if (isChar(type)) {
        emitter.emitData(label, "DB", "0", "char", 1, alignmentOf(type));
      } else {
        emitter.emitData(label, "DW", "0", "scalar", 4, alignmentOf(type));
      }
      return;
    }

    if (initializer instanceof IrConst.IntConst intConst) {
      emitter.emitData(label, "DW", formatImmediate(intConst.value()), "int", 4, alignmentOf(type));
      return;
    }
    if (initializer instanceof IrConst.CharConst charConst) {
      emitter.emitData(label, "DB", formatImmediate(charConst.value()), "char", 1, alignmentOf(type));
      return;
    }
    if (initializer instanceof IrConst.FloatConst floatConst) {
      int raw = floatToQ16_16(floatConst.value());
      emitter.emitData(label, "DW", formatImmediate(raw), "float", 4, alignmentOf(type));
      return;
    }
    if (initializer instanceof IrConst.NullConst) {
      emitter.emitData(label, "DW", "0", "null", 4, alignmentOf(type));
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
    int size = sizeOf(structType, structLayouts);
    if (initializer == null) {
      emitter.emitData(label, "`DS", String.valueOf(size), "struct", size, alignmentOf(structType));
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

    int elemSize = sizeOf(arrayType.elementType(), structLayouts);
    int totalSize = arrayType.size() * elemSize;

    if (initializer == null) {
      emitter.emitData(label, "`DS", String.valueOf(totalSize), "uninitialized array", totalSize,
          alignmentOf(arrayType.elementType()));
      return;
    }

    if (!(initializer instanceof IrConst.ArrayConst arrayConst)) {
      throw new CodeGenerationException("Array initializer mismatch for " + label);
    }

    if (isChar(arrayType.elementType())) {
      List<String> bytes = new ArrayList<>();
      for (IrConst element : arrayConst.elements()) {
        if (element instanceof IrConst.CharConst charConst) {
          bytes.add(formatImmediate(charConst.value()));
        } else if (element instanceof IrConst.IntConst intConst) {
          bytes.add(formatImmediate(intConst.value()));
        } else {
          throw new CodeGenerationException("Invalid char array element: " + element);
        }
      }
      emitter.emitData(label, "DB", String.join(", ", bytes), "char array", bytes.size(),
          alignmentOf(arrayType.elementType()));
      return;
    }

    List<String> words = new ArrayList<>();
    for (IrConst element : arrayConst.elements()) {
      if (element instanceof IrConst.IntConst intConst) {
        words.add(formatImmediate(intConst.value()));
      } else if (element instanceof IrConst.NullConst) {
        words.add("0");
      } else if (element instanceof IrConst.FloatConst floatConst) {
        int raw = floatToQ16_16(floatConst.value());
        words.add(formatImmediate(raw));
      } else {
        throw new CodeGenerationException("Unsupported array element: " + element);
      }
    }
    emitter.emitData(label, "DW", String.join(", ", words), "array", words.size() * 4,
        alignmentOf(arrayType.elementType()));
  }

  private TempAnalysis analyzeTemps(
      IrProgramModel.Function function,
      Map<String, IrProgramModel.Slot> localSlots,
      Map<String, IrProgramModel.Slot> paramSlots,
      Map<String, IrType> globalTypes) {

    Map<Integer, IrType> tempTypes = new HashMap<>();
    int maxTemp = -1;
    int maxArgs = 0;

    for (IrProgramModel.Block block : function.blocks()) {
      for (IrProgramModel.Instruction instruction : block.instructions()) {
        if (instruction instanceof IrProgramModel.Assign assign) {
          IrType type = resultType(assign.rhs(), localSlots, paramSlots, globalTypes);
          tempTypes.put(assign.dest().index(), type);
          maxTemp = Math.max(maxTemp, assign.dest().index());
        }
        maxTemp = Math.max(maxTemp, maxTempInInstruction(instruction));
        maxArgs = Math.max(maxArgs, callArgsInInstruction(instruction));
      }
      maxTemp = Math.max(maxTemp, maxTempInTerminator(block.terminator()));
    }

    return new TempAnalysis(tempTypes, maxTemp, maxArgs);
  }

  private Set<Integer> analyzeAddrIndexChecks(IrProgramModel.Function function) {
    Set<Integer> addressUsed = new HashSet<>();

    for (IrProgramModel.Block block : function.blocks()) {
      for (IrProgramModel.Instruction instruction : block.instructions()) {
        if (instruction instanceof IrProgramModel.Store store) {
          addTempIfValue(store.address(), addressUsed);
        } else if (instruction instanceof IrProgramModel.Assign assign
            && assign.rhs() instanceof IrProgramModel.Load load) {
          addTempIfValue(load.address(), addressUsed);
        }
      }
    }

    boolean changed;
    do {
      changed = false;
      for (IrProgramModel.Block block : function.blocks()) {
        for (IrProgramModel.Instruction instruction : block.instructions()) {
          if (!(instruction instanceof IrProgramModel.Assign assign)) {
            continue;
          }
          int dest = assign.dest().index();
          if (!addressUsed.contains(dest)) {
            continue;
          }
          IrProgramModel.Rhs rhs = assign.rhs();
          if (rhs instanceof IrProgramModel.AddrField addrField) {
            changed |= addTempIfValue(addrField.base(), addressUsed);
          } else if (rhs instanceof IrProgramModel.AddrIndex addrIndex) {
            changed |= addTempIfValue(addrIndex.base(), addressUsed);
          }
        }
      }
    } while (changed);

    Set<Integer> checks = new HashSet<>();
    for (IrProgramModel.Block block : function.blocks()) {
      for (IrProgramModel.Instruction instruction : block.instructions()) {
        if (instruction instanceof IrProgramModel.Assign assign
            && assign.rhs() instanceof IrProgramModel.AddrIndex
            && addressUsed.contains(assign.dest().index())) {
          checks.add(assign.dest().index());
        }
      }
    }
    return checks;
  }

  private boolean addTempIfValue(IrProgramModel.Value value, Set<Integer> set) {
    if (value instanceof IrProgramModel.Temp temp) {
      return set.add(temp.index());
    }
    return false;
  }

  private int maxTempInInstruction(IrProgramModel.Instruction instruction) {
    if (instruction instanceof IrProgramModel.Assign assign) {
      return maxTempInRhs(assign.rhs());
    }
    if (instruction instanceof IrProgramModel.Store store) {
      return Math.max(maxTempInValue(store.address()), maxTempInValue(store.value()));
    }
    if (instruction instanceof IrProgramModel.VoidCall call) {
      return maxTempInValues(call.args());
    }
    return -1;
  }

  private int maxTempInTerminator(IrProgramModel.Terminator terminator) {
    if (terminator instanceof IrProgramModel.Br br) {
      return maxTempInValue(br.condition());
    }
    if (terminator instanceof IrProgramModel.Ret ret) {
      if (ret.value() == null) {
        return -1;
      }
      return maxTempInValue(ret.value());
    }
    return -1;
  }

  private int maxTempInRhs(IrProgramModel.Rhs rhs) {
    if (rhs instanceof IrProgramModel.ConstRhs) {
      return -1;
    }
    if (rhs instanceof IrProgramModel.AddrOfSymbol) {
      return -1;
    }
    if (rhs instanceof IrProgramModel.AddrIndex addrIndex) {
      return Math.max(maxTempInValue(addrIndex.base()), maxTempInValue(addrIndex.index()));
    }
    if (rhs instanceof IrProgramModel.AddrField addrField) {
      return maxTempInValue(addrField.base());
    }
    if (rhs instanceof IrProgramModel.Load load) {
      return maxTempInValue(load.address());
    }
    if (rhs instanceof IrProgramModel.BinOp binOp) {
      return Math.max(maxTempInValue(binOp.left()), maxTempInValue(binOp.right()));
    }
    if (rhs instanceof IrProgramModel.CmpOp cmpOp) {
      return Math.max(maxTempInValue(cmpOp.left()), maxTempInValue(cmpOp.right()));
    }
    if (rhs instanceof IrProgramModel.Call call) {
      return maxTempInValues(call.args());
    }
    if (rhs instanceof IrProgramModel.UnaryOp unaryOp) {
      return maxTempInValue(unaryOp.operand());
    }
    if (rhs instanceof IrProgramModel.CastOp castOp) {
      return maxTempInValue(castOp.operand());
    }
    return -1;
  }

  private int maxTempInValue(IrProgramModel.Value value) {
    if (value instanceof IrProgramModel.Temp temp) {
      return temp.index();
    }
    return -1;
  }

  private int maxTempInValues(List<IrProgramModel.Value> values) {
    int max = -1;
    for (IrProgramModel.Value value : values) {
      max = Math.max(max, maxTempInValue(value));
    }
    return max;
  }

  private int callArgsInInstruction(IrProgramModel.Instruction instruction) {
    if (instruction instanceof IrProgramModel.VoidCall call) {
      return call.args().size();
    }
    if (instruction instanceof IrProgramModel.Assign assign && assign.rhs() instanceof IrProgramModel.Call call) {
      return call.args().size();
    }
    return 0;
  }

  private IrType resultType(
      IrProgramModel.Rhs rhs,
      Map<String, IrProgramModel.Slot> localSlots,
      Map<String, IrProgramModel.Slot> paramSlots,
      Map<String, IrType> globalTypes) {
    if (rhs instanceof IrProgramModel.ConstRhs constRhs) {
      return constRhs.constant().type();
    }
    if (rhs instanceof IrProgramModel.AddrOfSymbol addr) {
      return new IrPointerType(symbolType(addr.symbolRef(), localSlots, paramSlots, globalTypes));
    }
    if (rhs instanceof IrProgramModel.Load load) {
      return load.loadType();
    }
    if (rhs instanceof IrProgramModel.BinOp binOp) {
      return binOp.resultType();
    }
    if (rhs instanceof IrProgramModel.CmpOp) {
      return IrPrimitiveType.BOOL;
    }
    if (rhs instanceof IrProgramModel.Call call) {
      return call.resultType();
    }
    if (rhs instanceof IrProgramModel.UnaryOp unaryOp) {
      return unaryOp.resultType();
    }
    if (rhs instanceof IrProgramModel.CastOp castOp) {
      return castOp.resultType();
    }
    if (rhs instanceof IrProgramModel.AddrIndex) {
      return new IrPointerType(IrPrimitiveType.INT32);
    }
    if (rhs instanceof IrProgramModel.AddrField) {
      return new IrPointerType(IrPrimitiveType.INT32);
    }
    return IrPrimitiveType.INT32;
  }

  private IrType symbolType(
      IrProgramModel.SymbolRef symbolRef,
      Map<String, IrProgramModel.Slot> localSlots,
      Map<String, IrProgramModel.Slot> paramSlots,
      Map<String, IrType> globalTypes) {
    return switch (symbolRef.kind()) {
      case LOCAL -> localSlots.get(symbolRef.name()).type();
      case PARAM -> paramSlots.get(symbolRef.name()).type();
      case GLOBAL -> globalTypes.get(symbolRef.name());
    };
  }

  private void emitHelpers(FriscEmitter emitter) {
    HelperGenerator helpers = new HelperGenerator(emitter);
    if (emitter.needsFmul()) {
      helpers.emitFloatMul();
    }
    if (emitter.needsFdiv()) {
      helpers.emitFloatDiv();
    }
    if (emitter.needsF2i()) {
      helpers.emitFloatToInt();
    }
    if (emitter.needsI2f()) {
      helpers.emitIntToFloat();
    }
    if (emitter.needsMul()) {
      helpers.emitMul();
    }
    if (emitter.needsDiv()) {
      helpers.emitDiv();
    }
    if (emitter.needsMod()) {
      helpers.emitMod();
    }
    if (emitter.needsBoundsCheck()) {
      helpers.emitBoundsError();
    }
  }

  private static boolean isChar(IrType type) {
    return type == IrPrimitiveType.CHAR || type == IrPrimitiveType.UCHAR;
  }

  private static boolean isFloat(IrType type) {
    return type == IrPrimitiveType.FLOAT;
  }

  private static boolean isAggregate(IrType type) {
    return type instanceof IrStructType || type instanceof IrArrayType;
  }

  private static int floatToQ16_16(float value) {
    return Math.round(value * 65536.0f);
  }

  private static int sizeOf(IrType type, StructLayoutRegistry structLayouts) {
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
    if (type instanceof IrArrayType arrayType) {
      return arrayType.size() * sizeOf(arrayType.elementType(), structLayouts);
    }
    if (type instanceof IrStructType structType) {
      return structLayouts.getStructSize(structType.name());
    }
    return 4;
  }

  private static int alignmentOf(IrType type) {
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
    if (type instanceof IrArrayType arrayType) {
      return alignmentOf(arrayType.elementType());
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

  private static String formatImmediate(int value) {
    if (value == 0) {
      return "0";
    }
    if (value < 0) {
      return "-" + formatHexMagnitude(-value);
    }
    return formatHexMagnitude(value);
  }

  private static boolean fitsSigned20(int value) {
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

  private static final class TempAnalysis {
    private final Map<Integer, IrType> tempTypes;
    private final int maxTempIndex;
    private final int maxCallArgs;

    private TempAnalysis(Map<Integer, IrType> tempTypes, int maxTempIndex, int maxCallArgs) {
      this.tempTypes = tempTypes;
      this.maxTempIndex = maxTempIndex;
      this.maxCallArgs = maxCallArgs;
    }
  }

  private record PointerScratch(
      Map<String, Map<String, String>> labelsByFunction,
      List<Scratch> scratches) {
  }

  private record Scratch(
      String label,
      int size,
      int alignment,
      String comment) {
  }

  private record ParamLayout(List<ParamInfo> params, int totalSize) {
  }

  private record ParamInfo(IrType type, int offset) {
  }

  private static final class FunctionContext {
    private final IrProgramModel.Function function;
    private final FriscEmitter emitter;
    private final StructLayoutRegistry structLayouts;
    private final Map<String, IrType> globalTypes;
    private final Map<String, IrProgramModel.Slot> localSlots;
    private final Map<String, IrProgramModel.Slot> paramSlots;
    private final Map<String, String> functionLabels;
    private final Map<String, ParamLayout> functionParamLayouts;
    private final Set<Integer> addrIndexNeedsCheck;
    private final Map<Integer, IrType> tempTypes;
    private final Map<Integer, Integer> tempOffsets = new HashMap<>();
    private final Map<Integer, Integer> argOffsets = new HashMap<>();
    private final int frameSize;
    private final int localsBytes;
    private final int tempCount;
    private final int argScratchCount;
    private final String exitLabel;
    private final Map<String, String> blockLabels;

    private FunctionContext(
        IrProgramModel.Function function,
        FriscEmitter emitter,
        StructLayoutRegistry structLayouts,
        Map<String, IrType> globalTypes,
        Map<String, IrProgramModel.Slot> localSlots,
        Map<String, IrProgramModel.Slot> paramSlots,
        Map<String, String> functionLabels,
        Map<String, ParamLayout> functionParamLayouts,
        Set<Integer> addrIndexNeedsCheck,
        Map<Integer, IrType> tempTypes,
        int frameSize,
        int localsBytes,
        int tempCount,
        int argScratchCount,
        String exitLabel,
        Map<String, String> blockLabels) {
      this.function = function;
      this.emitter = emitter;
      this.structLayouts = structLayouts;
      this.globalTypes = globalTypes;
      this.localSlots = localSlots;
      this.paramSlots = paramSlots;
      this.functionLabels = functionLabels;
      this.functionParamLayouts = functionParamLayouts;
      this.addrIndexNeedsCheck = addrIndexNeedsCheck;
      this.tempTypes = tempTypes;
      this.frameSize = frameSize;
      this.localsBytes = localsBytes;
      this.tempCount = tempCount;
      this.argScratchCount = argScratchCount;
      this.exitLabel = exitLabel;
      this.blockLabels = blockLabels;

      int tempBase = localsBytes;
      for (int i = 0; i < tempCount; i++) {
        tempOffsets.put(i, tempBase + i * 4 + 4);
      }

      int argBase = localsBytes + tempCount * 4;
      for (int i = 0; i < argScratchCount; i++) {
        argOffsets.put(i, argBase + i * 4 + 4);
      }
    }
  }

  private static final class StructLayoutRegistry {
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

  private static final class HelperGenerator {
    private final FriscEmitter emitter;
    private int labelCounter;

    private HelperGenerator(FriscEmitter emitter) {
      this.emitter = emitter;
    }

    private String newLabel(String prefix) {
      labelCounter += 1;
      return prefix + "_" + labelCounter;
    }

    private void emitMul() {
      emitter.emitLabel("F_MUL", "int32 multiplication");
      emitter.emitInstruction("PUSH", List.of("R5"), null);
      emitter.emitInstruction("MOVE", List.of("R7", "R5"), null);
      emitter.emitInstruction("LOAD", List.of("R0", "(R5+" + formatImmediate(8) + ")"), "a");
      emitter.emitInstruction("LOAD", List.of("R1", "(R5+" + formatImmediate(12) + ")"), "b");
      emitter.emitInstruction("MOVE", List.of("0", "R4"), "zero");
      emitter.emitInstruction("MOVE", List.of("0", "R2"), "sign");
      emitter.emitInstruction("CMP", List.of("R0", "0"), null);
      String aPos = newLabel("L_MUL_A_POS");
      String bPos = newLabel("L_MUL_B_POS");
      String done = newLabel("L_MUL_DONE");
      String skipAdd = newLabel("L_MUL_SKIP");
      emitter.emitInstruction("JP_SGE", List.of(aPos), null);
      emitter.emitInstruction("SUB", List.of("R4", "R0", "R0"), null);
      emitter.emitInstruction("XOR", List.of("R2", "1", "R2"), null);
      emitter.emitLabel(aPos, null);
      emitter.emitInstruction("CMP", List.of("R1", "0"), null);
      emitter.emitInstruction("JP_SGE", List.of(bPos), null);
      emitter.emitInstruction("SUB", List.of("R4", "R1", "R1"), null);
      emitter.emitInstruction("XOR", List.of("R2", "1", "R2"), null);
      emitter.emitLabel(bPos, null);
      emitter.emitInstruction("MOVE", List.of("0", "R3"), "result");
      String loop = newLabel("L_MUL_LOOP");
      emitter.emitLabel(loop, null);
      emitter.emitInstruction("CMP", List.of("R1", "0"), null);
      emitter.emitInstruction("JP_EQ", List.of(done), null);
      emitter.emitInstruction("AND", List.of("R1", "1", "R4"), null);
      emitter.emitInstruction("CMP", List.of("R4", "0"), null);
      emitter.emitInstruction("JP_EQ", List.of(skipAdd), null);
      emitter.emitInstruction("ADD", List.of("R3", "R0", "R3"), null);
      emitter.emitLabel(skipAdd, null);
      emitter.emitInstruction("SHL", List.of("R0", "1", "R0"), null);
      emitter.emitInstruction("SHR", List.of("R1", "1", "R1"), null);
      emitter.emitInstruction("JP", List.of(loop), null);
      emitter.emitLabel(done, null);
      String signDone = newLabel("L_MUL_SIGN_DONE");
      emitter.emitInstruction("CMP", List.of("R2", "0"), null);
      emitter.emitInstruction("JP_EQ", List.of(signDone), null);
      emitter.emitInstruction("MOVE", List.of("0", "R4"), "zero");
      emitter.emitInstruction("SUB", List.of("R4", "R3", "R3"), null);
      emitter.emitLabel(signDone, null);
      emitter.emitInstruction("MOVE", List.of("R3", "R6"), null);
      emitter.emitInstruction("POP", List.of("R5"), null);
      emitter.emitInstruction("RET", List.of(), null);
    }

    private void emitDiv() {
      emitter.emitLabel("F_DIV", "int32 division");
      emitter.emitInstruction("PUSH", List.of("R5"), null);
      emitter.emitInstruction("MOVE", List.of("R7", "R5"), null);
      emitter.emitInstruction("LOAD", List.of("R0", "(R5+" + formatImmediate(8) + ")"), "dividend");
      emitter.emitInstruction("LOAD", List.of("R1", "(R5+" + formatImmediate(12) + ")"), "divisor");
      emitter.emitInstruction("MOVE", List.of("0", "R4"), "zero");
      emitter.emitInstruction("CMP", List.of("R1", "0"), null);
      String divByZero = newLabel("L_DIV_ZERO");
      emitter.emitInstruction("JP_EQ", List.of(divByZero), null);
      emitter.emitInstruction("MOVE", List.of("0", "R6"), "sign");
      String aPos = newLabel("L_DIV_A_POS");
      emitter.emitInstruction("CMP", List.of("R0", "0"), null);
      emitter.emitInstruction("JP_SGE", List.of(aPos), null);
      emitter.emitInstruction("SUB", List.of("R4", "R0", "R0"), null);
      emitter.emitInstruction("XOR", List.of("R6", "1", "R6"), null);
      emitter.emitLabel(aPos, null);
      String bPos = newLabel("L_DIV_B_POS");
      emitter.emitInstruction("CMP", List.of("R1", "0"), null);
      emitter.emitInstruction("JP_SGE", List.of(bPos), null);
      emitter.emitInstruction("SUB", List.of("R4", "R1", "R1"), null);
      emitter.emitInstruction("XOR", List.of("R6", "1", "R6"), null);
      emitter.emitLabel(bPos, null);

      emitter.emitInstruction("MOVE", List.of("0", "R2"), "remainder");
      emitter.emitInstruction("MOVE", List.of("0", "R3"), "quotient");
      emitter.emitInstruction("MOVE", List.of("20", "R4"), "bit count (32)");
      String loop = newLabel("L_DIV_LOOP");
      String noCarry = newLabel("L_DIV_NO_CARRY");
      String afterCarry = newLabel("L_DIV_AFTER_CARRY");
      String skipSub = newLabel("L_DIV_SKIP_SUB");
      emitter.emitLabel(loop, null);
      emitter.emitInstruction("SHL", List.of("R0", "1", "R0"), null);
      emitter.emitInstruction("JP_NC", List.of(noCarry), null);
      emitter.emitInstruction("SHL", List.of("R2", "1", "R2"), null);
      emitter.emitInstruction("OR", List.of("R2", "1", "R2"), null);
      emitter.emitInstruction("JP", List.of(afterCarry), null);
      emitter.emitLabel(noCarry, null);
      emitter.emitInstruction("SHL", List.of("R2", "1", "R2"), null);
      emitter.emitLabel(afterCarry, null);
      emitter.emitInstruction("SHL", List.of("R3", "1", "R3"), null);
      emitter.emitInstruction("CMP", List.of("R2", "R1"), null);
      emitter.emitInstruction("JP_SLT", List.of(skipSub), null);
      emitter.emitInstruction("SUB", List.of("R2", "R1", "R2"), null);
      emitter.emitInstruction("OR", List.of("R3", "1", "R3"), null);
      emitter.emitLabel(skipSub, null);
      emitter.emitInstruction("SUB", List.of("R4", "1", "R4"), null);
      emitter.emitInstruction("JP_NE", List.of(loop), null);

      String signDone = newLabel("L_DIV_SIGN_DONE");
      emitter.emitInstruction("CMP", List.of("R6", "0"), null);
      emitter.emitInstruction("JP_EQ", List.of(signDone), null);
      emitter.emitInstruction("SUB", List.of("R4", "R3", "R3"), null);
      emitter.emitLabel(signDone, null);
      emitter.emitInstruction("MOVE", List.of("R3", "R6"), null);
      emitter.emitInstruction("POP", List.of("R5"), null);
      emitter.emitInstruction("RET", List.of(), null);
      emitter.emitLabel(divByZero, null);
      emitter.emitInstruction("MOVE", List.of("0", "R6"), null);
      emitter.emitInstruction("POP", List.of("R5"), null);
      emitter.emitInstruction("RET", List.of(), null);
    }

    private void emitMod() {
      emitter.emitLabel("F_MOD", "int32 modulo");
      emitter.emitInstruction("PUSH", List.of("R5"), null);
      emitter.emitInstruction("MOVE", List.of("R7", "R5"), null);
      emitter.emitInstruction("LOAD", List.of("R0", "(R5+" + formatImmediate(8) + ")"), "dividend");
      emitter.emitInstruction("LOAD", List.of("R1", "(R5+" + formatImmediate(12) + ")"), "divisor");
      emitter.emitInstruction("MOVE", List.of("0", "R4"), "zero");
      emitter.emitInstruction("CMP", List.of("R1", "0"), null);
      String modByZero = newLabel("L_MOD_ZERO");
      emitter.emitInstruction("JP_EQ", List.of(modByZero), null);
      emitter.emitInstruction("MOVE", List.of("0", "R6"), "sign");
      String aPos = newLabel("L_MOD_A_POS");
      emitter.emitInstruction("CMP", List.of("R0", "0"), null);
      emitter.emitInstruction("JP_SGE", List.of(aPos), null);
      emitter.emitInstruction("SUB", List.of("R4", "R0", "R0"), null);
      emitter.emitInstruction("MOVE", List.of("1", "R6"), null);
      emitter.emitLabel(aPos, null);
      String bPos = newLabel("L_MOD_B_POS");
      emitter.emitInstruction("CMP", List.of("R1", "0"), null);
      emitter.emitInstruction("JP_SGE", List.of(bPos), null);
      emitter.emitInstruction("SUB", List.of("R4", "R1", "R1"), null);
      emitter.emitLabel(bPos, null);

      emitter.emitInstruction("MOVE", List.of("0", "R2"), "remainder");
      emitter.emitInstruction("MOVE", List.of("20", "R4"), "bit count (32)");
      String loop = newLabel("L_MOD_LOOP");
      String noCarry = newLabel("L_MOD_NO_CARRY");
      String afterCarry = newLabel("L_MOD_AFTER_CARRY");
      String skipSub = newLabel("L_MOD_SKIP_SUB");
      emitter.emitLabel(loop, null);
      emitter.emitInstruction("SHL", List.of("R0", "1", "R0"), null);
      emitter.emitInstruction("JP_NC", List.of(noCarry), null);
      emitter.emitInstruction("SHL", List.of("R2", "1", "R2"), null);
      emitter.emitInstruction("OR", List.of("R2", "1", "R2"), null);
      emitter.emitInstruction("JP", List.of(afterCarry), null);
      emitter.emitLabel(noCarry, null);
      emitter.emitInstruction("SHL", List.of("R2", "1", "R2"), null);
      emitter.emitLabel(afterCarry, null);
      emitter.emitInstruction("CMP", List.of("R2", "R1"), null);
      emitter.emitInstruction("JP_SLT", List.of(skipSub), null);
      emitter.emitInstruction("SUB", List.of("R2", "R1", "R2"), null);
      emitter.emitLabel(skipSub, null);
      emitter.emitInstruction("SUB", List.of("R4", "1", "R4"), null);
      emitter.emitInstruction("JP_NE", List.of(loop), null);

      String signDone = newLabel("L_MOD_SIGN_DONE");
      emitter.emitInstruction("CMP", List.of("R6", "0"), null);
      emitter.emitInstruction("JP_EQ", List.of(signDone), null);
      emitter.emitInstruction("SUB", List.of("R4", "R2", "R2"), null);
      emitter.emitLabel(signDone, null);
      emitter.emitInstruction("MOVE", List.of("R2", "R6"), null);
      emitter.emitInstruction("POP", List.of("R5"), null);
      emitter.emitInstruction("RET", List.of(), null);
      emitter.emitLabel(modByZero, null);
      emitter.emitInstruction("MOVE", List.of("0", "R6"), null);
      emitter.emitInstruction("POP", List.of("R5"), null);
      emitter.emitInstruction("RET", List.of(), null);
    }

    private void emitBoundsError() {
      emitter.emitLabel("L_BOUNDS_ERROR", "array bounds error");
      emitter.emitInstruction("MOVE", List.of(formatImmediate(-6), "R6"), "Error code");
      emitter.emitInstruction("HALT", List.of(), "Abort");
    }

    private void emitFloatMul() {
      emitter.emitLabel("F_FMUL", "Q16.16 float multiplication");
      emitter.emitInstruction("PUSH", List.of("R5"), null);
      emitter.emitInstruction("MOVE", List.of("R7", "R5"), null);
      emitter.emitInstruction("LOAD", List.of("R0", "(R5+" + formatImmediate(8) + ")"), "a");
      emitter.emitInstruction("LOAD", List.of("R1", "(R5+" + formatImmediate(12) + ")"), "b");
      emitter.emitInstruction("MOVE", List.of("0", "R6"), "sign");
      emitter.emitInstruction("MOVE", List.of("0", "R3"), "zero");
      String aPos = newLabel("L_FMUL_A_POS");
      String bPos = newLabel("L_FMUL_B_POS");
      emitter.emitInstruction("CMP", List.of("R0", "0"), null);
      emitter.emitInstruction("JP_SGE", List.of(aPos), null);
      emitter.emitInstruction("SUB", List.of("R3", "R0", "R0"), null);
      emitter.emitInstruction("XOR", List.of("R6", "1", "R6"), null);
      emitter.emitLabel(aPos, null);
      emitter.emitInstruction("CMP", List.of("R1", "0"), null);
      emitter.emitInstruction("JP_SGE", List.of(bPos), null);
      emitter.emitInstruction("SUB", List.of("R3", "R1", "R1"), null);
      emitter.emitInstruction("XOR", List.of("R6", "1", "R6"), null);
      emitter.emitLabel(bPos, null);
      emitter.emitInstruction("MOVE", List.of("0", "R2"), "prod low");
      emitter.emitInstruction("MOVE", List.of("0", "R3"), "prod high");
      emitter.emitInstruction("MOVE", List.of("0", "R4"), "multiplicand high");

      String loop = newLabel("L_FMUL_LOOP");
      String done = newLabel("L_FMUL_DONE");
      String skipAdd = newLabel("L_FMUL_SKIP");
      emitter.emitLabel(loop, null);
      emitter.emitInstruction("CMP", List.of("R1", "0"), null);
      emitter.emitInstruction("JP_EQ", List.of(done), null);
      emitter.emitInstruction("SHR", List.of("R1", "1", "R1"), null);
      emitter.emitInstruction("JP_NC", List.of(skipAdd), null);
      emitter.emitInstruction("ADD", List.of("R2", "R0", "R2"), null);
      emitter.emitInstruction("ADC", List.of("R3", "R4", "R3"), null);
      emitter.emitLabel(skipAdd, null);
      emitter.emitInstruction("SHL", List.of("R0", "1", "R0"), null);
      emitter.emitInstruction("ADC", List.of("R4", "R4", "R4"), null);
      emitter.emitInstruction("JP", List.of(loop), null);
      emitter.emitLabel(done, null);
      emitter.emitInstruction("SHR", List.of("R2", "10", "R2"), "lo >> 16");
      emitter.emitInstruction("SHL", List.of("R3", "10", "R3"), "hi << 16");
      emitter.emitInstruction("OR", List.of("R3", "R2", "R2"), "combine");
      String signDone = newLabel("L_FMUL_SIGN_DONE");
      emitter.emitInstruction("CMP", List.of("R6", "0"), null);
      emitter.emitInstruction("JP_EQ", List.of(signDone), null);
      emitter.emitInstruction("MOVE", List.of("0", "R1"), null);
      emitter.emitInstruction("SUB", List.of("R1", "R2", "R2"), null);
      emitter.emitLabel(signDone, null);
      emitter.emitInstruction("MOVE", List.of("R2", "R6"), null);
      emitter.emitInstruction("POP", List.of("R5"), null);
      emitter.emitInstruction("RET", List.of(), null);
    }

    private void emitFloatDiv() {
      emitter.emitLabel("F_FDIV", "Q16.16 float division");
      emitter.emitInstruction("PUSH", List.of("R5"), null);
      emitter.emitInstruction("MOVE", List.of("R7", "R5"), null);
      emitter.emitInstruction("LOAD", List.of("R0", "(R5+" + formatImmediate(8) + ")"), "a");
      emitter.emitInstruction("LOAD", List.of("R1", "(R5+" + formatImmediate(12) + ")"), "b");
      emitter.emitInstruction("MOVE", List.of("0", "R6"), "sign");
      emitter.emitInstruction("MOVE", List.of("0", "R4"), "zero");
      String divByZero = newLabel("L_FDIV_ZERO");
      emitter.emitInstruction("CMP", List.of("R1", "0"), null);
      emitter.emitInstruction("JP_EQ", List.of(divByZero), null);

      String aPos = newLabel("L_FDIV_A_POS");
      String bPos = newLabel("L_FDIV_B_POS");
      emitter.emitInstruction("CMP", List.of("R0", "0"), null);
      emitter.emitInstruction("JP_SGE", List.of(aPos), null);
      emitter.emitInstruction("SUB", List.of("R4", "R0", "R0"), null);
      emitter.emitInstruction("XOR", List.of("R6", "1", "R6"), null);
      emitter.emitLabel(aPos, null);
      emitter.emitInstruction("CMP", List.of("R1", "0"), null);
      emitter.emitInstruction("JP_SGE", List.of(bPos), null);
      emitter.emitInstruction("SUB", List.of("R4", "R1", "R1"), null);
      emitter.emitInstruction("XOR", List.of("R6", "1", "R6"), null);
      emitter.emitLabel(bPos, null);

      emitter.emitInstruction("PUSH", List.of("R6"), "Save sign");
      emitter.emitInstruction("PUSH", List.of("R0"), "Save a");
      emitter.emitInstruction("PUSH", List.of("R1"), "Save b");

      emitter.emitInstruction("PUSH", List.of("R1"), "Arg right");
      emitter.emitInstruction("PUSH", List.of("R0"), "Arg left");
      emitter.emitInstruction("CALL", List.of("F_DIV"), null);
      emitter.emitInstruction("ADD", List.of("R7", "8", "R7"), "Clean args");
      emitter.emitInstruction("MOVE", List.of("R6", "R3"), "Integer part");

      emitter.emitInstruction("POP", List.of("R1"), "Restore b");
      emitter.emitInstruction("POP", List.of("R0"), "Restore a");
      emitter.emitInstruction("PUSH", List.of("R1"), "Save b");

      emitter.emitInstruction("PUSH", List.of("R1"), "Arg right");
      emitter.emitInstruction("PUSH", List.of("R0"), "Arg left");
      emitter.emitInstruction("CALL", List.of("F_MOD"), null);
      emitter.emitInstruction("ADD", List.of("R7", "8", "R7"), "Clean args");
      emitter.emitInstruction("POP", List.of("R4"), "Restore b");
      emitter.emitInstruction("MOVE", List.of("R6", "R2"), "Remainder");

      emitter.emitInstruction("POP", List.of("R6"), "Restore sign");

      emitter.emitInstruction("MOVE", List.of("0", "R1"), "Fraction");
      emitter.emitInstruction("MOVE", List.of("10", "R0"), "Loop count (16)");
      String fracLoop = newLabel("L_FDIV_FRAC_LOOP");
      String fracDone = newLabel("L_FDIV_FRAC_DONE");
      String fracSkip = newLabel("L_FDIV_FRAC_SKIP");
      emitter.emitLabel(fracLoop, null);
      emitter.emitInstruction("CMP", List.of("R0", "0"), null);
      emitter.emitInstruction("JP_EQ", List.of(fracDone), null);
      emitter.emitInstruction("SHL", List.of("R2", "1", "R2"), null);
      emitter.emitInstruction("SHL", List.of("R1", "1", "R1"), null);
      emitter.emitInstruction("CMP", List.of("R2", "R4"), null);
      emitter.emitInstruction("JP_SLT", List.of(fracSkip), null);
      emitter.emitInstruction("SUB", List.of("R2", "R4", "R2"), null);
      emitter.emitInstruction("OR", List.of("R1", "1", "R1"), null);
      emitter.emitLabel(fracSkip, null);
      emitter.emitInstruction("SUB", List.of("R0", "1", "R0"), null);
      emitter.emitInstruction("JP", List.of(fracLoop), null);
      emitter.emitLabel(fracDone, null);

      emitter.emitInstruction("SHL", List.of("R3", "10", "R3"), "int << 16");
      emitter.emitInstruction("OR", List.of("R3", "R1", "R3"), "combine");
      String signDone = newLabel("L_FDIV_SIGN_DONE");
      emitter.emitInstruction("CMP", List.of("R6", "0"), null);
      emitter.emitInstruction("JP_EQ", List.of(signDone), null);
      emitter.emitInstruction("MOVE", List.of("0", "R0"), null);
      emitter.emitInstruction("SUB", List.of("R0", "R3", "R3"), null);
      emitter.emitLabel(signDone, null);
      emitter.emitInstruction("MOVE", List.of("R3", "R6"), null);
      emitter.emitInstruction("POP", List.of("R5"), null);
      emitter.emitInstruction("RET", List.of(), null);

      emitter.emitLabel(divByZero, null);
      emitter.emitInstruction("MOVE", List.of("0", "R6"), null);
      emitter.emitInstruction("POP", List.of("R5"), null);
      emitter.emitInstruction("RET", List.of(), null);
    }

    private void emitFloatToInt() {
      emitter.emitLabel("F_F2I", "Q16.16 to int32");
      emitter.emitInstruction("PUSH", List.of("R5"), null);
      emitter.emitInstruction("MOVE", List.of("R7", "R5"), null);
      emitter.emitInstruction("LOAD", List.of("R0", "(R5+" + formatImmediate(8) + ")"), null);
      emitter.emitInstruction("SHR", List.of("R0", "10", "R0"), null);
      emitter.emitInstruction("MOVE", List.of("R0", "R6"), null);
      emitter.emitInstruction("POP", List.of("R5"), null);
      emitter.emitInstruction("RET", List.of(), null);
    }

    private void emitIntToFloat() {
      emitter.emitLabel("F_I2F", "int32 to Q16.16");
      emitter.emitInstruction("PUSH", List.of("R5"), null);
      emitter.emitInstruction("MOVE", List.of("R7", "R5"), null);
      emitter.emitInstruction("LOAD", List.of("R0", "(R5+" + formatImmediate(8) + ")"), null);
      emitter.emitInstruction("SHL", List.of("R0", "10", "R0"), null);
      emitter.emitInstruction("MOVE", List.of("R0", "R6"), null);
      emitter.emitInstruction("POP", List.of("R5"), null);
      emitter.emitInstruction("RET", List.of(), null);
    }
  }
}
