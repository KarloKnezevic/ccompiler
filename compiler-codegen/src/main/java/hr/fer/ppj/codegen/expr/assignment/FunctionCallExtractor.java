package hr.fer.ppj.codegen.expr.assignment;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.env.VariableAddressResolver;
import hr.fer.ppj.semantics.symbols.FunctionSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.List;
import java.util.Objects;

/**
 * Extracts function call information from expression nodes.
 *
 * <p>This utility class provides methods to extract function call information from expression
 * nodes, including handling cases where function calls are wrapped in various expression wrapper
 * nodes.
 *
 * <p><b>Grammar Patterns Handled:</b>
 *
 * <ul>
 *   <li>{@code <postfiks_izraz> L_ZAGRADA D_ZAGRADA} - function call without arguments
 *   <li>{@code <postfiks_izraz> L_ZAGRADA <lista_argumenata> D_ZAGRADA} - function call with
 *       arguments
 * </ul>
 *
 * <p><b>Expression Wrapper Handling:</b>
 *
 * <p>Function calls may be wrapped in various expression nodes:
 *
 * <ul>
 *   <li>{@code <izraz_pridruzivanja>}
 *   <li>{@code <log_ili_izraz>}, {@code <log_i_izraz>}
 *   <li>{@code <bin_ili_izraz>}, {@code <bin_xili_izraz>}, {@code <bin_i_izraz>}
 *   <li>{@code <jednakosni_izraz>}, {@code <odnosni_izraz>}
 *   <li>{@code <aditivni_izraz>}, {@code <multiplikativni_izraz>}
 *   <li>{@code <cast_izraz>}, {@code <unarni_izraz>}
 * </ul>
 *
 * <p>This class recursively unwraps these layers to find the underlying function call.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FunctionCallExtractor {

  private final CodeGenContext context;
  private final VariableAddressResolver addressResolver;

  /**
   * Creates a new function call extractor.
   *
   * @param context the code generation context
   */
  public FunctionCallExtractor(CodeGenContext context) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.addressResolver = new VariableAddressResolver(context);
  }

  /** Information about a function call expression. */
  public record FunctionCallInfo(NonTerminalNode function, NonTerminalNode arguments) {}

  /**
   * Extracts function call information from an expression node.
   *
   * <p>Recursively searches through expression wrappers to find a function call. Handles cases
   * where the function call is wrapped in expression nodes like {@code <izraz_pridruzivanja>},
   * {@code <log_ili_izraz>}, etc.
   *
   * @param expr the expression node (may be wrapped)
   * @return FunctionCallInfo if the expression is a function call, null otherwise
   */
  public FunctionCallInfo extractFunctionCallInfo(NonTerminalNode expr) {
    Objects.requireNonNull(expr, "expr must not be null");

    // Check if this is a postfix expression with function call pattern
    if ("<postfiks_izraz>".equals(expr.symbol())) {
      List<ParseNode> children = expr.children();
      if (children.size() == 3) {
        // Pattern: <postfiks_izraz> L_ZAGRADA D_ZAGRADA (no arguments)
        ParseNode second = children.get(1);
        ParseNode third = children.get(2);
        if (second instanceof TerminalNode leftParen
            && "L_ZAGRADA".equals(leftParen.symbol())
            && third instanceof TerminalNode rightParen
            && "D_ZAGRADA".equals(rightParen.symbol())) {
          return new FunctionCallInfo((NonTerminalNode) children.get(0), null);
        }
      } else if (children.size() == 4) {
        // Pattern: <postfiks_izraz> L_ZAGRADA <lista_argumenata> D_ZAGRADA
        ParseNode second = children.get(1);
        ParseNode third = children.get(2);
        ParseNode fourth = children.get(3);
        if (second instanceof TerminalNode leftParen
            && "L_ZAGRADA".equals(leftParen.symbol())
            && third instanceof NonTerminalNode
            && fourth instanceof TerminalNode rightParen
            && "D_ZAGRADA".equals(rightParen.symbol())) {
          return new FunctionCallInfo((NonTerminalNode) children.get(0), (NonTerminalNode) third);
        }
      }
    }

    // If not a direct function call, check if it's wrapped in expression nodes
    // Recursively search through single-child expression wrappers
    List<ParseNode> children = expr.children();
    if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
      // Single child - might be an expression wrapper
      String symbol = expr.symbol();
      if ("<izraz_pridruzivanja>".equals(symbol)
          || "<log_ili_izraz>".equals(symbol)
          || "<log_i_izraz>".equals(symbol)
          || "<bin_ili_izraz>".equals(symbol)
          || "<bin_xili_izraz>".equals(symbol)
          || "<bin_i_izraz>".equals(symbol)
          || "<jednakosni_izraz>".equals(symbol)
          || "<odnosni_izraz>".equals(symbol)
          || "<aditivni_izraz>".equals(symbol)
          || "<multiplikativni_izraz>".equals(symbol)
          || "<cast_izraz>".equals(symbol)
          || "<unarni_izraz>".equals(symbol)
          || "<postfiks_izraz>".equals(symbol)
          || "<primarni_izraz>".equals(symbol)) {
        // Recursively check the child
        return extractFunctionCallInfo(child);
      }
    }

    return null;
  }

  /**
   * Checks if an expression is a struct-returning function call.
   *
   * @param expr the expression node
   * @return true if the expression is a function call that returns a struct
   */
  public boolean isStructReturningFunctionCall(NonTerminalNode expr) {
    Objects.requireNonNull(expr, "expr must not be null");

    FunctionCallInfo callInfo = extractFunctionCallInfo(expr);
    if (callInfo == null) {
      return false;
    }

    // Extract function name
    String functionName = addressResolver.extractVariableName(callInfo.function());
    if (functionName == null) {
      return false;
    }

    // Look up function in global scope
    var symbolOpt = context.globalScope().lookup(functionName);
    if (symbolOpt.isEmpty() || !(symbolOpt.get() instanceof FunctionSymbol funcSymbol)) {
      return false;
    }

    // Check if return type is a struct
    FunctionType funcType = funcSymbol.type();
    Type returnType = funcType.returnType();
    Type strippedReturnType = TypeSystem.stripConst(returnType);
    return strippedReturnType instanceof StructType;
  }
}
