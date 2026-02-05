package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.frisc.analysis.PointerScratch;
import hr.fer.ppj.codegen.frisc.analysis.PointerScratchCollector;
import hr.fer.ppj.codegen.frisc.emitter.FriscEmitter;
import hr.fer.ppj.codegen.frisc.frame.ParamLayout;
import hr.fer.ppj.codegen.frisc.frame.ParamLayoutBuilder;
import hr.fer.ppj.codegen.frisc.frame.StructLayoutRegistry;
import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.codegen.frisc.util.LabelGenerator;
import hr.fer.ppj.ir.types.IrType;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Emits top-level program structure: header, entry point, functions, helpers, and globals.
 */
final class ProgramEmitter {

  private final LabelGenerator labelGenerator;
  private final FunctionEmitter functionEmitter;
  private final GlobalsEmitter globalsEmitter;
  private final HelperEmitter helperEmitter;
  private final PointerScratchCollector pointerScratchCollector;
  private final ParamLayoutBuilder paramLayoutBuilder;

  ProgramEmitter(
      LabelGenerator labelGenerator,
      FunctionEmitter functionEmitter,
      GlobalsEmitter globalsEmitter,
      HelperEmitter helperEmitter,
      PointerScratchCollector pointerScratchCollector,
      ParamLayoutBuilder paramLayoutBuilder) {
    this.labelGenerator = labelGenerator;
    this.functionEmitter = functionEmitter;
    this.globalsEmitter = globalsEmitter;
    this.helperEmitter = helperEmitter;
    this.pointerScratchCollector = pointerScratchCollector;
    this.paramLayoutBuilder = paramLayoutBuilder;
  }

  void emit(IrProgramModel program, FriscEmitter emitter, String sourceName) {
    String src = sourceName == null || sourceName.isBlank() ? "unknown" : sourceName;
    String timestamp = LocalDateTime.now().format(FriscCodeGenerator.TIMESTAMP_FORMAT);

    emitter.emitComment("===========================================================================");
    emitter.emitComment("PPJ Compiler - Generated FRISC Assembly Code");
    emitter.emitComment("Source: " + src);
    emitter.emitComment("Generated: " + timestamp);
    emitter.emitComment("===========================================================================");

    emitter.emitSectionHeader("Program entry point and initialization");
    emitter.emitInstruction("MOVE", java.util.List.of("40000", "R7"), "Initialize stack pointer (SP)");
    emitter.emitInstruction("CALL", java.util.List.of(labelGenerator.functionLabel("main")), "Call main");
    emitter.emitInstruction("HALT", java.util.List.of(), "Program end");

    emitter.emitSectionHeader("Function Definitions");

    StructLayoutRegistry structLayouts = new StructLayoutRegistry();
    for (IrProgramModel.StructDef structDef : program.structDefs()) {
      structLayouts.register(structDef);
    }

    Map<String, String> functionLabels = new HashMap<>();
    for (IrProgramModel.Function func : program.functions()) {
      functionLabels.put(func.name(), labelGenerator.functionLabel(func.name()));
    }

    Map<String, IrType> globalTypes = new HashMap<>();
    for (IrProgramModel.GlobalVar global : program.globals()) {
      globalTypes.put(global.name(), global.type());
    }

    PointerScratch pointerScratch = pointerScratchCollector.collect(program, structLayouts);
    Map<String, ParamLayout> paramLayouts = paramLayoutBuilder.build(program, structLayouts);

    for (IrProgramModel.Function function : program.functions()) {
      Map<String, String> scratchLabels = pointerScratch.labelsByFunction().getOrDefault(function.name(), Map.of());
      functionEmitter.emit(function, emitter, structLayouts, globalTypes, functionLabels, paramLayouts, scratchLabels);
    }

    helperEmitter.emit(emitter);

    emitter.emitSectionHeader("Global Variables");
    emitter.beginDataSection();
    globalsEmitter.emitGlobals(program.globals(), emitter, structLayouts);
    globalsEmitter.emitPointerScratch(pointerScratch.scratches(), emitter);
    emitter.endDataSection();
  }
}
