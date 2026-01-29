package hr.fer.ppj.ir.util;

import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Evaluates global array initializers as array constants.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ArrayInitializerEvaluator {

  private ArrayInitializerEvaluator() {}

  /**
   * Evaluates a global array initializer as an array constant.
   *
   * @param initializer the initializer node
   * @param arrayType the array type
   * @return the array constant
   * @throws UnsupportedOperationException if elements are not compile-time constants
   */
  public static IrConst evaluateGlobalArrayInitializer(
      NonTerminalNode initializer, ArrayType arrayType) {
    Objects.requireNonNull(initializer, "initializer must not be null");
    Objects.requireNonNull(arrayType, "arrayType must not be null");

    // Array initializer: { <lista_izraza_pridruzivanja> }
    NonTerminalNode listNode =
        NodeUtils.asNonTerminal(initializer.children().get(1), "<lista_izraza_pridruzivanja>");
    List<NonTerminalNode> elementExprs = extractInitializerExpressions(listNode);

    Type elementType = arrayType.elementType();
    IrType irElementType = TypeMapper.toIrType(elementType);
    List<IrConst> elementConsts = new ArrayList<>();

    for (NonTerminalNode elemExpr : elementExprs) {
      try {
        IrConst elemConst = ConstantEvaluator.extractConstantFromExpression(elemExpr, elementType);
        elementConsts.add(elemConst);
      } catch (UnsupportedOperationException | IllegalArgumentException e) {
        throw new UnsupportedOperationException(
            "Array initializer element must be a compile-time constant: " + e.getMessage());
      }
    }

    IrArrayType irArrayType = (IrArrayType) TypeMapper.toIrType(arrayType);
    return new IrConst.ArrayConst(elementConsts, irArrayType);
  }

  /**
   * Extracts all expression nodes from a <lista_izraza_pridruzivanja> recursively.
   */
  public static List<NonTerminalNode> extractInitializerExpressions(NonTerminalNode listNode) {
    List<NonTerminalNode> result = new ArrayList<>();
    List<hr.fer.ppj.semantics.tree.ParseNode> children = listNode.children();

    if (children.size() == 1) {
      if (children.get(0) instanceof NonTerminalNode expr) {
        result.add(NodeUtils.asNonTerminal(expr, "<izraz_pridruzivanja>"));
      }
    } else if (children.size() == 3) {
      NonTerminalNode list =
          NodeUtils.asNonTerminal(children.get(0), "<lista_izraza_pridruzivanja>");
      NonTerminalNode expr =
          NodeUtils.asNonTerminal(children.get(2), "<izraz_pridruzivanja>");
      result.addAll(extractInitializerExpressions(list));
      result.add(expr);
    }

    return result;
  }
}
