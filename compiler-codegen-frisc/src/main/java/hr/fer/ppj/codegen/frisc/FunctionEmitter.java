package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.frisc.analysis.TempAnalyzer;
import hr.fer.ppj.codegen.frisc.analysis.TempAnalyzer.TempAnalysisResult;
import hr.fer.ppj.codegen.frisc.emitter.FriscEmitter;
import hr.fer.ppj.codegen.frisc.frame.ParamLayout;
import hr.fer.ppj.codegen.frisc.frame.StructLayoutRegistry;
import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.codegen.frisc.lowering.AddressLowerer;
import hr.fer.ppj.codegen.frisc.lowering.FunctionContext;
import hr.fer.ppj.codegen.frisc.lowering.ImmediateEmitter;
import hr.fer.ppj.codegen.frisc.lowering.LoweringSupport;
import hr.fer.ppj.codegen.frisc.lowering.StatementLowerer;
import hr.fer.ppj.codegen.frisc.util.LabelGenerator;
import hr.fer.ppj.ir.types.IrType;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Emits a single function body and its prologue/epilogue.
 */
final class FunctionEmitter {

  private final LabelGenerator labelGenerator;
  private final ImmediateEmitter immediateEmitter;
  private final AddressLowerer addressLowerer;
  private final StatementLowerer statementLowerer;
  private final TempAnalyzer tempAnalyzer;

  FunctionEmitter(
      LabelGenerator labelGenerator,
      ImmediateEmitter immediateEmitter,
      AddressLowerer addressLowerer,
      StatementLowerer statementLowerer,
      TempAnalyzer tempAnalyzer) {
    this.labelGenerator = labelGenerator;
    this.immediateEmitter = immediateEmitter;
    this.addressLowerer = addressLowerer;
    this.statementLowerer = statementLowerer;
    this.tempAnalyzer = tempAnalyzer;
  }

  void emit(
      IrProgramModel.Function function,
      FriscEmitter emitter,
      StructLayoutRegistry structLayouts,
      Map<String, IrType> globalTypes,
      Map<String, String> functionLabels,
      Map<String, ParamLayout> functionParamLayouts,
      Map<String, String> scratchLabels) {

    String funcLabel = labelGenerator.functionLabel(function.name());
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

    TempAnalysisResult analysis = tempAnalyzer.analyze(function, localSlots, paramSlots, globalTypes);
    Set<Integer> addrIndexNeedsCheck = analysis.addrIndexNeedsCheck();
    int tempCount = analysis.tempAnalysis().maxTempIndex() + 1;
    int argScratchCount = analysis.tempAnalysis().maxCallArgs();

    int localsAreaSize = function.localsBytes();
    if (tempCount > 0 || argScratchCount > 0) {
      // Word temps/arg-scratch must start below the byte-oriented local zone.
      // Without this padding, byte stores into trailing locals can clobber temp words.
      localsAreaSize = LoweringSupport.alignTo(function.localsBytes() + 3, 4);
    }
    int tempAreaSize = tempCount * 4;
    int argScratchSize = argScratchCount * 4;
    int frameSize = LoweringSupport.alignTo(localsAreaSize + tempAreaSize + argScratchSize, 4);

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
        analysis.tempAnalysis().tempTypes(),
        frameSize,
        localsAreaSize,
        tempCount,
        argScratchCount,
        exitLabel,
        buildBlockLabelMap(function));

    emitter.emitLabel(funcLabel, "Function: " + function.name());
    emitter.emitInstruction("PUSH", List.of("R5"), "Save old FP");
    emitter.emitInstruction("MOVE", List.of("R7", "R5"), "Set FP");
    if (frameSize > 0) {
      emitter.emitInstruction("SUB", List.of("R7", LoweringSupport.formatImmediate(frameSize), "R7"), "Allocate locals/temps");
      int localZeroBytes = LoweringSupport.alignTo(localsAreaSize, 4);
      int wordCount = localZeroBytes / 4;
      if (wordCount > 0) {
        immediateEmitter.emitLoadImmediate(wordCount, ctx, "R1", "Zero local words");
        ctx.emitter().emitInstruction("MOVE", List.of("R5", "R0"), "Local zero base");
        ctx.emitter().emitInstruction(
            "SUB",
            List.of("R0", LoweringSupport.formatImmediate(localZeroBytes), "R0"),
            "Local zero ptr");
        immediateEmitter.emitLoadImmediate(0, ctx, "R2", "Zero");
        String zeroLoop = labelGenerator.newLabel("L_ZERO");
        ctx.emitter().emitLabel(zeroLoop, null);
        ctx.emitter().emitInstruction("STORE", List.of("R2", "(R0)"), "Clear");
        ctx.emitter().emitInstruction("ADD", List.of("R0", "4", "R0"), null);
        ctx.emitter().emitInstruction("SUB", List.of("R1", "1", "R1"), null);
        ctx.emitter().emitInstruction("JP_NE", List.of(zeroLoop), null);
      }
    }

    if (!scratchLabels.isEmpty()) {
      ctx.emitter().emitComment("Initialize pointer locals to scratch storage");
      for (Map.Entry<String, String> entry : scratchLabels.entrySet()) {
        IrProgramModel.SymbolRef symbolRef =
            new IrProgramModel.SymbolRef(IrProgramModel.SymbolRefKind.LOCAL, entry.getKey());
        addressLowerer.emitAddrOfSymbol(symbolRef, ctx, "R0");
        ctx.emitter().emitInstruction("MOVE", List.of(entry.getValue(), "R1"), "Pointer scratch");
        ctx.emitter().emitInstruction("STORE", List.of("R1", "(R0)"), "Init pointer local");
      }
    }

    for (IrProgramModel.Block block : function.blocks()) {
      String blockLabel = ctx.blockLabels().get(block.label());
      emitter.emitLabel(blockLabel, null);
      for (IrProgramModel.Instruction instruction : block.instructions()) {
        emitInstruction(instruction, ctx);
      }
      emitTerminator(block.terminator(), ctx);
    }

    emitter.emitLabel(exitLabel, "Function epilogue");
    if (frameSize > 0) {
      emitter.emitInstruction("ADD", List.of("R7", LoweringSupport.formatImmediate(frameSize), "R7"), "Deallocate locals/temps");
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
    statementLowerer.emitInstruction(instruction, ctx);
  }

  private void emitTerminator(IrProgramModel.Terminator terminator, FunctionContext ctx) {
    statementLowerer.emitTerminator(terminator, ctx);
  }
}
