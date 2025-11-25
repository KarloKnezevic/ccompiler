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
 * Core semantic analysis engine. Dispatches productions to dedicated rule handlers, maintains
 * semantic context (symbol tables, current function, loop depth) and exposes helper utilities for
 * rule implementations.
 */
final class SemanticChecker {

  static final int MAX_ARRAY_LENGTH = 1024;

  private final SymbolTable globalScope;
  private final PrintStream out;
  private final Map<String, FunctionSymbol> functions = new LinkedHashMap<>();
  private final Map<String, Consumer<NonTerminalNode>> handlers = new LinkedHashMap<>();
  private final StatementRules statementRules;

  private SymbolTable currentScope;
  private boolean errorReported;
  private int loopDepth;
  private FunctionType currentFunction;

  SemanticChecker(SymbolTable globalScope, PrintStream out) {
    this.globalScope = Objects.requireNonNull(globalScope, "globalScope must not be null");
    this.out = Objects.requireNonNull(out, "out must not be null");
    this.currentScope = globalScope;

    registerRule("$", node -> {});
    new DeclarationRules(this);
    this.statementRules = new StatementRules(this);
    new ExpressionRules(this);
  }

  void check(NonTerminalNode root) {
    if (!"<prijevodna_jedinica>".equals(root.symbol())) {
      throw new SemanticException("Root production must be <prijevodna_jedinica>");
    }
    visitNonTerminal(root);
    verifyGlobalConstraints();
  }

  void visitNonTerminal(NonTerminalNode node) {
    Consumer<NonTerminalNode> handler = handlers.get(node.symbol());
    if (handler != null) {
      handler.accept(node);
      return;
    }
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt) {
        visitNonTerminal(nt);
      }
    }
  }

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

  void copyExpressionAttributes(NonTerminalNode target, NonTerminalNode source) {
    target.attributes().type(source.attributes().type());
    target.attributes().lValue(source.attributes().isLValue());
    target.attributes().stringLiteral(source.attributes().isStringLiteral());
    target.attributes().stringLiteralLength(source.attributes().stringLiteralLength());
  }

  void ensureIntConvertible(Type type, NonTerminalNode ctx) {
    if (type == null || !TypeSystem.isIntConvertible(TypeSystem.stripConst(type))) {
      fail(ctx);
    }
  }

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

  void verifyGlobalConstraints() {
    FunctionSymbol main = functions.get("main");
    if (main == null
        || !main.defined()
        || TypeSystem.stripConst(main.type().returnType()) != PrimitiveType.INT
        || !main.type().parameterTypes().isEmpty()) {
      out.println("main");
      out.println();
      throw new SemanticException("missing main");
    }
    for (FunctionSymbol function : functions.values()) {
      if (!function.defined()) {
        out.println("funkcija");
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

  void parseCharacterLiteral(String literal, NonTerminalNode ctx) {
    if (literal.length() < 3
        || literal.charAt(0) != '\''
        || literal.charAt(literal.length() - 1) != '\'') {
      fail(ctx);
    }
    if (literal.length() == 3) {
      return;
    }
    if (literal.length() == 4 && literal.charAt(1) == '\\') {
      char escape = literal.charAt(2);
      if ("nt0\\'\"".indexOf(escape) >= 0) {
        return;
      }
    }
    fail(ctx);
  }

  int computeStringLiteralLength(String literal, NonTerminalNode ctx) {
    if (literal.length() < 2
        || literal.charAt(0) != '"'
        || literal.charAt(literal.length() - 1) != '"') {
      fail(ctx);
    }
    int length = 0;
    for (int i = 1; i < literal.length() - 1; i++) {
      char ch = literal.charAt(i);
      if (ch == '\\') {
        if (i + 1 >= literal.length() - 1) {
          fail(ctx);
        }
        char escape = literal.charAt(++i);
        if ("nt0\\'\"".indexOf(escape) < 0) {
          fail(ctx);
        }
        length++;
      } else {
        if (ch == '"') {
          fail(ctx);
        }
        length++;
      }
    }
    return length + 1; // include terminating null character
  }
}

