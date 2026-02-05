package hr.fer.ppj.codegen.frisc.lowering;

import hr.fer.ppj.codegen.frisc.frame.ParamInfo;
import hr.fer.ppj.codegen.frisc.frame.ParamLayout;
import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.ir.types.IrType;
import java.util.List;
import java.util.function.Function;

/**
 * Lowers function calls and argument passing.
 */
final class CallLowerer {
  private final FrameAccess frameAccess;
  private final AddressLowerer addressLowerer;
  private final Function<String, String> functionLabelProvider;
  private final ValueEmitter valueEmitter;

  CallLowerer(
      FrameAccess frameAccess,
      AddressLowerer addressLowerer,
      Function<String, String> functionLabelProvider,
      ValueEmitter valueEmitter) {
    this.frameAccess = frameAccess;
    this.addressLowerer = addressLowerer;
    this.functionLabelProvider = functionLabelProvider;
    this.valueEmitter = valueEmitter;
  }

  void emitCall(String funcName, List<IrProgramModel.Value> args, IrType resultType, FunctionContext ctx) {
    int argCount = args.size();
    for (int i = 0; i < argCount; i++) {
      valueEmitter.emit(args.get(i), ctx, "R0");
      frameAccess.storeArgScratch(i, ctx);
    }

    ParamLayout layout = ctx.functionParamLayouts().get(funcName);
    boolean useLayout = layout != null && layout.params().size() == argCount;

    if (useLayout) {
      int totalBytes = layout.totalSize();
      if (totalBytes > 0) {
        ctx.emitter().emitInstruction("SUB", List.of("R7", LoweringSupport.formatImmediate(totalBytes), "R7"),
            "Allocate args");
      }
      for (int i = 0; i < argCount; i++) {
        ParamInfo param = layout.params().get(i);
        int offset = param.offset();
        IrType paramType = param.type();
        frameAccess.loadArgScratch(i, ctx, "R0");
        if (LoweringSupport.isAggregate(paramType)) {
          ctx.emitter().emitInstruction("MOVE", List.of("R7", "R1"), "Arg dst");
          if (offset != 0) {
            ctx.emitter().emitInstruction("ADD", List.of("R1", LoweringSupport.formatImmediate(offset), "R1"),
                "Arg offset");
          }
          int size = LoweringSupport.sizeOf(paramType, ctx.structLayouts());
          addressLowerer.emitMemCopy("R0", "R1", size, ctx, "Copy arg");
        } else {
          String addr = frameAccess.formatStackOffset(offset);
          if (LoweringSupport.isChar(paramType)) {
            ctx.emitter().emitInstruction("STOREB", List.of("R0", "(" + addr + ")"), "Store arg byte");
          } else {
            ctx.emitter().emitInstruction("STORE", List.of("R0", "(" + addr + ")"), "Store arg");
          }
        }
      }
    } else {
      for (int i = argCount - 1; i >= 0; i--) {
        frameAccess.loadArgScratch(i, ctx, "R0");
        ctx.emitter().emitInstruction("PUSH", List.of("R0"), "Push arg");
      }
    }

    String label = ctx.functionLabels().get(funcName);
    if (label == null) {
      label = functionLabelProvider.apply(funcName);
    }
    ctx.emitter().emitInstruction("CALL", List.of(label), "Call " + funcName);
    if (argCount > 0) {
      int cleanBytes = useLayout ? layout.totalSize() : argCount * 4;
      if (cleanBytes > 0) {
        ctx.emitter().emitInstruction("ADD", List.of("R7", LoweringSupport.formatImmediate(cleanBytes), "R7"),
            "Clean args");
      }
    }
    if (resultType != null) {
      ctx.emitter().emitInstruction("MOVE", List.of("R6", "R0"), "Return value");
    }
  }
}
