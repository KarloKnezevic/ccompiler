package hr.fer.ppj.codegen.frisc.analysis;

import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes temp usage and argument counts for a function body.
 */
final class TempUsageAnalyzer {

  TempAnalysis analyze(
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
}
