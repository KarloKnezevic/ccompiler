package hr.fer.ppj.semantics.analysis;

import hr.fer.ppj.semantics.analysis.context.SemanticContext;
import hr.fer.ppj.semantics.analysis.rules.DeclarationRules;
import hr.fer.ppj.semantics.analysis.rules.ExpressionRules;
import hr.fer.ppj.semantics.analysis.rules.StatementRules;
import hr.fer.ppj.semantics.analysis.util.LiteralParser;
import hr.fer.ppj.semantics.analysis.util.SymbolManager;
import hr.fer.ppj.semantics.analysis.util.TypeChecker;
import hr.fer.ppj.semantics.errors.SemanticErrorReporter;

import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.types.FunctionType;

import hr.fer.ppj.semantics.types.Type;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Core semantic analysis engine that implements the PPJ-C semantic rules
 * defined in
 * {@code config/semantics_definition.txt}.
 * 
 * <p>
 * This class serves as the central coordinator for semantic analysis,
 * maintaining the semantic
 * context (symbol tables, current function scope, loop nesting depth) and
 * delegating specific
 * semantic rule implementations to specialized rule handler classes.
 * 
 * <p>
 * The semantic analysis follows a single-pass visitor pattern over the
 * generative parse tree,
 * where each non-terminal node is dispatched to its corresponding semantic rule
 * handler. The
 * analysis enforces:
 * <ul>
 * <li>Type compatibility and conversion rules</li>
 * <li>Variable and function declaration/definition consistency</li>
 * <li>Scope and visibility rules</li>
 * <li>Control flow constraints (break/continue in loops, return type
 * matching)</li>
 * <li>Array bounds and indexing rules</li>
 * <li>Function call parameter matching</li>
 * </ul>
 * 
 * <p>
 * On the first semantic error encountered, the analyzer prints the offending
 * production in
 * the format required by PPJ specification and terminates analysis by throwing
 * a
 * {@link hr.fer.ppj.semantics.errors.SemanticException}.
 * 
 * @see hr.fer.ppj.semantics.analysis.rules.DeclarationRules for declaration and
 *      definition semantic rules
 * @see hr.fer.ppj.semantics.analysis.rules.ExpressionRules for expression
 *      semantic rules
 * @see hr.fer.ppj.semantics.analysis.rules.StatementRules for statement and
 *      control flow semantic rules
 */
public final class SemanticChecker {

  private final SemanticContext context;
  private final SemanticErrorReporter errorReporter;
  private final TypeChecker typeChecker;
  private final LiteralParser literalParser;
  private final SymbolManager symbolManager;
  private final GlobalConstraintVerifier constraintVerifier;
  private final Map<String, Consumer<NonTerminalNode>> handlers = new java.util.LinkedHashMap<>();
  private final StatementRules statementRules;

  /**
   * Constructs a new semantic checker with the given global symbol table and
   * output stream.
   * 
   * <p>
   * During construction, this method registers all semantic rule handlers for the
   * various
   * non-terminal symbols defined in {@code semantics_definition.txt}. The rule
   * handlers are
   * organized into specialized classes:
   * <ul>
   * <li>{@link DeclarationRules} - handles declarations, definitions, and type
   * specifications</li>
   * <li>{@link StatementRules} - handles statements, control flow, and compound
   * statements</li>
   * <li>{@link ExpressionRules} - handles expressions, operators, and function
   * calls</li>
   * </ul>
   * 
   * @param globalScope the root symbol table for global declarations and
   *                    definitions
   * @param out         the output stream for semantic error messages
   * @throws NullPointerException if either parameter is null
   */
  SemanticChecker(SymbolTable globalScope, hr.fer.ppj.common.diagnostic.DiagnosticReporter reporter) {
    Objects.requireNonNull(globalScope, "globalScope must not be null");
    Objects.requireNonNull(reporter, "reporter must not be null");

    this.errorReporter = new SemanticErrorReporter(reporter);
    this.context = new SemanticContext(globalScope);
    this.typeChecker = new TypeChecker(errorReporter);
    this.literalParser = new LiteralParser(errorReporter);
    this.symbolManager = new SymbolManager(context, errorReporter, typeChecker);
    this.constraintVerifier = new GlobalConstraintVerifier(errorReporter);

    // Register handler for the augmented start symbol (parser implementation
    // detail)
    registerRule("$", node -> {
    });

    // Initialize rule handler classes which register their specific handlers
    new DeclarationRules(this);
    this.statementRules = new StatementRules(this);
    new ExpressionRules(this);
  }

