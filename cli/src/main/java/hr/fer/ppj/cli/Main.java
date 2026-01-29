package hr.fer.ppj.cli;

import hr.fer.ppj.ir.IrPipeline;
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
import hr.fer.ppj.semantics.io.SemanticReport;
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
 * <p>This class provides a unified entry point for all compiler operations:
 * lexical analysis, syntax analysis, semantic analysis, code generation, and
 * FRISC program execution.
 *
 * <p><strong>Available Commands:</strong>
 * <ul>
 *   <li>{@code lexer <file>} - Perform only lexical analysis, output to stdout</li>
 *   <li>{@code syntax <file>} - Perform lexical and syntax analysis, generate output files in compiler-bin/</li>
 *   <li>{@code semantic <file>} - Perform full compilation (lexical + syntax + semantic + code generation)</li>
 *   <li>{@code run <frisc-file>} - Execute FRISC assembly via simulator and print R6 result to stdout</li>
 *   <li>{@code <file>} - Full compilation (default, same as semantic)</li>
 * </ul>
 *
 * <p><strong>Output Files:</strong>
 * <p>For {@code syntax} and {@code semantic} modes, output files are generated in
 * the {@code compiler-bin/} directory (created automatically if it doesn't exist):
 * <ul>
 *   <li>{@code leksicke_jedinke.txt} - Lexical tokens output</li>
 *   <li>{@code generativno_stablo.txt} - Generative tree</li>
 *   <li>{@code sintaksno_stablo.txt} - Syntax tree</li>
 *   <li>{@code tablica_simbola.txt} - Symbol table (semantic mode only)</li>
 *   <li>{@code semanticko_stablo.txt} - Semantic tree (semantic mode only)</li>
   *   <li>{@code medukod.ir} - Generated IR code (semantic mode only)</li>
 * </ul>
 *
 * <p><strong>Error Handling:</strong>
 * <p>All error messages are written to {@code System.err}. Successful execution
 * results (e.g., R6 register value from FRISC simulator) are written to
 * {@code System.out}.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class Main {

  /**
   * Name of the output directory for compiler artifacts.
   */
  private static final String COMPILER_BIN_DIR = "compiler-bin";

  /**
   * Result of lexical analysis containing tokens and symbol table.
   *
   * @param tokens the list of tokens produced by the lexer
   * @param symbolTable the symbol table mapping indices to token entries
   */
  private record LexicalResult(List<Token> tokens, List<SymbolTableEntry> symbolTable) {}

  /**
   * Compilation artifacts containing lexical analysis results and parse tree.
   *
   * @param lexical the lexical analysis result
   * @param parseTree the parse tree produced by the parser
   */
  private record CompilationArtifacts(LexicalResult lexical, ParseTree parseTree) {}
  
  /**
   * Main entry point for the compiler CLI.
   *
   * <p>Parses command-line arguments and delegates to the appropriate handler
   * method based on the command.
   *
   * @param args command-line arguments
   */
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

      // Validate command arguments
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

      // Dispatch to appropriate command handler
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
        case "run":
          runFrisc(filePath);
          break;
        default:
          System.err.println("Error: Unknown command: " + command);
          printUsage();
          System.exit(1);
      }
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }
  
  /**
   * Prints usage information to stderr.
   */
  private static void printUsage() {
    System.err.println("Usage:");
    System.err.println("  lexer <file>     - Perform only lexical analysis (output to stdout)");
    System.err.println("  syntax <file>    - Perform lexical and syntax analysis (output to compiler-bin/)");
    System.err.println("  semantic <file>  - Perform full compilation (lexical + syntax + code generation)");
    System.err.println("  run <frisc-file> - Execute FRISC assembly via simulator and print R6 result to stdout");
    System.err.println("  <file>           - Full compilation (same as semantic)");
  }

  /**
   * Checks if the given argument is a recognized command.
   *
   * @param arg the argument to check
   * @return true if the argument is a command, false otherwise
   */
  private static boolean isCommand(String arg) {
    return arg.equals("lexer") || arg.equals("syntax") || arg.equals("semantic") || arg.equals("run");
  }

  /**
   * Runs full compilation pipeline (equivalent to semantic command).
   *
   * @param filePath path to the source file to compile
   * @throws Exception if compilation fails
   */
  private static void runFullCompilation(String filePath) throws Exception {
    runSemantic(filePath);
  }
  
  /**
   * Runs lexical analysis only.
   *
   * <p>Performs lexical analysis on the input file and outputs the results
   * to stdout in the standard format:
   * <ul>
   *   <li>Symbol table (tablica znakova)</li>
   *   <li>Token stream (niz uniformnih znakova)</li>
   * </ul>
   *
   * @param filePath path to the source file to analyze
   * @throws Exception if lexical analysis fails
   */
  private static void runLexer(String filePath) throws Exception {
    LexicalResult lexical = performLexicalAnalysis(filePath);

    // Output symbol table
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
   *
   * <p>Performs lexical and syntax analysis on the input file and generates
   * output files in the {@code compiler-bin/} directory:
   * <ul>
   *   <li>{@code leksicke_jedinke.txt} - Lexical tokens output</li>
   *   <li>{@code generativno_stablo.txt} - Generative tree</li>
   *   <li>{@code sintaksno_stablo.txt} - Syntax tree</li>
   * </ul>
   *
   * <p>The output directory is created automatically if it doesn't exist.
   *
   * @param filePath path to the source file to analyze
   * @throws Exception if analysis fails
   */
  private static void runSyntax(String filePath) throws Exception {
    Path binDir = ensureOutputDirectory();
    compileToParseTree(filePath, binDir, true);
    System.err.println("Lexical and syntax analysis completed. Output files generated in " + COMPILER_BIN_DIR + "/");
  }
  
  /**
   * Runs full compilation pipeline (lexical + syntax + semantic + code generation).
   *
   * <p>Performs all compilation phases and generates all output files in the
   * {@code compiler-bin/} directory:
   * <ul>
   *   <li>Lexical analysis output files</li>
   *   <li>Syntax analysis output files</li>
   *   <li>{@code tablica_simbola.txt} - Symbol table</li>
   *   <li>{@code semanticko_stablo.txt} - Semantic tree</li>
   *   <li>{@code medukod.ir} - Generated IR code</li>
   * </ul>
   *
   * <p>The output directory is created automatically if it doesn't exist.
   *
   * @param filePath path to the source file to compile
   * @throws Exception if compilation fails
   */
  private static void runSemantic(String filePath) throws Exception {
    Path binDir = ensureOutputDirectory();
    CompilationArtifacts artifacts = compileToParseTree(filePath, binDir, true);
    SemanticAnalyzer analyzer = new SemanticAnalyzer();

    // Create semantic report for compiler-bin directory
    SemanticReport semanticReport = SemanticReport.forDirectory(binDir.toString());

    try {
      // Perform semantic analysis and get results
      SemanticAnalyzer.SemanticAnalysisResult semanticResults = analyzer.analyzeWithResults(
          artifacts.parseTree(), System.out, semanticReport);
      System.err.println("Semantic analysis completed.");

      // Generate IR
      hr.fer.ppj.ir.model.IrProgram irProgram = IrPipeline.generate(
          semanticResults.globalScope(), semanticResults.parseTree());
      String irString = IrPipeline.print(irProgram);
      Path irOutputPath = binDir.resolve("medukod.ir");
      Files.writeString(irOutputPath, irString);
      System.err.println("IR generation completed. Generated " + irOutputPath);

    } catch (hr.fer.ppj.semantics.errors.SemanticException e) {
      // Production already printed by SemanticChecker.fail()
      // Print error message and exit without stack trace
      System.err.println("Error: semantic error");
      System.exit(1);
    } catch (Exception e) {
      System.err.println("Error during code generation: " + e.getMessage());
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }

  // ---------------------------------------------------------------------------
  // Helper Methods
  // ---------------------------------------------------------------------------

  /**
   * Compiles source file to parse tree, optionally writing intermediate outputs.
   *
   * @param filePath path to the source file
   * @param outputDir directory where output files should be written
   * @param writeOutputs whether to write intermediate output files
   * @return compilation artifacts containing lexical results and parse tree
   * @throws Exception if compilation fails
   */
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

  /**
   * Performs lexical analysis on the source file.
   *
   * @param filePath path to the source file
   * @return lexical analysis result containing tokens and symbol table
   * @throws Exception if lexical analysis fails
   */
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

  /**
   * Ensures the output directory exists, creating it if necessary.
   *
   * @return the path to the output directory
   * @throws IOException if the directory cannot be created
   */
  private static Path ensureOutputDirectory() throws IOException {
    Path binDir = Paths.get(COMPILER_BIN_DIR);
    if (!Files.exists(binDir)) {
      Files.createDirectories(binDir);
    }
    return binDir;
  }

  /**
   * Writes lexical analysis output to a file.
   *
   * @param lexical the lexical analysis result
   * @param outputPath path where the output file should be written
   * @throws IOException if the file cannot be written
   */
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

  /**
   * Runs the parser on a list of tokens.
   *
   * @param lexerTokens the tokens produced by the lexer
   * @return the parse tree produced by the parser
   * @throws ParserException if parsing fails
   */
  private static ParseTree runParser(List<Token> lexerTokens) throws ParserException {
    List<TokenReader.Token> parserTokens = new ArrayList<>(lexerTokens.size());
    for (Token token : lexerTokens) {
      parserTokens.add(new TokenReader.Token(token.type(), token.line(), token.value()));
    }
    Parser parser = new Parser();
    return parser.parseTokens(parserTokens);
  }

  /**
   * Writes parse tree outputs to files.
   *
   * @param parseTree the parse tree to write
   * @param generativePath path for the generative tree output
   * @param syntaxPath path for the syntax tree output
   * @throws IOException if the files cannot be written
   */
  private static void writeParseOutputs(ParseTree parseTree, Path generativePath, Path syntaxPath)
      throws IOException {
    Files.writeString(generativePath, parseTree.toGenerativeTreeString());
    Files.writeString(syntaxPath, parseTree.toSyntaxTreeString());
  }

  /**
   * Executes a FRISC assembly file via the FRISC simulator.
   *
   * <p>Runs the FRISC assembly program using the FRISCjs simulator and prints
   * the decimal value of register R6 (the program's return value) to stdout.
   * If execution fails, error messages are printed to stderr.
   *
   * <p>The FRISC simulator outputs the R6 register value as a decimal number
   * on stdout. This method reads that value directly without any conversion.
   *
   * @param friscPath path to the FRISC assembly file to execute
   * @throws IOException if an I/O error occurs
   * @throws InterruptedException if the current thread is interrupted
   */
  private static void runFrisc(String friscPath) throws IOException, InterruptedException {
    Path friscFile = Paths.get(friscPath);
    FriscRunner runner = new FriscRunner(Paths.get("").toAbsolutePath());
    FriscRunner.Result result = runner.run(friscFile);

    if (result.success()) {
      // R6 value from simulator is already in decimal format
      String r6Value = result.r6Value();
      System.out.println(r6Value);
    } else {
      // Print error message to stderr
      System.err.println("Error: " + result.errorMessage());
      if (result.output() != null && !result.output().isBlank()) {
        System.err.println(result.output().trim());
      }
      System.exit(1);
    }
  }
}
