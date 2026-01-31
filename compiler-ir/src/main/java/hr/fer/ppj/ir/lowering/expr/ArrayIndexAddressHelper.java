package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.build.TypeSizeCalculator;
import hr.fer.ppj.ir.lowering.FunctionContext;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.SemanticAttributes;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PointerType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.List;
import java.util.Objects;

/**
 * Helper for computing array index addresses in assignments.
 *
 * <p>Handles the special case where array indexing appears on the left side
 * of an assignment and the right side contains a cast. In this case, the
 * base address must be computed before the cast.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class ArrayIndexAddressHelper {

  private final ExpressionEmitter emitter;
  private final LValueEmitter lValueEmitter;

  ArrayIndexAddressHelper(ExpressionEmitter emitter, LValueEmitter lValueEmitter) {
    this.emitter = Objects.requireNonNull(emitter);
    this.lValueEmitter = Objects.requireNonNull(lValueEmitter);
  }

  /**
   * Gets the base address of an array indexing expression.
   */
  IrTemp getArrayBaseAddress(NonTerminalNode arrayIndexNode, FunctionContext ctx) {
    NonTerminalNode postfixNode = unwrapToPostfix(arrayIndexNode);

    if (!postfixNode.symbol().equals("<postfiks_izraz>")) {
      throw new IllegalArgumentException("Not an array indexing expression");
    }

    List<ParseNode> children = postfixNode.children();
    if (children.size() < 3) {
      throw new IllegalArgumentException("Invalid array indexing expression");
    }

    NonTerminalNode baseNode = NodeUtils.asNonTerminal(children.get(0), "<postfiks_izraz>");
    return lValueEmitter.emitLValue(baseNode, ctx);
  }

  /**
   * Computes the indexed address given the base address.
   */
  IrTemp computeArrayIndexAddress(
      NonTerminalNode arrayIndexNode, IrTemp baseAddr, FunctionContext ctx) {

    NonTerminalNode postfixNode = unwrapToPostfix(arrayIndexNode);

    if (!postfixNode.symbol().equals("<postfiks_izraz>")) {
      throw new IllegalArgumentException("Not an array indexing expression");
    }

    List<ParseNode> children = postfixNode.children();
    if (children.size() < 3) {
      throw new IllegalArgumentException("Invalid array indexing expression");
    }

    NonTerminalNode baseNode = NodeUtils.asNonTerminal(children.get(0), "<postfiks_izraz>");
    NonTerminalNode indexNode = NodeUtils.asNonTerminal(children.get(2), "<izraz>");

    SemanticAttributes baseAttrs = baseNode.attributes();
    Type baseType = baseAttrs.type();
    IrValue index = emitter.emitRValue(indexNode, ctx);

    Type elementType = extractElementType(baseType);
    IrType irElementType = TypeMapper.toIrType(elementType);
    int elemSize = TypeSizeCalculator.getTypeSize(irElementType);

    IrFunctionBuilder builder = ctx.functionBuilder();
    IrRhs.AddrIndex addrIndex = new IrRhs.AddrIndex(
        baseAddr, index, elemSize, new IrPointerType(irElementType));
    IrTemp result = builder.tempFactory().newTemp(addrIndex.resultType());
    builder.addInstruction(new IrInstruction.IrAssignInstr(result, addrIndex));
    return result;
  }

  private NonTerminalNode unwrapToPostfix(NonTerminalNode node) {
    String symbol = node.symbol();
    if (symbol.equals("<unarni_izraz>") || symbol.equals("<cast_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
        return unwrapToPostfix(child);
      }
    }
    return node;
  }

  private Type extractElementType(Type baseType) {
    Type stripped = TypeSystem.stripConst(baseType);
    if (stripped instanceof ArrayType at) return at.elementType();
    if (stripped instanceof PointerType pt) return pt.baseType();
    throw new IllegalArgumentException("Base type is not array or pointer: " + baseType);
  }
}