  /**
   * Performs complete semantic analysis on the given parse tree.
   * 
   * <p>
   * This method implements the entry point for semantic analysis as defined in
   * {@code semantics_definition.txt}. The analysis proceeds in two phases:
   * 
   * <ol>
   * <li><strong>Tree traversal:</strong> Visits all nodes in the generative parse
   * tree,
   * applying semantic rules and building symbol tables</li>
   * <li><strong>Global constraint verification:</strong> Ensures program-wide
   * semantic
   * requirements are met (main function existence, all functions defined,
   * etc.)</li>
   * </ol>
   * 
   * <p>
   * The root node must represent the start symbol {@code <prijevodna_jedinica>}
   * as defined
   * in the grammar. Any semantic error encountered during analysis will cause
   * immediate
   * termination with a {@link hr.fer.ppj.semantics.errors.SemanticException}.
   * 
   * @param root the root node of the generative parse tree, must be
   *             {@code <prijevodna_jedinica>}
   * @throws hr.fer.ppj.semantics.errors.SemanticException if any semantic rule is
   *                                                       violated or if the root
   *                                                       is not the start symbol
   */
  void check(NonTerminalNode root) {
    if (!SemanticConstants.PRIJEVODNA_JEDINICA.equals(root.symbol())) {
      throw new hr.fer.ppj.semantics.errors.SemanticException(
          "Root production must be " + SemanticConstants.PRIJEVODNA_JEDINICA);
    }
    visitNonTerminal(root);
    verifyGlobalConstraints();
  }

