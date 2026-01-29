package hr.fer.ppj.semantics.analysis.rules;
import hr.fer.ppj.semantics.analysis.SemanticConstants;
import hr.fer.ppj.semantics.analysis.SemanticChecker;

import hr.fer.ppj.semantics.symbols.FunctionSymbol;
import hr.fer.ppj.semantics.symbols.Symbol;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.ConstType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Semantic rules for primary expressions and argument lists.
 * 
 * <p>Handles:
 * <ul>
 *   <li>Identifiers (variables and functions)</li>
 *   <li>Literals (integers, floats, characters, strings)</li>
 *   <li>Parenthesized expressions</li>
 *   <li>Function argument lists</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class PrimaryExpressionRules {
  
  private final SemanticChecker checker;
  
  PrimaryExpressionRules(SemanticChecker checker) {
    this.checker = checker;
    checker.registerRule("<primarni_izraz>", this::visitPrimarniIzraz);
    checker.registerRule("<lista_argumenata>", this::visitListaArgumenata);
  }
  
  private void visitPrimarniIzraz(NonTerminalNode node) {
    var children = node.children();
    
    if (children.size() == 3 && children.get(0) instanceof TerminalNode lParen) {
      if (!SemanticConstants.L_ZAGRADA.equals(lParen.symbol())
          || !(children.get(2) instanceof TerminalNode rParen)
          || !SemanticConstants.D_ZAGRADA.equals(rParen.symbol())) {
        checker.fail(node);
      }
      NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(1));
      checker.visitNonTerminal(expr);
      checker.copyExpressionAttributes(node, expr);
      return;
    }
    
    ParseNode child = children.get(0);
    if (child instanceof TerminalNode terminal) {
      switch (terminal.symbol()) {
        case SemanticConstants.IDN -> handleIdentifier(node, terminal);
        case SemanticConstants.BROJ -> handleNumberLiteral(node, terminal);
        case SemanticConstants.ZNAK -> handleCharacterLiteral(node, terminal);
        case SemanticConstants.NIZ_ZNAKOVA -> handleStringLiteral(node, terminal);
        default -> checker.fail(node);
      }
      return;
    }
    
    NonTerminalNode nested = NodeUtils.asNonTerminal(child);
    checker.visitNonTerminal(nested);
    checker.copyExpressionAttributes(node, nested);
  }
  
  /**
   * Handles identifier resolution in primary expressions.
   * 
   * <p>Resolves the identifier in the symbol table and sets appropriate attributes:
   * <ul>
   *   <li>Variable symbols: type from symbol, l-value status (arrays and functions are not l-values)</li>
   *   <li>Function symbols: function type, not an l-value</li>
   * </ul>
   * 
   * <p>L-value determination:
   * <ul>
   *   <li>Variables are l-values (can be assigned to)</li>
   *   <li>Arrays are not l-values (they decay to pointers in most contexts)</li>
   *   <li>Functions are not l-values (cannot be assigned to)</li>
   * </ul>
   * 
   * @param node the primary expression node
   * @param id the identifier terminal node
   */
  private void handleIdentifier(NonTerminalNode node, TerminalNode id) {
    // Look up identifier in symbol table (follows lexical scoping)
    Symbol symbol = checker.currentScope().lookup(id.lexeme()).orElse(null);
    if (symbol instanceof VariableSymbol variableSymbol) {
      node.attributes().type(variableSymbol.type());
      // Variables are l-values, except arrays and functions
      // Arrays decay to pointers in most contexts, so they're not directly assignable
      // Functions cannot be assigned to
      boolean lValue =
          !(TypeSystem.stripConst(variableSymbol.type()) instanceof ArrayType)
              && !(variableSymbol.type() instanceof FunctionType);
      node.attributes().lValue(lValue);
    } else if (symbol instanceof FunctionSymbol functionSymbol) {
      // Function identifier: represents function type, not an l-value
      node.attributes().type(functionSymbol.type());
      node.attributes().lValue(false);
    } else {
      // Identifier not found in symbol table
      checker.fail(node);
    }
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
  
  /**
   * Handles numeric literal parsing (integer or float).
   * 
   * <p>Determines literal type based on lexeme:
   * <ul>
   *   <li>Contains '.' or 'e'/'E': float literal</li>
   *   <li>Otherwise: integer literal</li>
   * </ul>
   * 
   * <p>Literals are always r-values (not l-values).
   * 
   * @param node the primary expression node
   * @param terminal the numeric literal terminal node
   */
  private void handleNumberLiteral(NonTerminalNode node, TerminalNode terminal) {
    String lexeme = terminal.lexeme();
    // Check for decimal point or scientific notation to determine float vs int
    if (lexeme.contains(".") || lexeme.toLowerCase().contains("e")) {
      // Float literal: parse and validate
      checker.parseFloatLiteral(lexeme, node);
      node.attributes().type(PrimitiveType.FLOAT);
    } else {
      // Integer literal: parse and validate
      checker.parseIntegerLiteral(lexeme, node);
      node.attributes().type(PrimitiveType.INT);
    }
    // Literals are always r-values (cannot be assigned to)
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
  
  private void handleCharacterLiteral(NonTerminalNode node, TerminalNode terminal) {
    checker.parseCharacterLiteral(terminal.lexeme(), node);
    node.attributes().type(PrimitiveType.CHAR);
    node.attributes().lValue(false);
    node.attributes().stringLiteral(false);
    node.attributes().stringLiteralLength(0);
  }
  
  private void handleStringLiteral(NonTerminalNode node, TerminalNode literal) {
    int length = checker.computeStringLiteralLength(literal.lexeme(), node);
    node.attributes().type(new ArrayType(new ConstType(PrimitiveType.CHAR), length));
    node.attributes().lValue(false);
    node.attributes().stringLiteral(true);
    node.attributes().stringLiteralLength(length);
  }
  
  private void visitListaArgumenata(NonTerminalNode node) {
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
      List<hr.fer.ppj.semantics.types.Type> result = new ArrayList<>(list.attributes().parameterTypes());
      result.add(expr.attributes().type());
      node.attributes().parameterTypes(result);
      return;
    }
    checker.fail(node);
  }
}

