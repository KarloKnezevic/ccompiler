package hr.fer.ppj.codegen.frisc.lowering;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import java.util.List;

/**
 * Lowers IR instructions and terminators into FRISC.
 */
public final class StatementLowerer {

  private final ExpressionLowerer expressionLowerer;
  private final FrameAccess frameAccess;
  private final AddressLowerer addressLowerer;

  public StatementLowerer(ExpressionLowerer expressionLowerer, FrameAccess frameAccess, AddressLowerer addressLowerer) {
    this.expressionLowerer = expressionLowerer;
    this.frameAccess = frameAccess;
    this.addressLowerer = addressLowerer;
  }

  public void emitInstruction(IrProgramModel.Instruction instruction, FunctionContext ctx) {
    if (instruction instanceof IrProgramModel.Assign assign) {
      expressionLowerer.emitRhs(assign.rhs(), ctx, assign.dest().index());
      frameAccess.storeTemp(assign.dest().index(), ctx);
      return;
    }
    if (instruction instanceof IrProgramModel.Store store) {
      emitStore(store, ctx);
      return;
    }
    if (instruction instanceof IrProgramModel.VoidCall call) {
      expressionLowerer.emitCall(call.funcName(), call.args(), null, ctx);
      return;
    }
    throw new CodeGenerationException("Unsupported instruction: " + instruction);
  }

  private void emitStore(IrProgramModel.Store store, FunctionContext ctx) {
    if (LoweringSupport.isAggregate(store.storeType())) {
      expressionLowerer.emitValue(store.address(), ctx, "R0");
      expressionLowerer.emitValue(store.value(), ctx, "R1");
      int size = LoweringSupport.sizeOf(store.storeType(), ctx.structLayouts());
      addressLowerer.emitMemCopy("R1", "R0", size, ctx, "Copy struct");
      return;
    }

    expressionLowerer.emitValue(store.address(), ctx, "R0");
    ctx.emitter().emitInstruction("PUSH", List.of("R0"), "Save address");
    expressionLowerer.emitValue(store.value(), ctx, "R0");
    ctx.emitter().emitInstruction("POP", List.of("R1"), "Restore address");

    if (LoweringSupport.isChar(store.storeType())) {
      ctx.emitter().emitInstruction("STOREB", List.of("R0", "(R1)"), "Store byte");
    } else {
      ctx.emitter().emitInstruction("STORE", List.of("R0", "(R1)"), "Store word");
    }
  }

  public void emitTerminator(IrProgramModel.Terminator terminator, FunctionContext ctx) {
    if (terminator instanceof IrProgramModel.Br br) {
      expressionLowerer.emitValue(br.condition(), ctx, "R0");
      ctx.emitter().emitInstruction("CMP", List.of("R0", "0"), "Branch on condition");
      String trueLabel = ctx.blockLabels().get(br.trueLabel());
      String falseLabel = ctx.blockLabels().get(br.falseLabel());
      ctx.emitter().emitInstruction("JP_NE", List.of(trueLabel), null);
      ctx.emitter().emitInstruction("JP", List.of(falseLabel), null);
      return;
    }
    if (terminator instanceof IrProgramModel.Jmp jmp) {
      String target = ctx.blockLabels().get(jmp.targetLabel());
      ctx.emitter().emitInstruction("JP", List.of(target), null);
      return;
    }
    if (terminator instanceof IrProgramModel.Ret ret) {
      if (ret.value() == null) {
        ctx.emitter().emitInstruction("MOVE", List.of("0", "R6"), "Return 0 (void)");
      } else {
        expressionLowerer.emitValue(ret.value(), ctx, "R0");
        ctx.emitter().emitInstruction("MOVE", List.of("R0", "R6"), "Set return value");
      }
      ctx.emitter().emitInstruction("JP", List.of(ctx.exitLabel()), null);
      return;
    }
    throw new CodeGenerationException("Unsupported terminator: " + terminator);
  }
}
