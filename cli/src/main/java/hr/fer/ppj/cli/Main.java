package hr.fer.ppj.cli;

import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Lexer.SymbolTableEntry;
import hr.fer.ppj.lexer.io.Token;
import hr.fer.ppj.parser.Parser;
import hr.fer.ppj.parser.Parser.ParserException;
import hr.fer.ppj.parser.io.TokenReader;
import hr.fer.ppj.parser.tree.ParseTree;
import hr.fer.ppj.semantics.analysis.SemanticAnalyzer;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line interface for the PPJ compiler.
 * 
 * <p>Usage:
 * <ul>
 *   <li>{@code lexer <file>} - Perform only lexical analysis, output to stdout</li>
 *   <li>{@code syntax <file>} - Perform lexical and syntax analysis, generate output files in compiler-bin/</li>
 *   <li>{@code semantic <file>} - Perform full compilation (lexical + syntax + code generation)</li>
 *   <li>{@code <file>} - Full compilation (default, same as semantic)</li>
 * </ul>
 * 
 * <p>Output files (for syntax and semantic modes) are generated in {@code compiler-bin/} directory:
 * <ul>
 *   <li>{@code leksicke_jedinke.txt} - Lexical tokens output</li>
 *   <li>{@code generativno_stablo.txt} - Generative tree</li>
 *   <li>{@code sintaksno_stablo.txt} - Syntax tree</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class Main {
  
  private static final String COMPILER_BIN_DIR = "compiler-bin";
  
  private record LexicalResult(List<Token> tokens, List<SymbolTableEntry> symbolTable) {}
  
  private record CompilationArtifacts(LexicalResult lexical, ParseTree parseTree) {}
  
  public static void main(String[] args) {
    if (args.length == 0) {
      printUsage();
      System.exit(1);
    }
    
    try {
      // If first argument is a file path (not a command), treat as full compilation
      if (args.length == 1 && !isCommand(args[0])) {
        runFullCompilation(args[0]);
        return;
      }
      
      String command = args[0];
      
      if (args.length < 2) {
        System.err.println("Error: File path required for command: " + command);
        printUsage();
        System.exit(1);
      }
      
      String filePath = args[1];
      Path inputFile = Paths.get(filePath);
      
      if (!Files.exists(inputFile)) {
        System.err.println("Error: File not found: " + filePath);
        System.exit(1);
      }
      
      switch (command) {
        case "lexer":
          runLexer(filePath);
          break;
        case "syntax":
          runSyntax(filePath);
          break;
        case "semantic":
          runSemantic(filePath);
          break;
        default:
          System.err.println("Unknown command: " + command);
          printUsage();
          System.exit(1);
      }
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }
  
  private static void printUsage() {
    System.err.println("Usage:");
    System.err.println("  lexer <file>     - Perform only lexical analysis (output to stdout)");
    System.err.println("  syntax <file>    - Perform lexical and syntax analysis (output to compiler-bin/)");
    System.err.println("  semantic <file>  - Perform full compilation (lexical + syntax + code generation)");
    System.err.println("  <file>           - Full compilation (same as semantic)");
  }
  
  private static boolean isCommand(String arg) {
    return arg.equals("lexer") || arg.equals("syntax") || arg.equals("semantic");
  }
  
  private static void runFullCompilation(String filePath) throws Exception {
    runSemantic(filePath);
  }
  
  /**
   * Runs lexical analysis only.
   * Output is written to stdout in the standard format.
   */
  private static void runLexer(String filePath) throws Exception {
    LexicalResult lexical = performLexicalAnalysis(filePath);
    System.out.println("tablica znakova:");
    System.out.println("indeks   uniformni znak   izvorni tekst");
    for (int i = 0; i < lexical.symbolTable().size(); i++) {
      SymbolTableEntry entry = lexical.symbolTable().get(i);
      System.out.printf("     %d   %-18s %s%n", i, entry.token(), entry.text());
    }
    
    // Output token stream
    System.out.println("\nniz uniformnih znakova:");
    System.out.println("uniformni znak    redak    indeks u tablicu znakova");
    for (Token token : lexical.tokens()) {
      System.out.printf("%-18s %5d       %d%n", 
          token.type(), 
          token.line(), 
          token.symbolTableIndex());
    }
  }
  
  /**
   * Runs lexical and syntax analysis.
   * Generates output files in compiler-bin/ directory:
   * - leksicke_jedinke.txt
   * - generativno_stablo.txt
   * - sintaksno_stablo.txt
   */
  private static void runSyntax(String filePath) throws Exception {
    Path binDir = ensureOutputDirectory();
    compileToParseTree(filePath, binDir, true);
    System.err.println("Lexical and syntax analysis completed. Output files generated in " + COMPILER_BIN_DIR + "/");
  }
  
  /**
   * Runs full compilation pipeline (lexical + syntax + code generation).
   * Generates all output files in compiler-bin/ directory.
   */
  private static void runSemantic(String filePath) throws Exception {
    Path binDir = ensureOutputDirectory();
    CompilationArtifacts artifacts = compileToParseTree(filePath, binDir, true);
    SemanticAnalyzer analyzer = new SemanticAnalyzer();
    try {
      analyzer.analyze(artifacts.parseTree(), System.out);
      System.err.println("Semantic analysis completed.");
    } catch (hr.fer.ppj.semantics.analysis.SemanticException e) {
      // Production already printed by SemanticChecker.fail()
      // Print error message and exit without stack trace
      System.err.println("Error: semantic error");
      System.exit(1);
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static CompilationArtifacts compileToParseTree(String filePath, Path outputDir, boolean writeOutputs)
      throws Exception {
    LexicalResult lexical = performLexicalAnalysis(filePath);
    if (writeOutputs) {
      writeLexerOutputFile(lexical, outputDir.resolve("leksicke_jedinke.txt"));
    }

    ParseTree parseTree = runParser(lexical.tokens());

    if (writeOutputs) {
      writeParseOutputs(parseTree,
          outputDir.resolve("generativno_stablo.txt"),
          outputDir.resolve("sintaksno_stablo.txt"));
    }

    return new CompilationArtifacts(lexical, parseTree);
  }

  private static LexicalResult performLexicalAnalysis(String filePath) throws Exception {
    Path specPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
    LexerGenerator generator = new LexerGenerator();
    LexerGeneratorResult result;
    try (FileReader reader = new FileReader(specPath.toFile())) {
      result = generator.generate(reader);
    }

    Lexer lexer = new Lexer(result);
    List<Token> tokens;
    try (Reader reader = new FileReader(filePath)) {
      tokens = lexer.tokenize(reader);
    }

    return new LexicalResult(List.copyOf(tokens), lexer.getSymbolTable());
  }

  private static Path ensureOutputDirectory() throws IOException {
    Path binDir = Paths.get(COMPILER_BIN_DIR);
    if (!Files.exists(binDir)) {
      Files.createDirectories(binDir);
    }
    return binDir;
  }

  private static void writeLexerOutputFile(LexicalResult lexical, Path outputPath) throws IOException {
    try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath.toFile()))) {
      writer.println("tablica znakova:");
      writer.println("indeks   uniformni znak   izvorni tekst");
      List<SymbolTableEntry> symbolTable = lexical.symbolTable();
      for (int i = 0; i < symbolTable.size(); i++) {
        SymbolTableEntry entry = symbolTable.get(i);
        writer.printf("     %d   %-18s %s%n", i, entry.token(), entry.text());
      }

      writer.println("\nniz uniformnih znakova:");
      writer.println("uniformni znak    redak    indeks u tablicu znakova");
      for (Token token : lexical.tokens()) {
        writer.printf("%-18s %5d       %d%n",
            token.type(),
            token.line(),
            token.symbolTableIndex());
      }
    }
  }

  private static ParseTree runParser(List<Token> lexerTokens) throws ParserException {
    List<TokenReader.Token> parserTokens = new ArrayList<>(lexerTokens.size());
    for (Token token : lexerTokens) {
      parserTokens.add(new TokenReader.Token(token.type(), token.line(), token.value()));
    }
    Parser parser = new Parser();
    return parser.parseTokens(parserTokens);
  }

  private static void writeParseOutputs(ParseTree parseTree, Path generativePath, Path syntaxPath)
      throws IOException {
    Files.writeString(generativePath, parseTree.toGenerativeTreeString());
    Files.writeString(syntaxPath, parseTree.toSyntaxTreeString());
  }
}
