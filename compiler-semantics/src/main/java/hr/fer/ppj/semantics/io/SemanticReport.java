package hr.fer.ppj.semantics.io;

import hr.fer.ppj.semantics.symbols.FunctionSymbol;
import hr.fer.ppj.semantics.symbols.Symbol;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.ConstType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.Type;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for generating semantic analysis debug files.
 * 
 * <p>This class is responsible for generating two additional output files when
 * semantic analysis completes successfully:
 * <ul>
 *   <li>{@code tablica_simbola.txt} - Human-readable symbol table dump</li>
 *   <li>{@code semanticko_stablo.txt} - Semantic tree with type annotations</li>
 * </ul>
 * 
 * <p>These files are only generated when semantic analysis succeeds (no errors).
 * If semantic analysis fails, no additional files are created.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SemanticReport {
  
  private static final Logger LOGGER = Logger.getLogger(SemanticReport.class.getName());
  
  private final Path outputDirectory;
  
  /**
   * Creates a new semantic report generator.
   * 
   * @param outputDirectory the directory where debug files will be written
   */
  public SemanticReport(Path outputDirectory) {
    this.outputDirectory = outputDirectory;
  }
  
  /**
   * Creates a new semantic report generator for the current working directory.
   * 
   * @return a new semantic report generator
   */
  public static SemanticReport forCurrentDirectory() {
    return new SemanticReport(Paths.get("."));
  }
  
  /**
   * Creates a new semantic report generator for the specified directory.
   * 
   * @param directory the output directory path
   * @return a new semantic report generator
   */
  public static SemanticReport forDirectory(String directory) {
    return new SemanticReport(Paths.get(directory));
  }
  
  /**
   * Generates both debug files when semantic analysis completes successfully.
   * 
   * <p>This method should only be called when semantic analysis has completed
   * without any errors. It generates:
   * <ul>
   *   <li>{@code tablica_simbola.txt} - Symbol table dump</li>
   *   <li>{@code semanticko_stablo.txt} - Semantic tree dump</li>
   * </ul>
   * 
   * @param globalSymbolTable the global symbol table from semantic analysis
   * @param semanticTree the root of the semantic tree with attributes
   */
  public void generateDebugFiles(SymbolTable globalSymbolTable, NonTerminalNode semanticTree) {
    try {
      // Ensure output directory exists
      Files.createDirectories(outputDirectory);
      
      // Generate symbol table dump
      writeSymbolTable(globalSymbolTable);
      
      // Generate semantic tree dump
      writeSemanticTree(semanticTree);
      
    } catch (IOException e) {
      // Log the error but don't fail semantic analysis
      LOGGER.log(Level.WARNING, "Failed to generate semantic debug files", e);
    }
  }
  
  /**
   * Writes the symbol table dump to {@code tablica_simbola.txt}.
   * 
   * @param globalSymbolTable the global symbol table
   * @throws IOException if file writing fails
   */
  private void writeSymbolTable(SymbolTable globalSymbolTable) throws IOException {
    Path symbolTableFile = outputDirectory.resolve("tablica_simbola.txt");
    
    try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(symbolTableFile))) {
      writer.println("=== SYMBOL TABLE DUMP ===");
      writer.println();
      
      writeSymbolTableRecursive(writer, globalSymbolTable, 0, "Global Scope");
    }
  }
  
  /**
   * Recursively writes symbol table information with proper indentation.
   * 
   * @param writer the output writer
   * @param symbolTable the symbol table to dump
   * @param indentLevel the current indentation level
   * @param scopeName the name of this scope
   */
  private void writeSymbolTableRecursive(PrintWriter writer, SymbolTable symbolTable, 
                                       int indentLevel, String scopeName) {
    String indent = "  ".repeat(indentLevel);
    
    writer.println(indent + scopeName + ":");
    
    // Get all symbols in this scope
    Map<String, Symbol> symbols = symbolTable.getAllSymbols();
    
    if (symbols.isEmpty()) {
      writer.println(indent + "  (no symbols)");
    } else {
      // Sort symbols by name for deterministic output
      symbols.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(entry -> {
            String name = entry.getKey();
            Symbol symbol = entry.getValue();
            writer.println(indent + "  " + formatSymbol(name, symbol));
          });
    }
    
    writer.println();
    
    // Write child scopes (if any)
    List<SymbolTable> childScopes = symbolTable.getChildScopes();
    for (int i = 0; i < childScopes.size(); i++) {
      String childScopeName = "Nested Scope " + (i + 1);
      writeSymbolTableRecursive(writer, childScopes.get(i), indentLevel + 1, childScopeName);
    }
  }
  
  /**
   * Formats a symbol for display in the symbol table dump.
   * 
   * @param name the symbol name
   * @param symbol the symbol information
   * @return formatted string representation
   */
  private String formatSymbol(String name, Symbol symbol) {
    if (symbol instanceof VariableSymbol variable) {
      return formatVariableSymbol(name, variable);
    } else if (symbol instanceof FunctionSymbol function) {
      return formatFunctionSymbol(name, function);
    } else {
      return name + " : " + symbol.getClass().getSimpleName();
    }
  }
  
  /**
   * Formats a variable symbol for display.
   * 
   * @param name the variable name
   * @param variable the variable symbol
   * @return formatted string
   */
  private String formatVariableSymbol(String name, VariableSymbol variable) {
    StringBuilder sb = new StringBuilder();
    sb.append(name).append(" : ");
    
    Type type = variable.type();
    sb.append(formatType(type));
    
    if (variable.isConst()) {
      sb.append(" (const)");
    }
    
    return sb.toString();
  }
  
  /**
   * Formats a function symbol for display.
   * 
   * @param name the function name
   * @param function the function symbol
   * @return formatted string
   */
  private String formatFunctionSymbol(String name, FunctionSymbol function) {
    StringBuilder sb = new StringBuilder();
    sb.append(name).append(" : ");
    
    FunctionType funcType = function.type();
    sb.append(formatType(funcType.returnType()));
    sb.append(" (");
    
    List<Type> paramTypes = funcType.parameterTypes();
    for (int i = 0; i < paramTypes.size(); i++) {
      if (i > 0) sb.append(", ");
      sb.append(formatType(paramTypes.get(i)));
    }
    
    sb.append(")");
    
    if (function.defined()) {
      sb.append(" [defined]");
    } else {
      sb.append(" [declared]");
    }
    
    return sb.toString();
  }
  
  /**
   * Formats a type for display.
   * 
   * @param type the type to format
   * @return formatted type string
   */
  private String formatType(Type type) {
    if (type instanceof ConstType constType) {
      return "const " + formatType(constType.baseType());
    } else if (type instanceof ArrayType arrayType) {
      return "array(" + formatType(arrayType.elementType()) + ")";
    } else if (type instanceof FunctionType funcType) {
      StringBuilder sb = new StringBuilder();
      sb.append(formatType(funcType.returnType())).append("(");
      List<Type> paramTypes = funcType.parameterTypes();
      for (int i = 0; i < paramTypes.size(); i++) {
        if (i > 0) sb.append(", ");
        sb.append(formatType(paramTypes.get(i)));
      }
      sb.append(")");
      return sb.toString();
    } else {
      return type.toString();
    }
  }
  
  /**
   * Writes the semantic tree dump to {@code semanticko_stablo.txt}.
   * 
   * @param semanticTree the root of the semantic tree
   * @throws IOException if file writing fails
   */
  private void writeSemanticTree(NonTerminalNode semanticTree) throws IOException {
    Path semanticTreeFile = outputDirectory.resolve("semanticko_stablo.txt");
    
    try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(semanticTreeFile))) {
      writer.println("=== SEMANTIC TREE DUMP ===");
      writer.println();
      
      writeSemanticTreeNode(writer, semanticTree, 0);
    }
  }
  
  /**
   * Recursively writes semantic tree nodes with attributes.
   * 
   * @param writer the output writer
   * @param node the current node
   * @param indentLevel the current indentation level
   */
  private void writeSemanticTreeNode(PrintWriter writer, ParseNode node, int indentLevel) {
    String indent = "    ".repeat(indentLevel);
    
    if (node instanceof NonTerminalNode nonTerminal) {
      writer.print(indent + "<" + nonTerminal.symbol() + ">");
      
      // Add semantic attributes if available
      String attributes = formatSemanticAttributes(nonTerminal);
      if (!attributes.isEmpty()) {
        writer.print(" " + attributes);
      }
      
      writer.println();
      
      // Write children
      for (ParseNode child : nonTerminal.children()) {
        writeSemanticTreeNode(writer, child, indentLevel + 1);
      }
      
    } else if (node instanceof TerminalNode terminal) {
      writer.print(indent + terminal.symbol());
      
      // Add terminal information
      writer.print(" (" + terminal.line() + "," + terminal.lexeme() + ")");
      
      // Add semantic attributes for terminals if available
      String attributes = formatTerminalAttributes(terminal);
      if (!attributes.isEmpty()) {
        writer.print(" " + attributes);
      }
      
      writer.println();
    }
  }
  
  /**
   * Formats semantic attributes for a non-terminal node.
   * 
   * @param node the non-terminal node
   * @return formatted attributes string
   */
  private String formatSemanticAttributes(NonTerminalNode node) {
    StringBuilder sb = new StringBuilder();
    
    Type type = node.attributes().type();
    if (type != null) {
      sb.append("type=").append(formatType(type));
    }
    
    Boolean isLValue = node.attributes().isLValue();
    if (isLValue != null) {
      if (sb.length() > 0) sb.append(", ");
      sb.append("lvalue=").append(isLValue);
    }
    
    String identifier = node.attributes().identifier();
    if (identifier != null) {
      if (sb.length() > 0) sb.append(", ");
      sb.append("id=").append(identifier);
    }
    
    Integer elementCount = node.attributes().elementCount();
    if (elementCount != null) {
      if (sb.length() > 0) sb.append(", ");
      sb.append("elements=").append(elementCount);
    }
    
    if (sb.length() > 0) {
      return "[" + sb.toString() + "]";
    } else {
      return "";
    }
  }
  
  /**
   * Formats semantic attributes for a terminal node.
   * 
   * @param terminal the terminal node
   * @return formatted attributes string
   */
  private String formatTerminalAttributes(TerminalNode terminal) {
    // For terminals, we can infer some basic type information
    String symbol = terminal.symbol();
    String lexeme = terminal.lexeme();
    
    StringBuilder sb = new StringBuilder();
    
    switch (symbol) {
      case "BROJ":
        sb.append("type=int");
        break;
      case "ZNAK":
        sb.append("type=char");
        break;
      case "NIZ_ZNAKOVA":
        sb.append("type=array(char)");
        break;
      case "IDN":
        // For identifiers, we would need symbol table lookup
        // This is a simplified version
        sb.append("symbol=").append(lexeme);
        break;
    }
    
    if (sb.length() > 0) {
      return "[" + sb.toString() + "]";
    } else {
      return "";
    }
  }
}
