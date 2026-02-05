package hr.fer.ppj.cli.pipeline;

import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.codegen.frisc.ir.IrTextParser;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class IrPointerValidator {

  private final IrTextParser parser = new IrTextParser();

  void validate(String irText) {
    Objects.requireNonNull(irText, "irText must not be null");
    validate(parser.parse(irText));
  }

  void validate(IrProgramModel program) {
    Map<String, IrProgramModel.Function> functions = new HashMap<>();
    for (IrProgramModel.Function function : program.functions()) {
      functions.put(function.name(), function);
    }

    Map<String, IrType> globalTypes = new HashMap<>();
    for (IrProgramModel.GlobalVar global : program.globals()) {
      globalTypes.put(global.name(), global.type());
    }

    for (IrProgramModel.Function function : program.functions()) {
      validateFunction(function, functions, globalTypes);
    }
  }

  private void validateFunction(
      IrProgramModel.Function function,
      Map<String, IrProgramModel.Function> functions,
      Map<String, IrType> globalTypes) {

    Map<String, IrProgramModel.Slot> localSlots = new HashMap<>();
    Map<String, IrProgramModel.Slot> paramSlots = new HashMap<>();
    for (IrProgramModel.Slot slot : function.slots()) {
      if (slot.kind() == IrProgramModel.SlotKind.LOCAL || slot.kind() == IrProgramModel.SlotKind.SPILL) {
        localSlots.put(slot.name(), slot);
      } else if (slot.kind() == IrProgramModel.SlotKind.PARAM) {
        paramSlots.put(slot.name(), slot);
      }
    }

    Map<Integer, IrType> tempTypes = new HashMap<>();

    for (IrProgramModel.Block block : function.blocks()) {
      for (IrProgramModel.Instruction instruction : block.instructions()) {
        if (instruction instanceof IrProgramModel.Assign assign) {
          IrType type = resultType(assign.rhs(), localSlots, paramSlots, globalTypes);
          tempTypes.put(assign.dest().index(), type);
          if (assign.rhs() instanceof IrProgramModel.Call call) {
            validateCall(function, call.funcName(), call.args(), tempTypes, functions);
          }
        } else if (instruction instanceof IrProgramModel.VoidCall call) {
          validateCall(function, call.funcName(), call.args(), tempTypes, functions);
        } else if (instruction instanceof IrProgramModel.Store store) {
          validateStore(function, store, tempTypes);
        }
      }
    }
  }

  private void validateCall(
      IrProgramModel.Function currentFunction,
      String funcName,
      List<IrProgramModel.Value> args,
      Map<Integer, IrType> tempTypes,
      Map<String, IrProgramModel.Function> functions) {

    IrProgramModel.Function callee = functions.get(funcName);
    if (callee == null) {
      return;
    }
    List<IrProgramModel.Parameter> params = callee.parameters();
    int count = Math.min(params.size(), args.size());
    for (int i = 0; i < count; i++) {
      IrType paramType = params.get(i).type();
      if (paramType instanceof IrPointerType) {
        IrType argType = valueType(args.get(i), tempTypes);
        if (argType instanceof IrPointerType || isNullLike(args.get(i))) {
          continue;
        }
        throw new IllegalArgumentException("Invalid pointer argument in function "
            + currentFunction.name() + " when calling " + funcName);
      }
    }
  }

  private void validateStore(
      IrProgramModel.Function currentFunction,
      IrProgramModel.Store store,
      Map<Integer, IrType> tempTypes) {

    if (!(store.storeType() instanceof IrPointerType)) {
      return;
    }
    IrType valueType = valueType(store.value(), tempTypes);
    if (valueType instanceof IrPointerType || isNullLike(store.value())) {
      return;
    }
    throw new IllegalArgumentException("Invalid pointer assignment in function " + currentFunction.name());
  }

  private IrType valueType(IrProgramModel.Value value, Map<Integer, IrType> tempTypes) {
    if (value instanceof IrProgramModel.Temp temp) {
      return tempTypes.get(temp.index());
    }
    if (value instanceof IrProgramModel.Const c) {
      return c.constant().type();
    }
    return null;
  }

  private boolean isNullLike(IrProgramModel.Value value) {
    if (value instanceof IrProgramModel.Const c) {
      IrConst constant = c.constant();
      if (constant instanceof IrConst.NullConst) {
        return true;
      }
      if (constant instanceof IrConst.IntConst intConst) {
        return intConst.value() == 0;
      }
    }
    return false;
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
      IrType base = symbolType(addr.symbolRef(), localSlots, paramSlots, globalTypes);
      return new IrPointerType(base);
    }
    if (rhs instanceof IrProgramModel.AddrIndex) {
      return new IrPointerType(IrPrimitiveType.INT32);
    }
    if (rhs instanceof IrProgramModel.AddrField) {
      return new IrPointerType(IrPrimitiveType.INT32);
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
