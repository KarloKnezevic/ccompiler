package hr.fer.ppj.semantics.analysis;

import hr.fer.ppj.semantics.symbols.FunctionSymbol;
import hr.fer.ppj.semantics.symbols.Symbol;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.ConstType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Core semantic analysis engine that implements the PPJ-C semantic rules defined in
 * {@code config/semantics_definition.txt}.
 * 
 * <p>This class serves as the central coordinator for semantic analysis, maintaining the semantic
 * context (symbol tables, current function scope, loop nesting depth) and delegating specific
 * semantic rule implementations to specialized rule handler classes.
 * 
 * <p>The semantic analysis follows a single-pass visitor pattern over the generative parse tree,
 * where each non-terminal node is dispatched to its corresponding semantic rule handler. The
 * analysis enforces:
 * <ul>
 *   <li>Type compatibility and conversion rules</li>
 *   <li>Variable and function declaration/definition consistency</li>
 *   <li>Scope and visibility rules</li>
 *   <li>Control flow constraints (break/continue in loops, return type matching)</li>
 *   <li>Array bounds and indexing rules</li>
 *   <li>Function call parameter matching</li>
 * </ul>
 * 
 * <p>On the first semantic error encountered, the analyzer prints the offending production in
 * the format required by PPJ specification and terminates analysis by throwing a
 * {@link SemanticException}.
 * 
 * @see DeclarationRules for declaration and definition semantic rules
 * @see ExpressionRules for expression semantic rules  
 * @see StatementRules for statement and control flow semantic rules
 */
final class SemanticChecker {

  /**
   * Maximum allowed array length according to PPJ-C specification.
   * Arrays with length greater than this value are rejected as semantic errors.
   */
  static final int MAX_ARRAY_LENGTH = SemanticConstants.MAX_ARRAY_LENGTH;

  private final SymbolTable globalScope;
  private final PrintStream out;
  private final Map<String, FunctionSymbol> functions = new LinkedHashMap<>();
  private final Map<String, Consumer<NonTerminalNode>> handlers = new LinkedHashMap<>();
  private final StatementRules statementRules;

  private SymbolTable currentScope;
  private boolean errorReported;
  private int loopDepth;
  private FunctionType currentFunction;

  /**
   * Constructs a new semantic checker with the given global symbol table and output stream.
   * 
   * <p>During construction, this method registers all semantic rule handlers for the various
   * non-terminal symbols defined in {@code semantics_definition.txt}. The rule handlers are
   * organized into specialized classes:
   * <ul>
   *   <li>{@link DeclarationRules} - handles declarations, definitions, and type specifications</li>
   *   <li>{@link StatementRules} - handles statements, control flow, and compound statements</li>
   *   <li>{@link ExpressionRules} - handles expressions, operators, and function calls</li>
   * </ul>
   * 
   * @param globalScope the root symbol table for global declarations and definitions
   * @param out the output stream for semantic error messages
   * @throws NullPointerException if either parameter is null
   */
  SemanticChecker(SymbolTable globalScope, PrintStream out) {
    this.globalScope = Objects.requireNonNull(globalScope, "globalScope must not be null");
    this.out = Objects.requireNonNull(out, "out must not be null");
    this.currentScope = globalScope;

    // Register handler for the augmented start symbol (parser implementation detail)
    registerRule("$", node -> {});
    
    // Initialize rule handler classes which register their specific handlers
    new DeclarationRules(this);
    this.statementRules = new StatementRules(this);
    new ExpressionRules(this);
  }

  /**
   * Performs complete semantic analysis on the given parse tree.
   * 
   * <p>This method implements the entry point for semantic analysis as defined in
   * {@code semantics_definition.txt}. The analysis proceeds in two phases:
   * 
   * <ol>
   *   <li><strong>Tree traversal:</strong> Visits all nodes in the generative parse tree,
   *       applying semantic rules and building symbol tables</li>
   *   <li><strong>Global constraint verification:</strong> Ensures program-wide semantic
   *       requirements are met (main function existence, all functions defined, etc.)</li>
   * </ol>
   * 
   * <p>The root node must represent the start symbol {@code <prijevodna_jedinica>} as defined
   * in the grammar. Any semantic error encountered during analysis will cause immediate
   * termination with a {@link SemanticException}.
   * 
   * @param root the root node of the generative parse tree, must be {@code <prijevodna_jedinica>}
   * @throws SemanticException if any semantic rule is violated or if the root is not the start symbol
   */
  void check(NonTerminalNode root) {
    if (!SemanticConstants.PRIJEVODNA_JEDINICA.equals(root.symbol())) {
      throw new SemanticException("Root production must be " + SemanticConstants.PRIJEVODNA_JEDINICA);
    }
    visitNonTerminal(root);
    verifyGlobalConstraints();
  }

