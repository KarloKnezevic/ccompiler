package hr.fer.ppj.cli;

import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Lexer.SymbolTableEntry;
import hr.fer.ppj.lexer.io.Token;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    // For now, full compilation is the same as semantic analysis
    runSemantic(filePath);
  }
  
  /**
   * Runs lexical analysis only.
   * Output is written to stdout in the standard format.
   */
  private static void runLexer(String filePath) throws Exception {
    // Load lexer definition from centralized configuration
    Path specPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
    LexerGenerator generator = new LexerGenerator();
    LexerGeneratorResult result;
    try (FileReader reader = new FileReader(specPath.toFile())) {
      result = generator.generate(reader);
    }
    
    // Tokenize input file
    Lexer lexer = new Lexer(result);
    List<Token> tokens;
    try (FileReader reader = new FileReader(filePath)) {
      tokens = lexer.tokenize(reader);
    }
    
    // Output symbol table
    System.out.println("tablica znakova:");
    System.out.println("indeks   uniformni znak   izvorni tekst");
    List<SymbolTableEntry> symbolTable = lexer.getSymbolTable();
    for (int i = 0; i < symbolTable.size(); i++) {
      SymbolTableEntry entry = symbolTable.get(i);
      System.out.printf("     %d   %-18s %s%n", i, entry.token(), entry.text());
    }
    
    // Output token stream
    System.out.println("\nniz uniformnih znakova:");
    System.out.println("uniformni znak    redak    indeks u tablicu znakova");
    for (Token token : tokens) {
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
    // Ensure compiler-bin directory exists
    Path binDir = Paths.get(COMPILER_BIN_DIR);
    if (!Files.exists(binDir)) {
      Files.createDirectories(binDir);
    }
    
    // Run lexical analysis
    Path specPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
    LexerGenerator generator = new LexerGenerator();
    LexerGeneratorResult result;
    try (FileReader reader = new FileReader(specPath.toFile())) {
      result = generator.generate(reader);
    }
    
    Lexer lexer = new Lexer(result);
    List<Token> tokens;
    try (FileReader reader = new FileReader(filePath)) {
      tokens = lexer.tokenize(reader);
    }
    
    // Generate leksicke_jedinke.txt
    Path leksickePath = binDir.resolve("leksicke_jedinke.txt");
    try (PrintWriter writer = new PrintWriter(new FileWriter(leksickePath.toFile()))) {
      // Output symbol table
      writer.println("tablica znakova:");
      writer.println("indeks   uniformni znak   izvorni tekst");
      List<SymbolTableEntry> symbolTable = lexer.getSymbolTable();
      for (int i = 0; i < symbolTable.size(); i++) {
        SymbolTableEntry entry = symbolTable.get(i);
        writer.printf("     %d   %-18s %s%n", i, entry.token(), entry.text());
      }
      
      // Output token stream
      writer.println("\nniz uniformnih znakova:");
      writer.println("uniformni znak    redak    indeks u tablicu znakova");
      for (Token token : tokens) {
        writer.printf("%-18s %5d       %d%n", 
            token.type(), 
            token.line(), 
            token.symbolTableIndex());
      }
    }
    
    // TODO: Run syntax analysis and generate trees
    // For now, create placeholder files
    Path generativnoPath = binDir.resolve("generativno_stablo.txt");
    Path sintaksnoPath = binDir.resolve("sintaksno_stablo.txt");
    
    try (PrintWriter writer = new PrintWriter(new FileWriter(generativnoPath.toFile()))) {
      writer.println("Generative tree generation not yet implemented");
      writer.println("This will contain the generative tree output once parser is fully implemented.");
    }
    
    try (PrintWriter writer = new PrintWriter(new FileWriter(sintaksnoPath.toFile()))) {
      writer.println("Syntax tree generation not yet implemented");
      writer.println("This will contain the syntax tree output once parser is fully implemented.");
    }
    
    System.err.println("Lexical analysis completed. Output files generated in " + COMPILER_BIN_DIR + "/");
    System.err.println("Note: Syntax analysis is not yet fully implemented.");
  }
  
  /**
   * Runs full compilation pipeline (lexical + syntax + code generation).
   * Generates all output files in compiler-bin/ directory.
   */
  private static void runSemantic(String filePath) throws Exception {
    // For now, semantic is the same as syntax
    // TODO: Add code generation when implemented
    runSyntax(filePath);
    
    System.err.println("Note: Code generation is not yet implemented.");
  }
}
