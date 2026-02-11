package hr.fer.ppj.cli.io;

import hr.fer.ppj.semantics.symbols.FunctionSymbol;
import hr.fer.ppj.semantics.symbols.Symbol;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.SemanticAttributes;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.ConstType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.PointerType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Writes semantic analysis output to a single, structured text file.
 */
public final class SemanticOutputWriter {

  public void write(Path outputFile, SymbolTable globalScope, NonTerminalNode semanticTree) throws Exception {
    Objects.requireNonNull(outputFile, "outputFile must not be null");
    Objects.requireNonNull(globalScope, "globalScope must not be null");
    Objects.requireNonNull(semanticTree, "semanticTree must not be null");

    ScopeStats stats = collectScopeStats(globalScope);
    StringBuilder sb = new StringBuilder(16_384);
    sb.append("SEMANTIC TREE REPORT\n");
    sb.append("====================\n\n");

    sb.append("Symbol Table\n");
    sb.append("------------\n");
    sb.append("Summary:\n");
    sb.append("- total scopes: ").append(stats.scopeCount).append('\n');
    sb.append("- total symbols: ").append(stats.symbolCount).append('\n');
    sb.append("- variables: ").append(stats.variableCount).append('\n');
    sb.append("- constants: ").append(stats.constantCount).append('\n');
    sb.append("- functions: ").append(stats.functionCount).append("\n\n");
    sb.append("Scopes and symbols:\n\n");
    writeScope(sb, globalScope, 0, new ScopeCounter(), null);

    sb.append("\nSemantic Tree\n");
    sb.append("-------------\n");
    sb.append("Each non-terminal includes semantic attributes computed during analysis.\n\n");
    writeNode(sb, semanticTree, 0);

    Files.writeString(outputFile, sb.toString(), StandardCharsets.UTF_8);
  }

  private void writeScope(
      StringBuilder sb,
      SymbolTable scope,
      int depth,
      ScopeCounter scopeCounter,
      Integer parentScopeId) {
    int scopeId = scopeCounter.next();
    String indent = "  ".repeat(depth);
    sb.append(indent).append("Scope #").append(scopeId);
    sb.append(" (depth=").append(depth).append(", parent=");
    sb.append(parentScopeId == null ? "none" : "#" + parentScopeId).append(")\n");
    sb.append(indent)
        .append(String.format("%-24s %-10s %-38s %s%n", "Name", "Kind", "Type / Signature", "Details"));

    Map<String, Symbol> symbols = scope.getAllSymbols();
    if (symbols.isEmpty()) {
      sb.append(indent).append(String.format("%-24s %-10s %-38s %s%n", "(no symbols)", "-", "-", "-"));
    } else {
      symbols.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(entry -> writeSymbolRow(sb, indent, entry.getValue()));
    }

    sb.append('\n');
    for (SymbolTable childScope : scope.getChildScopes()) {
      writeScope(sb, childScope, depth + 1, scopeCounter, scopeId);
    }
  }

  private void writeSymbolRow(StringBuilder sb, String indent, Symbol symbol) {
    SymbolRow row = toRow(symbol);
    sb.append(indent).append(String.format("%-24s %-10s %-38s %s%n",
        symbol.name(), row.kind(), row.typeOrSignature(), row.details()));
  }

  private SymbolRow toRow(Symbol symbol) {
    if (symbol instanceof VariableSymbol variable) {
      String details = "const=" + variable.isConst() + ", scalar=" + variable.type().isScalar();
      if (variable.type() instanceof ArrayType arrayType && !arrayType.dimensions().isEmpty()) {
        details += ", dimensions=" + formatDimensions(arrayType);
      }
      return new SymbolRow("variable", formatType(variable.type()), details);
    }

    if (symbol instanceof FunctionSymbol function) {
      FunctionType functionType = function.type();
      String details = "defined=" + function.defined()
          + ", return=" + formatType(functionType.returnType())
          + ", params=" + functionType.parameterTypes().size();
      return new SymbolRow("function", formatFunction(functionType), details);
    }

    return new SymbolRow("symbol", "<unknown>", "-");
  }

  private void writeNode(StringBuilder sb, ParseNode node, int depth) {
    String indent = "    ".repeat(depth);
    if (node instanceof NonTerminalNode nonTerminal) {
      sb.append(indent).append(nonTerminal.symbol());
      String attributes = formatAttributes(nonTerminal.attributes());
      if (!attributes.isEmpty()) {
        sb.append(" ").append(attributes);
      }
      sb.append('\n');
      for (ParseNode child : nonTerminal.children()) {
        writeNode(sb, child, depth + 1);
      }
      return;
    }

    if (node instanceof TerminalNode terminal) {
      sb.append(indent)
          .append(terminal.symbol())
          .append(" (line=")
          .append(terminal.line())
          .append(", lexeme=")
          .append(terminal.lexeme())
          .append(")\n");
    }
  }