  /**
   * Visits a non-terminal node and applies the appropriate semantic rules.
   * 
   * <p>This method implements the visitor pattern for semantic analysis. It first attempts
   * to find a registered semantic rule handler for the node's symbol. If a specific handler
   * exists, it delegates to that handler. Otherwise, it performs a default traversal of
   * all non-terminal children.
   * 
   * <p>The registered handlers correspond to the semantic rules defined in
   * {@code semantics_definition.txt} for each non-terminal symbol.
   * 
   * @param node the non-terminal node to visit and analyze
   */
  void visitNonTerminal(NonTerminalNode node) {
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
   * <p>This method is used by the rule handler classes ({@link DeclarationRules},
   * {@link StatementRules}, {@link ExpressionRules}) to register their specific
   * semantic rule implementations during initialization.
   * 
   * @param symbol the non-terminal symbol (e.g., "&lt;primarni_izraz&gt;", "&lt;deklaracija&gt;")
   * @param handler the semantic rule implementation for this symbol
   */
  void registerRule(String symbol, Consumer<NonTerminalNode> handler) {
    handlers.put(symbol, handler);
  }

  SymbolTable currentScope() {
    return currentScope;
  }

  void setCurrentScope(SymbolTable scope) {
    this.currentScope = scope;
  }

  FunctionType currentFunction() {
    return currentFunction;
  }

  void setCurrentFunction(FunctionType function) {
    this.currentFunction = function;
  }

  int loopDepth() {
    return loopDepth;
  }

  void processBlock(NonTerminalNode node) {
    statementRules.processBlock(node);
  }

  /**
   * Copies expression-related semantic attributes from source to target node.
   * 
   * <p>This utility method is used when a non-terminal directly inherits the semantic
   * attributes of its single child, which is common in expression productions like:
   * <pre>
   * &lt;izraz&gt; ::= &lt;izraz_pridruzivanja&gt;
   * &lt;postfiks_izraz&gt; ::= &lt;primarni_izraz&gt;
   * </pre>
   * 
   * <p>The copied attributes include:
   * <ul>
   *   <li>Type information</li>
   *   <li>L-value status (whether the expression can be assigned to)</li>
   *   <li>String literal flag and length (for array initialization)</li>
   * </ul>
   * 
   * @param target the node to receive the copied attributes
   * @param source the node whose attributes should be copied
   */
  void copyExpressionAttributes(NonTerminalNode target, NonTerminalNode source) {
    target.attributes().type(source.attributes().type());
    target.attributes().lValue(source.attributes().isLValue());
    target.attributes().stringLiteral(source.attributes().isStringLiteral());
    target.attributes().stringLiteralLength(source.attributes().stringLiteralLength());
  }

  /**
   * Ensures that the given type is convertible to {@code int} according to PPJ-C rules.
   * 
   * <p>This method implements the type compatibility check required by many semantic rules
   * in {@code semantics_definition.txt}. A type is int-convertible if:
   * <ul>
   *   <li>It is {@code int} (after stripping const qualifiers)</li>
   *   <li>It is {@code char} (after stripping const qualifiers)</li>
   * </ul>
   * 
   * <p>This check is used in contexts such as:
   * <ul>
   *   <li>Array indexing expressions (index must be int-convertible)</li>
   *   <li>Arithmetic and logical operations (operands must be int-convertible)</li>
   *   <li>Control flow conditions (condition expressions must be int-convertible)</li>
   * </ul>
   * 
   * @param type the type to check for int-convertibility
   * @param ctx the parse node context for error reporting
   * @throws SemanticException if the type is not int-convertible
   */
  void ensureIntConvertible(Type type, NonTerminalNode ctx) {
    if (type == null || !TypeSystem.isIntConvertible(TypeSystem.stripConst(type))) {
      fail(ctx);
    }
  }

  /**
   * Ensures that a value of source type can be assigned to a variable of target type.
   * 
   * <p>This method implements the assignment compatibility rules defined in PPJ-C specification.
   * The assignment is valid if the source type can be implicitly converted to the target type
   * according to the language's type conversion rules.
   * 
   * <p>This check is used in contexts such as:
   * <ul>
   *   <li>Assignment expressions ({@code <postfiks_izraz> OP_PRIDRUZI <izraz_pridruzivanja>})</li>
   *   <li>Function call argument passing</li>
   *   <li>Return statement type checking</li>
   *   <li>Variable initialization</li>
   * </ul>
   * 
   * @param source the type of the value being assigned
   * @param target the type of the variable receiving the assignment
   * @param ctx the parse node context for error reporting
   * @throws SemanticException if the assignment is not type-compatible
   */
  void ensureAssignable(Type source, Type target, NonTerminalNode ctx) {
    if (!TypeSystem.canAssign(source, target)) {
      fail(ctx);
    }
  }

  void declareVariable(String name, Type type, NonTerminalNode ctx) {
    if (TypeSystem.stripConst(type) == PrimitiveType.VOID) {
      fail(ctx);
    }
    VariableSymbol symbol = new VariableSymbol(name, type, TypeSystem.isConst(type));
    if (!currentScope.declare(symbol)) {
      fail(ctx);
    }
  }

  void registerFunctionPrototype(String name, FunctionType type, NonTerminalNode ctx) {
    Symbol existing = currentScope.lookupLocal(name).orElse(null);
    if (existing == null) {
      currentScope.declare(new FunctionSymbol(name, type, false));
    } else if (existing instanceof FunctionSymbol fn) {
      if (!fn.type().equals(type)) {
        fail(ctx);
      }
    } else {
      fail(ctx);
    }
    recordFunction(name, type, false, ctx);
  }

  void registerFunctionDefinition(String name, FunctionType type, NonTerminalNode ctx) {
    Symbol existing = globalScope.lookupLocal(name).orElse(null);
    if (existing == null) {
      globalScope.declare(new FunctionSymbol(name, type, true));
    } else if (existing instanceof FunctionSymbol fn) {
      if (!fn.type().equals(type) || fn.defined()) {
        fail(ctx);
      }
      globalScope.update(fn.markDefined());
    } else {
      fail(ctx);
    }
    recordFunction(name, type, true, ctx);
  }

  void recordFunction(String name, FunctionType type, boolean defined, NonTerminalNode ctx) {
    FunctionSymbol existing = functions.get(name);
    if (existing == null) {
      functions.put(name, new FunctionSymbol(name, type, defined));
      return;
    }
    if (!existing.type().equals(type)) {
      fail(ctx);
    }
    if (defined && existing.defined()) {
      fail(ctx);
    }
    if (defined) {
      functions.put(name, existing.markDefined());
    }
  }

  /**
   * Verifies global semantic constraints that must hold for a valid PPJ-C program.
   * 
   * <p>This method implements the program-level semantic requirements defined in the
   * PPJ-C specification:
   * 
   * <ol>
   *   <li><strong>Main function requirement:</strong> Every program must contain exactly one
   *       function named "main" with signature {@code int main(void)} and a definition
   *       (not just a declaration)</li>
   *   <li><strong>Function definition requirement:</strong> Every declared function must
   *       have exactly one definition in the program</li>
   * </ol>
   * 
   * <p>This method is called after the complete parse tree has been analyzed to ensure
   * these global invariants are satisfied.
   * 
   * @throws SemanticException if the main function is missing, has wrong signature,
   *         or if any declared function lacks a definition
   */
  void verifyGlobalConstraints() {
    // Check main function requirement: int main(void) must exist and be defined
    FunctionSymbol main = functions.get(SemanticConstants.MAIN_FUNCTION_NAME);
    if (main == null
        || !main.defined()
        || TypeSystem.stripConst(main.type().returnType()) != PrimitiveType.INT
        || !main.type().parameterTypes().isEmpty()) {
      out.println(SemanticConstants.ERROR_MISSING_MAIN);
      out.println();
      throw new SemanticException("missing main");
    }
    
    // Check that all declared functions have definitions
    for (FunctionSymbol function : functions.values()) {
      if (!function.defined()) {
        out.println(SemanticConstants.ERROR_UNDEFINED_FUNCTION);
        out.println();
        throw new SemanticException("undefined function");
      }
    }
  }

  void declareFunctionParameters(List<String> names, List<Type> types, NonTerminalNode ctx) {
    if (names == null || types == null) {
      return;
    }
    if (names.size() != types.size()) {
      fail(ctx);
    }
    for (int i = 0; i < names.size(); i++) {
      declareVariable(names.get(i), types.get(i), ctx);
    }
  }

  void withNewScope(Runnable action) {
    SymbolTable previous = currentScope;
    currentScope = currentScope.enterChildScope();
    try {
      action.run();
    } finally {
      currentScope = previous;
    }
  }

  void withinLoop(Runnable action) {
    loopDepth++;
    try {
      action.run();
    } finally {
      loopDepth--;
    }
  }

  boolean requiresInitialization(Type type) {
    if (type instanceof ConstType) {
      return true;
    }
    if (type instanceof ArrayType arrayType) {
      return requiresInitialization(arrayType.elementType());
    }
    return false;
  }

  void fail(NonTerminalNode node) {
    if (errorReported) {
      throw new SemanticException("error already reported");
    }
    errorReported = true;
    out.println(ProductionFormatter.formatProduction(node));
    out.println();
    throw new SemanticException("semantic error");
  }

  int parseArrayLength(String literal, NonTerminalNode ctx) {
    long value = parseIntegerLiteral(literal, ctx);
    if (value <= 0 || value > MAX_ARRAY_LENGTH) {
      fail(ctx);
    }
    return (int) value;
  }

  long parseIntegerLiteral(String literal, NonTerminalNode ctx) {
    String text = literal.toLowerCase();
    int radix = 10;
    if (text.startsWith("0x")) {
      radix = 16;
      text = text.substring(2);
    } else if (text.startsWith("0") && text.length() > 1) {
      radix = 8;
      text = text.substring(1);
    }
    if (text.isEmpty()) {
      text = "0";
    }
    long value;
    try {
      value = Long.parseLong(text, radix);
    } catch (NumberFormatException ex) {
      fail(ctx);
      return 0;
    }
    if (value < 0 || value > Integer.MAX_VALUE) {
      fail(ctx);
    }
    return value;
  }

  /**
   * Validates a character literal according to PPJ-C specification.
   * 
   * <p>This method implements the semantic rules for character literals used in
   * {@code <primarni_izraz> ::= ZNAK}. A valid character literal must:
   * <ul>
   *   <li>Be enclosed in single quotes (')</li>
   *   <li>Contain exactly one character, or</li>
   *   <li>Contain exactly one escape sequence (\n, \t, \0, \', \")</li>
   * </ul>
   * 
   * @param literal the character literal string including quotes
   * @param ctx the parse node context for error reporting
   * @throws SemanticException if the character literal is malformed
   */
  void parseCharacterLiteral(String literal, NonTerminalNode ctx) {
    // Check basic format: must be at least 3 characters ('x') and properly quoted
    if (literal.length() < 3
        || literal.charAt(0) != '\''
        || literal.charAt(literal.length() - 1) != '\'') {
      fail(ctx);
    }
    
    // Simple character literal: 'x'
    if (literal.length() == 3) {
      return;
    }
    
    // Escape sequence: '\x' where x is a valid escape character
    if (literal.length() == 4 && literal.charAt(1) == '\\') {
      char escape = literal.charAt(2);
      if (SemanticConstants.VALID_ESCAPE_SEQUENCES.indexOf(escape) >= 0) {
        return;
      }
    }
    
    // Invalid character literal
    fail(ctx);
  }

  /**
   * Computes the length of a string literal for array type checking.
   * 
   * <p>This method implements the semantic rules for string literals used in
   * {@code <primarni_izraz> ::= NIZ_ZNAKOVA}. The computed length includes:
   * <ul>
   *   <li>All regular characters in the string</li>
   *   <li>Escape sequences (\n, \t, \0, \', \") count as single characters</li>
   *   <li>The implicit null terminator character</li>
   * </ul>
   * 
   * <p>This length is used for:
   * <ul>
   *   <li>Array initialization compatibility checking</li>
   *   <li>Setting the string literal length attribute for type analysis</li>
   * </ul>
   * 
   * @param literal the string literal including quotes
   * @param ctx the parse node context for error reporting
   * @return the computed length including null terminator
   * @throws SemanticException if the string literal is malformed
   */
  int computeStringLiteralLength(String literal, NonTerminalNode ctx) {
    // Check basic format: must be at least 2 characters ("") and properly quoted
    if (literal.length() < 2
        || literal.charAt(0) != '"'
        || literal.charAt(literal.length() - 1) != '"') {
      fail(ctx);
    }
    
    int length = 0;
    // Process characters between quotes
    for (int i = 1; i < literal.length() - 1; i++) {
      char ch = literal.charAt(i);
      if (ch == '\\') {
        // Handle escape sequence
        if (i + 1 >= literal.length() - 1) {
          fail(ctx); // Incomplete escape sequence
        }
        char escape = literal.charAt(++i);
        if (SemanticConstants.VALID_ESCAPE_SEQUENCES.indexOf(escape) < 0) {
          fail(ctx); // Invalid escape sequence
        }
        length++; // Escape sequence counts as one character
      } else {
        if (ch == '"') {
          fail(ctx); // Unescaped quote inside string
        }
        length++; // Regular character
      }
    }
    return length + 1; // Include null terminator
  }
}

