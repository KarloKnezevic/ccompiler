package hr.fer.ppj.codegen.frisc.lowering;

import hr.fer.ppj.codegen.frisc.emitter.FriscEmitter;
import hr.fer.ppj.codegen.frisc.frame.ParamLayout;
import hr.fer.ppj.codegen.frisc.frame.StructLayoutRegistry;
import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.ir.types.IrType;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Bundles per-function lowering state and precomputed offsets.
 */
public final class FunctionContext {
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

  public FunctionContext(
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

  public IrProgramModel.Function function() {
    return function;
  }

  public FriscEmitter emitter() {
    return emitter;
  }

  public StructLayoutRegistry structLayouts() {
    return structLayouts;
  }

  public Map<String, IrType> globalTypes() {
    return globalTypes;
  }

  public Map<String, IrProgramModel.Slot> localSlots() {
    return localSlots;
  }

  public Map<String, IrProgramModel.Slot> paramSlots() {
    return paramSlots;
  }

  public Map<String, String> functionLabels() {
    return functionLabels;
  }

  public Map<String, ParamLayout> functionParamLayouts() {
    return functionParamLayouts;
  }

  public Set<Integer> addrIndexNeedsCheck() {
    return addrIndexNeedsCheck;
  }

  public Map<Integer, IrType> tempTypes() {
    return tempTypes;
  }

  public Map<Integer, Integer> tempOffsets() {
    return tempOffsets;
  }

  public Map<Integer, Integer> argOffsets() {
    return argOffsets;
  }

  public int frameSize() {
    return frameSize;
  }

  public int localsBytes() {
    return localsBytes;
  }

  public int tempCount() {
    return tempCount;
  }

  public int argScratchCount() {
    return argScratchCount;
  }

  public String exitLabel() {
    return exitLabel;
  }

  public Map<String, String> blockLabels() {
    return blockLabels;
  }
}
