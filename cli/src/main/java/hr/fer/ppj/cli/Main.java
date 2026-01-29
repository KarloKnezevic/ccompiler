package hr.fer.ppj.cli;

import hr.fer.ppj.cli.args.ArgsParser;
import hr.fer.ppj.cli.args.ArgsParser.ArgsParseException;
import hr.fer.ppj.cli.args.CliOptions;
import hr.fer.ppj.cli.commands.Command;
import hr.fer.ppj.cli.commands.HelpCommand;
import hr.fer.ppj.cli.commands.IrCommand;
import hr.fer.ppj.cli.commands.IrTestCommand;
import hr.fer.ppj.cli.pipeline.CompilationPipeline;
import hr.fer.ppj.cli.pipeline.CompilationPipeline.LexicalResult;
import hr.fer.ppj.ir.IrPipeline;
import hr.fer.ppj.lexer.io.Lexer.SymbolTableEntry;
import hr.fer.ppj.lexer.io.Token;
import hr.fer.ppj.parser.tree.ParseTree;
import hr.fer.ppj.semantics.analysis.SemanticAnalyzer;
import hr.fer.ppj.semantics.io.SemanticReport;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Command-line interface for the PPJ compiler.
 *
 * <p>This class provides a unified entry point for all compiler operations:
 * lexical analysis, syntax analysis, semantic analysis, IR generation, and
 * FRISC program execution.
 *
 * <p><strong>Available Commands:</strong>
 * <ul>
 *   <li>{@code lexer <file>} - Lexical analysis only (output to stdout)</li>
 *   <li>{@code syntax <file>} - Lexical and syntax analysis</li>
 *   <li>{@code semantic <file>} - Full compilation pipeline</li>
 *   <li>{@code ir --in <file> [--out <dir>]} - Generate IR for single file</li>
 *   <li>{@code ir-test --golden <dir> [--out <dir>]} - Run golden IR tests</li>
 *   <li>{@code run <frisc-file>} - Execute FRISC assembly</li>
 *   <li>{@code <file>} - Full compilation (default, same as semantic)</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class Main {

  private Main() {}

  public static void main(String[] args) {
    if (args.length == 0) {
      HelpCommand.printUsage();
      System.exit(1);
    }

    try {
      CliOptions options = ArgsParser.parse(args);
      int exitCode = dispatch(options);
      System.exit(exitCode);
    } catch (ArgsParseException e) {
      System.err.println("Error: " + e.getMessage());
      System.err.println();
      HelpCommand.printUsage();
      System.exit(1);
    }
  }

  private static int dispatch(CliOptions options) {
    return switch (options.command()) {
      case HELP -> new HelpCommand().execute(options);
      case IR -> new IrCommand().execute(options);
      case IR_TEST -> new IrTestCommand().execute(options);
      case LEXER -> runLexer(options);
      case SYNTAX -> runSyntax(options);
      case SEMANTIC -> runSemantic(options);
      case RUN -> runFrisc(options);
    };
  }

  // -------------------------------------------------------------------------
  // Legacy commands (kept for backwards compatibility)
  // -------------------------------------------------------------------------

  private static int runLexer(CliOptions options) {
    try {
      Path inputFile = options.inputFile().orElseThrow();
      validateInputFile(inputFile);

      CompilationPipeline pipeline = new CompilationPipeline();
      LexicalResult lexical = pipeline.lexicalAnalysis(inputFile);

      System.out.println("tablica znakova:");
      System.out.println("indeks   uniformni znak   izvorni tekst");
      List<SymbolTableEntry> symbolTable = lexical.symbolTable();
      for (int i = 0; i < symbolTable.size(); i++) {
        SymbolTableEntry entry = symbolTable.get(i);
        System.out.printf("     %d   %-18s %s%n", i, entry.token(), entry.text());
      }

      System.out.println("\nniz uniformnih znakova:");
      System.out.println("uniformni znak    redak    indeks u tablicu znakova");
      for (Token token : lexical.tokens()) {
        System.out.printf("%-18s %5d       %d%n",
            token.type(), token.line(), token.symbolTableIndex());
      }

      return 0;
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      return 1;
    }
  }

  private static int runSyntax(CliOptions options) {
    try {
      Path inputFile = options.inputFile().orElseThrow();
      validateInputFile(inputFile);
      Path outputDir = ensureOutputDirectory(options.outputDir());

      CompilationPipeline pipeline = new CompilationPipeline();
      LexicalResult lexical = pipeline.lexicalAnalysis(inputFile);
      writeLexerOutputFile(lexical, outputDir.resolve("leksicke_jedinke.txt"));

      ParseTree parseTree = pipeline.syntaxAnalysis(lexical);
      writeParseOutputs(parseTree,
          outputDir.resolve("generativno_stablo.txt"),
          outputDir.resolve("sintaksno_stablo.txt"));

      System.err.println("Lexical and syntax analysis completed. Output in " + outputDir);
      return 0;
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      return 1;
    }
  }

  private static int runSemantic(CliOptions options) {
    try {
      Path inputFile = options.inputFile().orElseThrow();
      validateInputFile(inputFile);
      Path outputDir = ensureOutputDirectory(options.outputDir());

      CompilationPipeline pipeline = new CompilationPipeline();

      // Lexical analysis
      LexicalResult lexical = pipeline.lexicalAnalysis(inputFile);
      writeLexerOutputFile(lexical, outputDir.resolve("leksicke_jedinke.txt"));

      // Syntax analysis
      ParseTree parseTree = pipeline.syntaxAnalysis(lexical);
      writeParseOutputs(parseTree,
          outputDir.resolve("generativno_stablo.txt"),
          outputDir.resolve("sintaksno_stablo.txt"));

      // Semantic analysis with report
      SemanticAnalyzer analyzer = new SemanticAnalyzer();
      SemanticReport report = SemanticReport.forDirectory(outputDir.toString());
      SemanticAnalyzer.SemanticAnalysisResult semantic =
          analyzer.analyzeWithResults(parseTree, System.out, report);
      System.err.println("Semantic analysis completed.");

      // IR generation
      hr.fer.ppj.ir.model.IrProgram irProgram = IrPipeline.generate(
          semantic.globalScope(), semantic.parseTree());
      String irString = IrPipeline.print(irProgram);
      Path irOutputPath = outputDir.resolve("medukod.ir");
      Files.writeString(irOutputPath, irString);
      System.err.println("IR generation completed: " + irOutputPath);

      return 0;

    } catch (hr.fer.ppj.semantics.errors.SemanticException e) {
      System.err.println("Error: semantic error");
      return 1;
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace(System.err);
      return 1;
    }
  }

  private static int runFrisc(CliOptions options) {
    try {
      Path friscFile = options.inputFile().orElseThrow();
      validateInputFile(friscFile);

      FriscRunner runner = new FriscRunner(Paths.get("").toAbsolutePath());
      FriscRunner.Result result = runner.run(friscFile);

      if (result.success()) {
        System.out.println(result.r6Value());
        return 0;
      } else {
        System.err.println("Error: " + result.errorMessage());
        if (result.output() != null && !result.output().isBlank()) {
          System.err.println(result.output().trim());
        }
        return 1;
      }
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      return 1;
    }
  }

  // -------------------------------------------------------------------------
  // Helper methods
  // -------------------------------------------------------------------------

  private static void validateInputFile(Path inputFile) throws IOException {
    if (!Files.exists(inputFile)) {
      throw new IOException("File not found: " + inputFile);
    }
  }

  private static Path ensureOutputDirectory(Path outputDir) throws IOException {
    if (!Files.exists(outputDir)) {
      Files.createDirectories(outputDir);
    }
    return outputDir;
  }

  private static void writeLexerOutputFile(LexicalResult lexical, Path outputPath)
      throws IOException {
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
            token.type(), token.line(), token.symbolTableIndex());
      }
    }
  }

  private static void writeParseOutputs(ParseTree parseTree, Path generativePath, Path syntaxPath)
      throws IOException {
    Files.writeString(generativePath, parseTree.toGenerativeTreeString());
    Files.writeString(syntaxPath, parseTree.toSyntaxTreeString());
  }
}
