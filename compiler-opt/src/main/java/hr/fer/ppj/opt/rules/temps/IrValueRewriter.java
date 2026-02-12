package hr.fer.ppj.opt.rules.temps;

import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.model.IrValue;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Rewrites IR values inside instructions and terminators.
 */
final class IrValueRewriter {

  private IrValueRewriter() {
  }

  static IrInstruction rewriteInstruction(IrInstruction instruction, UnaryOperator<IrValue> mapper) {
    return switch (instruction) {
      case IrInstruction.IrAssignInstr assign ->
          new IrInstruction.IrAssignInstr(assign.dest(), rewriteRhs(assign.rhs(), mapper));
      case IrInstruction.IrStoreInstr store ->
          new IrInstruction.IrStoreInstr(
              mapper.apply(store.addr()),
              mapper.apply(store.value()),
              store.storeType());
      case IrInstruction.IrVoidCallInstr call ->
          new IrInstruction.IrVoidCallInstr(call.funcName(), rewriteValues(call.args(), mapper));
    };
  }

  static IrTerminator rewriteTerminator(IrTerminator terminator, UnaryOperator<IrValue> mapper) {
    return switch (terminator) {
      case IrTerminator.IrBrTerm br ->
          new IrTerminator.IrBrTerm(mapper.apply(br.condition()), br.trueLabel(), br.falseLabel());
      case IrTerminator.IrJmpTerm jmp -> jmp;
      case IrTerminator.IrRetTerm ret ->
          new IrTerminator.IrRetTerm(ret.value() == null ? null : mapper.apply(ret.value()));
    };
  }

  private static IrRhs rewriteRhs(IrRhs rhs, UnaryOperator<IrValue> mapper) {
    return switch (rhs) {
      case IrRhs.AddrOfSymbol addrOfSymbol -> addrOfSymbol;
      case IrRhs.ConstRhs constRhs -> constRhs;
      case IrRhs.AddrIndex addrIndex ->
          new IrRhs.AddrIndex(
              mapper.apply(addrIndex.base()),
              mapper.apply(addrIndex.idx()),
              addrIndex.elemSize(),
              addrIndex.resultType());
      case IrRhs.AddrField addrField ->
          new IrRhs.AddrField(
              mapper.apply(addrField.base()),
              addrField.structName(),
              addrField.fieldName(),
              addrField.resultType());
      case IrRhs.Load load ->
          new IrRhs.Load(mapper.apply(load.addr()), load.loadType());
      case IrRhs.BinOp binOp ->
          new IrRhs.BinOp(
              binOp.op(),
              mapper.apply(binOp.left()),
              mapper.apply(binOp.right()),
              binOp.resultType());
      case IrRhs.CmpOp cmpOp ->
          new IrRhs.CmpOp(cmpOp.op(), mapper.apply(cmpOp.left()), mapper.apply(cmpOp.right()));
      case IrRhs.Call call ->
          new IrRhs.Call(call.funcName(), rewriteValues(call.args(), mapper), call.resultType());
      case IrRhs.UnaryOp unaryOp ->
          new IrRhs.UnaryOp(unaryOp.op(), mapper.apply(unaryOp.operand()), unaryOp.resultType());
      case IrRhs.IncDecOp incDecOp ->
          new IrRhs.IncDecOp(incDecOp.op(), mapper.apply(incDecOp.addr()), incDecOp.resultType());
      case IrRhs.CastOp castOp ->
          new IrRhs.CastOp(castOp.op(), mapper.apply(castOp.operand()), castOp.resultType());
    };
  }

  private static List<IrValue> rewriteValues(List<IrValue> values, UnaryOperator<IrValue> mapper) {
    List<IrValue> rewritten = new ArrayList<>(values.size());
    for (IrValue value : values) {
      rewritten.add(mapper.apply(value));
    }
    return rewritten;
  }
}
