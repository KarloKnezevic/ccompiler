package hr.fer.ppj.codegen.frisc.lowering;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.codegen.frisc.util.LabelGenerator;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrType;
import java.util.List;
import java.util.function.Function;

/**
 * Lowers IR expressions and RHS forms into FRISC instructions.
 */
public final class ExpressionLowerer {

  private final FrameAccess frameAccess;
  private final AddressLowerer addressLowerer;
  private final ImmediateEmitter immediateEmitter;
  private final BinaryLowerer binaryLowerer;
  private final CompareLowerer compareLowerer;
  private final UnaryLowerer unaryLowerer;
  private final CallLowerer callLowerer;

  public ExpressionLowerer(
      LabelGenerator labelGenerator,
      FrameAccess frameAccess,
      AddressLowerer addressLowerer,
      ImmediateEmitter immediateEmitter,
      Function<String, String> functionLabelProvider) {
    this.frameAccess = frameAccess;
    this.addressLowerer = addressLowerer;
    this.immediateEmitter = immediateEmitter;
    ValueEmitter valueEmitter = this::emitValue;
    this.binaryLowerer = new BinaryLowerer(valueEmitter);
    this.compareLowerer = new CompareLowerer(labelGenerator, valueEmitter);
    this.unaryLowerer = new UnaryLowerer(labelGenerator, valueEmitter);
    this.callLowerer = new CallLowerer(frameAccess, addressLowerer, functionLabelProvider, valueEmitter);
  }

  public void emitRhs(IrProgramModel.Rhs rhs, FunctionContext ctx, Integer destTemp) {
    if (rhs instanceof IrProgramModel.AddrOfSymbol addr) {
      addressLowerer.emitAddrOfSymbol(addr.symbolRef(), ctx, "R0");
      return;
    }
    if (rhs instanceof IrProgramModel.AddrIndex addrIndex) {
      addressLowerer.emitAddrIndex(addrIndex, ctx, destTemp);
      return;
    }
    if (rhs instanceof IrProgramModel.AddrField addrField) {
      addressLowerer.emitAddrField(addrField, ctx);
      return;
    }
    if (rhs instanceof IrProgramModel.Load load) {
      emitValue(load.address(), ctx, "R0");
      if (LoweringSupport.isAggregate(load.loadType())) {
        return;
      }
      if (LoweringSupport.isChar(load.loadType())) {
        ctx.emitter().emitInstruction("LOADB", List.of("R0", "(R0)"), "Load byte");
      } else {
        ctx.emitter().emitInstruction("LOAD", List.of("R0", "(R0)"), "Load word");
      }
      return;
    }
    if (rhs instanceof IrProgramModel.ConstRhs constRhs) {
      emitConst(constRhs.constant(), ctx, "R0");
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
      unaryLowerer.emitUnaryOp(unaryOp, ctx);
      return;
    }
    if (rhs instanceof IrProgramModel.CastOp castOp) {
      unaryLowerer.emitCastOp(castOp, ctx);
      return;
    }

    throw new CodeGenerationException("Unsupported RHS: " + rhs);
  }

  public void emitBinOp(IrProgramModel.BinOp binOp, FunctionContext ctx) {
    binaryLowerer.emitBinOp(binOp, ctx);
  }

  public void emitCmpOp(IrProgramModel.CmpOp cmpOp, FunctionContext ctx) {
    compareLowerer.emitCmpOp(cmpOp, ctx);
  }

  public void emitCall(String funcName, List<IrProgramModel.Value> args, IrType resultType, FunctionContext ctx) {
    callLowerer.emitCall(funcName, args, resultType, ctx);
  }

  public void emitValue(IrProgramModel.Value value, FunctionContext ctx, String targetReg) {
    if (value instanceof IrProgramModel.Temp temp) {
      frameAccess.loadTemp(temp.index(), ctx, targetReg);
      return;
    }
    if (value instanceof IrProgramModel.Const constant) {
      emitConst(constant.constant(), ctx, targetReg);
      return;
    }
    throw new CodeGenerationException("Unsupported value: " + value);
  }

  public void emitConst(IrConst constant, FunctionContext ctx, String targetReg) {
    if (constant instanceof IrConst.IntConst intConst) {
      immediateEmitter.emitLoadImmediate(intConst.value(), ctx, targetReg, null);
      return;
    }
    if (constant instanceof IrConst.CharConst charConst) {
      immediateEmitter.emitLoadImmediate(charConst.value(), ctx, targetReg, null);
      return;
    }
    if (constant instanceof IrConst.NullConst) {
      immediateEmitter.emitLoadImmediate(0, ctx, targetReg, null);
      return;
    }
    if (constant instanceof IrConst.FloatConst floatConst) {
      int raw = LoweringSupport.floatToQ16_16(floatConst.value());
      immediateEmitter.emitLoadImmediate(raw, ctx, targetReg, null);
      return;
    }
    if (constant instanceof IrConst.ArrayConst) {
      throw new CodeGenerationException("Array constant cannot be used as value");
    }
    throw new CodeGenerationException("Unsupported constant: " + constant);
  }
}