  /**
   * Visits a non-terminal node and applies the appropriate semantic rules.
   * 
   * <p>
   * This method implements the visitor pattern for semantic analysis. It first
   * attempts
   * to find a registered semantic rule handler for the node's symbol. If a
   * specific handler
   * exists, it delegates to that handler. Otherwise, it performs a default
   * traversal of
   * all non-terminal children.
   * 
   * <p>
   * The registered handlers correspond to the semantic rules defined in
   * {@code semantics_definition.txt} for each non-terminal symbol.
   * 
   * @param node the non-terminal node to visit and analyze
   */
  public void visitNonTerminal(NonTerminalNode node) {
    Consumer<NonTerminalNode> handler = handlers.get(node.symbol());
    if (handler != null) {
      handler.accept(node);
      return;
    }
    // Default behavior: recursively visit all non-terminal children
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt) {
        visitNonTerminal(nt);
      }
    }
  }

  /**
   * Registers a semantic rule handler for the specified non-terminal symbol.
   * 
   * <p>
   * This method is used by the rule handler classes
   * ({@link hr.fer.ppj.semantics.analysis.rules.DeclarationRules},
   * {@link hr.fer.ppj.semantics.analysis.rules.StatementRules},
   * {@link hr.fer.ppj.semantics.analysis.rules.ExpressionRules}) to register
   * their specific
   * semantic rule implementations during initialization.
   * 
   * @param symbol  the non-terminal symbol (e.g., "&lt;primarni_izraz&gt;",
   *                "&lt;deklaracija&gt;")
   * @param handler the semantic rule implementation for this symbol
   */
  public void registerRule(String symbol, Consumer<NonTerminalNode> handler) {
    handlers.put(symbol, handler);
  }

  // ========== Context Accessors ==========

  SemanticContext context() {
    return context;
  }

  public SymbolTable currentScope() {
    return context.currentScope();
  }

  public void setCurrentScope(SymbolTable scope) {
    context.setCurrentScope(scope);
  }

  public FunctionType currentFunction() {
    return context.currentFunction();
  }

  public void setCurrentFunction(FunctionType function) {
    context.setCurrentFunction(function);
  }

  public int loopDepth() {
    return context.loopDepth();
  }

  public void processBlock(NonTerminalNode node) {
    statementRules.processBlock(node);
  }

  // ========== Type Checking Delegates ==========

  /**
   * Copies expression-related semantic attributes from source to target node.
   * 
   * <p>
   * This utility method is used when a non-terminal directly inherits the
   * semantic
   * attributes of its single child, which is common in expression productions.
   * 
   * @param target the node to receive the copied attributes
   * @param source the node whose attributes should be copied
   */
  public void copyExpressionAttributes(NonTerminalNode target, NonTerminalNode source) {
    target.attributes().type(source.attributes().type());
    target.attributes().lValue(source.attributes().isLValue());
    target.attributes().stringLiteral(source.attributes().isStringLiteral());
    target.attributes().stringLiteralLength(source.attributes().stringLiteralLength());
  }

  public void ensureIntConvertible(Type type, NonTerminalNode ctx) {
    typeChecker.ensureIntConvertible(type, ctx);
  }

  public void ensureAssignable(Type source, Type target, NonTerminalNode ctx) {
    typeChecker.ensureAssignable(source, target, ctx);
  }

  // ========== Symbol Management Delegates ==========

  public void declareVariable(String name, Type type, NonTerminalNode ctx) {
    symbolManager.declareVariable(name, type, ctx);
  }

  public void registerFunctionPrototype(String name, FunctionType type, NonTerminalNode ctx) {
    symbolManager.registerFunctionPrototype(name, type, ctx);
  }

  public void registerFunctionDefinition(String name, FunctionType type, NonTerminalNode ctx) {
    symbolManager.registerFunctionDefinition(name, type, ctx);
  }

  public void declareFunctionParameters(List<String> names, List<Type> types, NonTerminalNode ctx) {
    symbolManager.declareFunctionParameters(names, types, ctx);
  }

  public void withNewScope(Runnable action) {
    context.withNewScope(action);
  }

  public boolean requiresInitialization(Type type) {
    return typeChecker.requiresInitialization(type);
  }

  public void registerStructTagForward(String tag, hr.fer.ppj.semantics.types.StructType structType,
      NonTerminalNode ctx) {
    symbolManager.registerStructTagForward(tag, structType, ctx);
  }

  public void registerStructTag(String tag, hr.fer.ppj.semantics.types.StructType structType, NonTerminalNode ctx) {
    symbolManager.registerStructTag(tag, structType, ctx);
  }

  public hr.fer.ppj.semantics.types.StructType lookupStructTag(String tag) {
    return symbolManager.lookupStructTag(tag);
  }

  // ========== Literal Parsing Delegates ==========

  public int parseArrayLength(String literal, NonTerminalNode ctx) {
    return literalParser.parseArrayLength(literal, ctx);
  }

  public long parseIntegerLiteral(String literal, NonTerminalNode ctx) {
    return literalParser.parseIntegerLiteral(literal, ctx);
  }

  public void parseFloatLiteral(String literal, NonTerminalNode ctx) {
    literalParser.parseFloatLiteral(literal, ctx);
  }

  public void parseCharacterLiteral(String literal, NonTerminalNode ctx) {
    literalParser.parseCharacterLiteral(literal, ctx);
  }

  public int computeStringLiteralLength(String literal, NonTerminalNode ctx) {
    return literalParser.computeStringLiteralLength(literal, ctx);
  }

  // ========== Error Reporting ==========

  public void fail(NonTerminalNode node) {
    errorReporter.reportError(node);
  }

  // ========== Global Constraint Verification ==========

  void verifyGlobalConstraints() {
    constraintVerifier.verify(symbolManager.getAllFunctions());
  }

  public void withinLoop(Runnable action) {
    context.withinLoop(action);
  }
}
