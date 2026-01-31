package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.StructNameRegistry;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.util.AddressabilityChecker;
import hr.fer.ppj.ir.util.AddressReuseContext;
import hr.fer.ppj.ir.util.VariableNameManager;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.SemanticAttributes;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for l-values (memory addresses).
 *
 * <p>This class is responsible for generating address computation instructions
 * as defined in the IR grammar ({@code config/ir_definition.txt}):
 *
 * <pre>
 * AddrOfSymbol ::= "addr_of_symbol" SymbolRef ;
 * AddrIndex    ::= "addr_index" Value "," Value "," Int ;
 * AddrField    ::= "addr_field" Value "," Ident "." Ident ;
 * </pre>
 *
 * <h3>L-Value Categories</h3>
 * <ul>
 *   <li>Variable references (local/param/global)</li>
 *   <li>Array element access (base[index])</li>
 *   <li>Struct field access (base.field)</li>
 *   <li>Pointer dereference (*ptr)</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LValueGenerator {

  private final ExpressionEmitter emitter;
  private final LValueVariableEmitter variableEmitter;
  private final LValuePostfixHandler postfixHandler;

  /**
   * Creates a new l-value generator.
   *
   * @param emitter the expression emitter for r-value generation
   * @param globalScope the global symbol table
   * @param variableNameManager the variable name manager for shadowing
   * @param addressReuseContext the address reuse context for optimization
   * @param structNameRegistry the struct name registry
   */
  public LValueGenerator(
      ExpressionEmitter emitter,
      SymbolTable globalScope,
      VariableNameManager variableNameManager,
      AddressReuseContext addressReuseContext,
      StructNameRegistry structNameRegistry) {
    this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
    this.variableEmitter = new LValueVariableEmitter(
        globalScope, variableNameManager, addressReuseContext);
    this.postfixHandler = new LValuePostfixHandler(emitter, this, structNameRegistry);
  }

  /**
   * Emits an l-value (address) for an expression.
   *
   * @param node the expression node
   * @param functionContext the function context
   * @return the address temp
   * @throws IllegalArgumentException if the expression is not addressable
   */
  public IrTemp emitLValue(
      NonTerminalNode node,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    Objects.requireNonNull(node, "node must not be null");
    Objects.requireNonNull(functionContext, "functionContext must not be null");

    if (!AddressabilityChecker.isAddressableExpressionForm(node)) {
      throw new IllegalArgumentException(
          "Expression is not addressable (l-value): " + node.symbol());
    }

    return switch (node.symbol()) {
      case "<primarni_izraz>" -> emitLValuePrimary(node, functionContext);
      case "<postfiks_izraz>" -> postfixHandler.emitLValuePostfix(node, functionContext);
      case "<cast_izraz>" -> emitPassThrough(node, functionContext);
      case "<unarni_izraz>" -> emitUnary(node, functionContext);
      case "<izraz>", "<izraz_pridruzivanja>", "<log_ili_izraz>", "<log_i_izraz>",
           "<bin_ili_izraz>", "<bin_xili_izraz>", "<bin_i_izraz>", "<jednakosni_izraz>",
           "<odnosni_izraz>", "<aditivni_izraz>", "<multiplikativni_izraz>" ->
          emitPassThrough(node, functionContext);
      default -> throw new IllegalArgumentException(
          "Cannot emit l-value for: " + node.symbol());
    };
  }

  /**
   * Emits l-value for a primary expression (identifier or parenthesized).
   */
  public IrTemp emitLValuePrimary(
      NonTerminalNode node,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    List<ParseNode> children = node.children();
    if (children.isEmpty()) {
      throw new IllegalArgumentException("Empty primary expression");
    }

    ParseNode firstChild = children.get(0);
    if (firstChild instanceof TerminalNode term) {
      if (term.symbol().equals("IDN")) {
        return emitIdentifierLValue(node, term.lexeme(), functionContext);
      }

      // Parenthesized expression: ( <izraz> )
      if (term.symbol().equals("L_ZAGRADA") && children.size() >= 3) {
        ParseNode middle = children.get(1);
        if (middle instanceof NonTerminalNode inner) {
          return emitLValue(inner, functionContext);
        }
      }
    }

    throw new IllegalArgumentException("Cannot emit l-value for primary expression");
  }

  private IrTemp emitIdentifierLValue(
      NonTerminalNode node,
      String varName,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext) {

    SemanticAttributes attrs = node.attributes();
    Type varType = attrs.type();

    // Check for address reuse from assignment context
    if (variableEmitter.canReuseAddress(varName)) {
      IrTemp reuseAddr = variableEmitter.getReuseAddress(varName);
      if (reuseAddr != null) {
        return reuseAddr;
      }
    }

    // Check for last load address
    IrTemp lastLoadAddr = variableEmitter.getLastLoadAddress(varName);
    if (lastLoadAddr != null) {
      return lastLoadAddr;
    }

    return variableEmitter.emitLValueForVariable(varName, varType, functionContext);
  }

  private IrTemp emitPassThrough(
      NonTerminalNode node,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    List<ParseNode> children = node.children();
    if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
      return emitLValue(child, functionContext);
    }
    throw new IllegalArgumentException(
        "Expression " + node.symbol() + " is not addressable as l-value");
  }

  private IrTemp emitUnary(
      NonTerminalNode node,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    List<ParseNode> children = node.children();

    // Single child: pass through
    if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
      return emitLValue(child, functionContext);
    }

    // Dereference: * <cast_izraz>
    if (children.size() >= 2 && isDereferenceOperator(children.get(0))) {
      NonTerminalNode operand = NodeUtils.asNonTerminal(children.get(1), "<cast_izraz>");
      IrValue ptrValue = emitter.emitRValue(operand, functionContext);

      if (ptrValue instanceof IrTemp temp) {
        return temp;
      }
      throw new IllegalArgumentException("Cannot get address of constant pointer value");
    }

    throw new IllegalArgumentException("Cannot emit l-value for unary expression");
  }

  private boolean isDereferenceOperator(ParseNode node) {
    if (node instanceof TerminalNode term && term.symbol().equals("ASTERISK")) {
      return true;
    }
    if (node instanceof NonTerminalNode nt && nt.symbol().equals("<unarni_operator>")) {
      List<ParseNode> opChildren = nt.children();
      if (!opChildren.isEmpty() && opChildren.get(0) instanceof TerminalNode opTerm) {
        return opTerm.symbol().equals("ASTERISK");
      }
    }
    return false;
  }

  /**
   * Emits l-value for a variable by name.
   *
   * @param varName the variable name
   * @param varType the variable type
   * @param functionContext the function context
   * @return the address temp
   */
  public IrTemp emitLValueForVariable(
      String varName,
      Type varType,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    return variableEmitter.emitLValueForVariable(varName, varType, functionContext);
  }
}