  private String formatAttributes(SemanticAttributes attributes) {
    StringBuilder sb = new StringBuilder(256);
    appendTypeAttribute(sb, "type", attributes.type());
    appendAttribute(sb, "lvalue", attributes.isLValue());
    appendAttribute(sb, "constValue", attributes.isConstValue());
    appendOptionalAttribute(sb, "identifier", attributes.identifier());
    appendPositiveAttribute(sb, "elementCount", attributes.elementCount());
    appendPositiveAttribute(sb, "initializerElementCount", attributes.initializerElementCount());
    appendTypeListAttribute(sb, "parameterTypes", attributes.parameterTypes());
    appendTypeListAttribute(sb, "initializerElementTypes", attributes.initializerElementTypes());
    appendAttribute(sb, "containsReturn", attributes.containsReturn());
    appendTypeAttribute(sb, "inheritedType", attributes.inheritedType());
    appendTypeAttribute(sb, "castSourceType", attributes.castSourceType());
    appendEnumAttribute(sb, "castCategory", attributes.castCategory());
    if (attributes.functionType() != null) {
      appendOptionalAttribute(sb, "functionType", formatFunction(attributes.functionType()));
    }

    if (sb.isEmpty()) {
      return "";
    }
    return "[" + sb + "]";
  }

  private void appendAttribute(StringBuilder sb, String name, Object value) {
    if (sb.length() > 0) {
      sb.append(", ");
    }
    sb.append(name).append('=').append(value);
  }

  private void appendOptionalAttribute(StringBuilder sb, String name, Object value) {
    if (value == null) {
      return;
    }
    appendAttribute(sb, name, value);
  }

  private void appendPositiveAttribute(StringBuilder sb, String name, int value) {
    if (value <= 0) {
      return;
    }
    appendAttribute(sb, name, value);
  }

  private void appendTypeAttribute(StringBuilder sb, String name, Type type) {
    if (type == null) {
      return;
    }
    appendAttribute(sb, name, formatType(type));
  }

  private void appendTypeListAttribute(StringBuilder sb, String name, List<Type> types) {
    if (types == null || types.isEmpty()) {
      return;
    }
    String joined = types.stream().map(this::formatType).reduce((a, b) -> a + "|" + b).orElse("");
    appendAttribute(sb, name, joined);
  }

  private void appendEnumAttribute(StringBuilder sb, String name, Enum<?> value) {
    if (value == null) {
      return;
    }
    appendAttribute(sb, name, value.name().toLowerCase(Locale.ROOT));
  }

  private String formatFunction(FunctionType type) {
    StringBuilder sb = new StringBuilder();
    sb.append(formatType(type.returnType())).append(" (");
    for (int i = 0; i < type.parameterTypes().size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(formatType(type.parameterTypes().get(i)));
    }
    sb.append(")");
    return sb.toString();
  }

  private String formatType(Type type) {
    if (type == null) {
      return "<none>";
    }
    if (type instanceof PrimitiveType primitiveType) {
      return primitiveType.name().toLowerCase(Locale.ROOT);
    }
    if (type instanceof ConstType constType) {
      return "const " + formatType(constType.baseType());
    }
    if (type instanceof PointerType pointerType) {
      String ptr = "ptr<" + formatType(pointerType.baseType()) + ">";
      return pointerType.isConst() ? "const " + ptr : ptr;
    }
    if (type instanceof ArrayType arrayType) {
      return "array<" + formatType(arrayType.elementType()) + ">" + formatDimensions(arrayType);
    }
    if (type instanceof StructType structType) {
      return structType.tag() == null ? "struct <anonymous>" : "struct " + structType.tag();
    }
    if (type instanceof FunctionType functionType) {
      return formatFunction(functionType);
    }
    return type.toString().toLowerCase(Locale.ROOT);
  }

  private String formatDimensions(ArrayType arrayType) {
    if (arrayType.dimensions().isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (Integer dimension : arrayType.dimensions()) {
      sb.append('[').append(dimension).append(']');
    }
    return sb.toString();
  }

  private ScopeStats collectScopeStats(SymbolTable scope) {
    ScopeStats stats = new ScopeStats();
    collectScopeStats(scope, stats);
    return stats;
  }

  private void collectScopeStats(SymbolTable scope, ScopeStats stats) {
    stats.scopeCount++;
    Map<String, Symbol> symbols = scope.getAllSymbols();
    stats.symbolCount += symbols.size();
    for (Symbol symbol : symbols.values()) {
      if (symbol instanceof VariableSymbol variable) {
        stats.variableCount++;
        if (variable.isConst()) {
          stats.constantCount++;
        }
      } else if (symbol instanceof FunctionSymbol) {
        stats.functionCount++;
      }
    }

    for (SymbolTable childScope : scope.getChildScopes()) {
      collectScopeStats(childScope, stats);
    }
  }

  private record SymbolRow(String kind, String typeOrSignature, String details) {
  }

  private static final class ScopeCounter {
    private int nextId = 1;

    int next() {
      return nextId++;
    }
  }

  private static final class ScopeStats {
    private int scopeCount;
    private int symbolCount;
    private int variableCount;
    private int constantCount;
    private int functionCount;
  }
}
