package hr.fer.ppj.semantics.analysis.rules;
import hr.fer.ppj.semantics.analysis.SemanticChecker;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Semantic rules for variable initializers.
 * 
 * <p>Handles:
 * <ul>
 *   <li>Initializer expressions</li>
 *   <li>Initializer lists</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class InitializerRules {
  
  private final SemanticChecker checker;
  
  InitializerRules(SemanticChecker checker) {
    this.checker = checker;
    checker.registerRule("<inicijalizator>", this::visitInicijalizator);
    checker.registerRule("<lista_izraza_pridruzivanja>", this::visitListaIzrazaPridruzivanja);
  }
  
  private void visitInicijalizator(NonTerminalNode node) {
    var children = node.children();
    if (children.size() == 1) {
      NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(expr);
      if (isStringLiteralForCharArray(node, expr)) {
        ArrayType literalType = (ArrayType) TypeSystem.stripConst(expr.attributes().type());
        int elementCount = expr.attributes().stringLiteralLength();
        List<Type> elements = new ArrayList<>(elementCount);
        for (int i = 0; i < elementCount; i++) {
          elements.add(literalType.elementType());
        }
        node.attributes().initializerElementTypes(elements);
        node.attributes().initializerElementCount(elementCount);
        return;
      }
      node.attributes().initializerElementTypes(List.of(expr.attributes().type()));
      node.attributes().initializerElementCount(1);
      return;
    }
    NonTerminalNode list = NodeUtils.asNonTerminal(children.get(1));
    checker.visitNonTerminal(list);
    node.attributes().initializerElementTypes(list.attributes().parameterTypes());
    node.attributes().initializerElementCount(list.attributes().parameterTypes().size());
  }

  private boolean isStringLiteralForCharArray(NonTerminalNode initializer, NonTerminalNode expr) {
    if (!expr.attributes().isStringLiteral()) {
      return false;
    }
    Type targetType = initializer.attributes().inheritedType();
    if (!(TypeSystem.stripConst(targetType) instanceof ArrayType targetArray)) {
      return false;
    }
    Type targetElement = TypeSystem.stripConst(targetArray.elementType());
    return targetElement == PrimitiveType.CHAR;
  }
  
  private void visitListaIzrazaPridruzivanja(NonTerminalNode node) {
    var children = node.children();
    if (children.size() == 1) {
      NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(expr);
      node.attributes().parameterTypes(List.of(expr.attributes().type()));
      return;
    }
    if (children.size() == 3) {
      NonTerminalNode list = NodeUtils.asNonTerminal(children.get(0));
      NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(2));
      checker.visitNonTerminal(list);
      checker.visitNonTerminal(expr);
      List<Type> types = new ArrayList<>(list.attributes().parameterTypes());
      types.add(expr.attributes().type());
      node.attributes().parameterTypes(types);
      return;
    }
    checker.fail(node);
  }
  
  /**
   * Validates that an initializer is compatible with the declared type.
   * 
   * <p>This method handles:
   * <ul>
   *   <li>Array initializers: validates element count and element types</li>
   *   <li>Scalar initializers: validates single value is assignable</li>
   * </ul>
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Array initializers: element count must not exceed array size (if specified)</li>
   *   <li>Array initializers: each element type must be assignable to array element type</li>
   *   <li>Scalar initializers: must have exactly one value</li>
   *   <li>Scalar initializers: value type must be assignable to declared type</li>
   * </ul>
   * 
   * @param initializer the initializer node
   * @param declarator the declarator node (for array size information)
   * @param targetType the declared type being initialized
   * @param ctx the context node for error reporting
   */
  void validateInitializer(
      NonTerminalNode initializer, NonTerminalNode declarator, Type targetType, NonTerminalNode ctx) {
    if (targetType instanceof ArrayType arrayType) {
      // Array initializer validation
      int limit = declarator.attributes().elementCount();
      int provided = initializer.attributes().initializerElementCount();
      // Element count must not exceed array size (if array size is specified)
      if (limit > 0 && provided > limit) {
        checker.fail(ctx);
      }
      // Each initializer element must be assignable to array element type
      for (Type value : initializer.attributes().initializerElementTypes()) {
        checker.ensureAssignable(value, arrayType.elementType(), ctx);
      }
    } else {
      // Scalar initializer validation
      List<Type> values = initializer.attributes().initializerElementTypes();
      // Scalar types must have exactly one initializer value
      if (values.isEmpty() || values.size() > 1) {
        checker.fail(ctx);
      }
      // Initializer value type must be assignable to declared type
      checker.ensureAssignable(values.get(0), targetType, ctx);
    }
  }
}
