package hr.fer.ppj.cli;

import hr.fer.ppj.ir.IrPipeline;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Token;
import hr.fer.ppj.parser.Parser;
import hr.fer.ppj.parser.Parser.ParserException;
import hr.fer.ppj.parser.io.TokenReader;
import hr.fer.ppj.parser.tree.ParseTree;
import hr.fer.ppj.semantics.analysis.SemanticAnalyzer;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.ConstType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.Type;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Generates a comprehensive validation report for all C programs in examples/valid.
 * 
 * <p>For each C program, the report includes:
 * <ul>
 *   <li>The original C source code</li>
 *   <li>The generative parse tree</li>
 *   <li>The semantic tree</li>
 *   <li>The generated IR program</li>
 * </ul>
 * 
 * <p>All outputs are written to a single text file with clear separators.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class IrValidationReport {

  private static final String EXAMPLES_VALID_DIR = "examples/valid";
  private static final String OUTPUT_FILE = "ir_validation_report.txt";
  private static final String SEPARATOR = "=".repeat(80);

  /**
   * Main entry point.
   */
  public static void main(String[] args) {
    try {
      Path examplesDir = Paths.get(EXAMPLES_VALID_DIR);
      if (!Files.exists(examplesDir) || !Files.isDirectory(examplesDir)) {
        System.err.println("Error: Directory not found: " + EXAMPLES_VALID_DIR);
        System.exit(1);
      }

      // Find all .c files
      List<Path> cFiles = new ArrayList<>();
      try (Stream<Path> paths = Files.list(examplesDir)) {
        paths.filter(p -> p.toString().endsWith(".c"))
            .sorted()
            .forEach(cFiles::add);
      }

      if (cFiles.isEmpty()) {
        System.err.println("Error: No .c files found in " + EXAMPLES_VALID_DIR);
        System.exit(1);
      }

      System.err.println("Found " + cFiles.size() + " C programs to process.");
      System.err.println("Generating report: " + OUTPUT_FILE);

      // Generate report
      try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(Paths.get(OUTPUT_FILE)))) {
        writer.println("IR VALIDATION REPORT");
        writer.println("Generated for all C programs in " + EXAMPLES_VALID_DIR);
        writer.println();
        writer.println(SEPARATOR);
        writer.println();

        int successCount = 0;
        int errorCount = 0;

        for (int i = 0; i < cFiles.size(); i++) {
          Path cFile = cFiles.get(i);
          String fileName = cFile.getFileName().toString();
          
          System.err.println("Processing [" + (i + 1) + "/" + cFiles.size() + "]: " + fileName);

          try {
            processProgram(writer, cFile, fileName);
            successCount++;
          } catch (Exception e) {
            errorCount++;
            writer.println("FILE: " + fileName);
            writer.println(SEPARATOR);
            writer.println();
            writer.println("ERROR PROCESSING FILE:");
            writer.println(e.getMessage());
            if (e.getCause() != null) {
              writer.println("Cause: " + e.getCause().getMessage());
            }
            writer.println();
            writer.println(SEPARATOR);
            writer.println();
            writer.println();
            
            System.err.println("  ERROR: " + e.getMessage());
          }
        }

        writer.println(SEPARATOR);
        writer.println();
        writer.println("SUMMARY:");
        writer.println("  Total programs: " + cFiles.size());
        writer.println("  Successful: " + successCount);
        writer.println("  Errors: " + errorCount);
      }

      System.err.println();
      System.err.println("Report generated successfully: " + OUTPUT_FILE);

    } catch (Exception e) {
      System.err.println("Fatal error: " + e.getMessage());
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  /**
   * Processes a single C program and writes its information to the report.
   */
  private static void processProgram(PrintWriter writer, Path cFile, String fileName)
      throws Exception {
    
    // Read C source code
    String cSource = Files.readString(cFile);

    // Run lexical analysis
    Path specPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
    LexerGenerator generator = new LexerGenerator();
    LexerGeneratorResult lexerResult;
    try (FileReader reader = new FileReader(specPath.toFile())) {
      lexerResult = generator.generate(reader);
    }
    
    Lexer lexer = new Lexer(lexerResult);
    List<Token> tokens;
    try (Reader reader = new FileReader(cFile.toFile())) {
      tokens = lexer.tokenize(reader);
    }

    // Run parser
    List<TokenReader.Token> parserTokens = new ArrayList<>(tokens.size());
    for (Token token : tokens) {
      parserTokens.add(new TokenReader.Token(token.type(), token.line(), token.value()));
    }
    Parser parser = new Parser();
    ParseTree parseTree = parser.parseTokens(parserTokens);

    // Get generative tree
    String generativeTree = parseTree.toGenerativeTreeString();

    // Run semantic analysis
    SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
    SemanticAnalyzer.SemanticAnalysisResult semanticResult;
    try {
      // Run semantic analysis and get results
      semanticResult = semanticAnalyzer.analyzeWithResults(parseTree, System.err, null);
      NonTerminalNode semanticTree = semanticResult.parseTree();
      
      // Get semantic tree string using SemanticReport formatter
      String semanticTreeString = formatSemanticTree(semanticTree);

      // Generate IR
      IrProgram irProgram = IrPipeline.generate(semanticResult.globalScope(), semanticTree);
      String irString = IrPipeline.print(irProgram);

      // Write to report
      writer.println("FILE: " + fileName);
      writer.println(SEPARATOR);
      writer.println();
      
      writer.println("=== C SOURCE CODE ===");
      writer.println();
      writer.println(cSource);
      writer.println();
      writer.println(SEPARATOR);
      writer.println();
      
      writer.println("=== GENERATIVE TREE ===");
      writer.println();
      writer.println(generativeTree);
      writer.println();
      writer.println(SEPARATOR);
      writer.println();
      
      writer.println("=== SEMANTIC TREE ===");
      writer.println();
      writer.println(semanticTreeString);
      writer.println();
      writer.println(SEPARATOR);
      writer.println();
      
      writer.println("=== IR PROGRAM ===");
      writer.println();
      writer.println(irString);
      writer.println();
      writer.println(SEPARATOR);
      writer.println();
      writer.println();

    } catch (hr.fer.ppj.semantics.errors.SemanticException e) {
      // Semantic error - still write what we have
      // Try to get semantic tree from the exception context if possible
      // Otherwise, we'll just show the parse tree
      String semanticTreeString = "Semantic analysis failed before tree conversion";
      
      writer.println("FILE: " + fileName);
      writer.println(SEPARATOR);
      writer.println();
      
      writer.println("=== C SOURCE CODE ===");
      writer.println();
      writer.println(cSource);
      writer.println();
      writer.println(SEPARATOR);
      writer.println();
      
      writer.println("=== GENERATIVE TREE ===");
      writer.println();
      writer.println(generativeTree);
      writer.println();
      writer.println(SEPARATOR);
      writer.println();
      
      writer.println("=== SEMANTIC TREE ===");
      writer.println();
      writer.println(semanticTreeString);
      writer.println();
      writer.println(SEPARATOR);
      writer.println();
      
      writer.println("=== SEMANTIC ERROR ===");
      writer.println();
      writer.println("Semantic analysis failed: " + e.getMessage());
      writer.println();
      writer.println(SEPARATOR);
      writer.println();
      writer.println();
      
      throw new IOException("Semantic error: " + e.getMessage(), e);
    }
  }

  /**
   * Formats a semantic tree node recursively.
   */
  private static String formatSemanticTree(hr.fer.ppj.semantics.tree.ParseNode node) {
    StringBuilder sb = new StringBuilder();
    formatSemanticTreeNode(sb, node, 0);
    return sb.toString();
  }

  /**
   * Recursively formats semantic tree nodes with attributes.
   */
  private static void formatSemanticTreeNode(StringBuilder sb, 
      ParseNode node, int indentLevel) {
    String indent = "    ".repeat(indentLevel);
    
    if (node instanceof NonTerminalNode nonTerminal) {
      sb.append(indent).append("<").append(nonTerminal.symbol()).append(">");
      
      // Add semantic attributes if available
      String attributes = formatSemanticAttributes(nonTerminal);
      if (!attributes.isEmpty()) {
        sb.append(" ").append(attributes);
      }
      
      sb.append("\n");
      
      // Write children
      for (ParseNode child : nonTerminal.children()) {
        formatSemanticTreeNode(sb, child, indentLevel + 1);
      }
      
    } else if (node instanceof TerminalNode terminal) {
      sb.append(indent).append(terminal.symbol());
      sb.append(" (").append(terminal.line()).append(",").append(terminal.lexeme()).append(")");
      
      // Add semantic attributes for terminals if available
      String attributes = formatTerminalAttributes(terminal);
      if (!attributes.isEmpty()) {
        sb.append(" ").append(attributes);
      }
      
      sb.append("\n");
    }
  }

  /**
   * Formats semantic attributes for a non-terminal node.
   */
  private static String formatSemanticAttributes(NonTerminalNode node) {
    hr.fer.ppj.semantics.tree.SemanticAttributes attrs = node.attributes();
    if (attrs == null) {
      return "";
    }
    
    List<String> parts = new ArrayList<>();
    
    if (attrs.type() != null) {
      parts.add("type=" + formatType(attrs.type()));
    }
    if (attrs.identifier() != null) {
      parts.add("id=" + attrs.identifier());
    }
    if (attrs.isLValue()) {
      parts.add("lvalue");
    }
    if (attrs.isConstValue()) {
      parts.add("const");
    }
    
    return parts.isEmpty() ? "" : "[" + String.join(", ", parts) + "]";
  }

  /**
   * Formats semantic attributes for a terminal node.
   */
  private static String formatTerminalAttributes(TerminalNode terminal) {
    // Terminal nodes may not have attributes in all cases
    // Just return empty string for now
    return "";
  }

  /**
   * Formats a type for display.
   */
  private static String formatType(Type type) {
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
}

