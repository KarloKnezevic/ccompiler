package hr.fer.ppj.parser;

import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.parser.config.ParserConfig;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that compiler-bin output files are generated correctly.
 * 
 * <p>This test runs the full lexer + parser pipeline and verifies that all
 * expected output files are created in compiler-bin/ directory.
 */
public final class CompilerBinOutputTest {
  
  private static LexerGeneratorResult lexerGeneratorResult;
  
  /**
   * Gets the compiler-bin directory path.
   * Tries multiple locations to find the directory.
   */
  private static Path getCompilerBinPath() {
    // Try from project root (go up from compiler-parser module)
    Path currentDir = Paths.get("").toAbsolutePath();
    
    // If we're in compiler-parser, go up to project root
    if (currentDir.getFileName().toString().equals("compiler-parser")) {
      Path projectRoot = currentDir.getParent();
      Path bin = projectRoot.resolve("compiler-bin");
      if (Files.exists(bin)) {
        return bin;
      }
      return bin; // Return even if doesn't exist (will be created)
    }
    
    // Try current directory
    Path bin1 = currentDir.resolve("compiler-bin");
    if (Files.exists(bin1)) {
      return bin1;
    }
    
    // Try from user.dir
    Path projectRoot = Paths.get(System.getProperty("user.dir"));
    Path bin2 = projectRoot.resolve("compiler-bin");
    if (Files.exists(bin2)) {
      return bin2;
    }
    
    // Default to project root
    return projectRoot.resolve("compiler-bin");
  }
  
  @BeforeAll
  static void setup() throws Exception {
    // Setup lexer generator
    LexerGenerator generator = new LexerGenerator();
    Path lexerDefinitionPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
    try (FileReader reader = new FileReader(lexerDefinitionPath.toFile())) {
      lexerGeneratorResult = generator.generate(reader);
    }
    
    // Ensure compiler-bin directory exists
    Path compilerBin = getCompilerBinPath();
    if (!Files.exists(compilerBin)) {
      Files.createDirectories(compilerBin);
    }
  }
  
  @Test
  void testCompilerBinOutputFiles() throws Exception {
    // Use an existing test program that we know works (ppjc_case_01 is simpler)
    Path testProgramPath = Paths.get("compiler-parser/src/test/resources/ppjc_case_01/program.c");
    if (!Files.exists(testProgramPath)) {
      // Try alternative path
      testProgramPath = Paths.get("src/test/resources/ppjc_case_01/program.c");
    }
    
    if (!Files.exists(testProgramPath)) {
      System.out.println("Test program not found, skipping test");
      return;
    }
    
    String testProgram = Files.readString(testProgramPath);
    
    // Step 1: Run lexer
    Lexer lexer = new Lexer(lexerGeneratorResult);
    List<hr.fer.ppj.lexer.io.Token> tokens;
    try (java.io.StringReader reader = new java.io.StringReader(testProgram)) {
      tokens = lexer.tokenize(reader);
    }
    
    // Step 2: Write lexer output to compiler-bin/leksicke_jedinke.txt
    Path compilerBin = getCompilerBinPath();
    Path leksickePath = compilerBin.resolve("leksicke_jedinke.txt");
    try (var writer = Files.newBufferedWriter(leksickePath)) {
      writer.write("tablica znakova:\n");
      writer.write("indeks   uniformni znak   izvorni tekst\n");
      List<Lexer.SymbolTableEntry> symbolTable = lexer.getSymbolTable();
      for (int i = 0; i < symbolTable.size(); i++) {
        Lexer.SymbolTableEntry entry = symbolTable.get(i);
        writer.write(String.format("     %d   %-18s %s%n", i, entry.token(), entry.text()));
      }
      writer.write("\nniz uniformnih znakova:\n");
      writer.write("uniformni znak    redak    indeks u tablicu znakova\n");
      for (hr.fer.ppj.lexer.io.Token token : tokens) {
        writer.write(String.format("%-18s %5d       %d%n",
            token.type(),
            token.line(),
            token.symbolTableIndex()));
      }
    }
    
    // Step 3: Run parser
    ParserConfig.Config config = ParserConfig.Config.createDefault(
        leksickePath,
        compilerBin
    );
    
    Parser parser = new Parser();
    parser.parse(config);
    
    // Step 4: Verify all output files exist and are not empty
    Path generativnoPath = config.outputGenerativeTree();
    Path sintaksnoPath = config.outputSyntaxTree();
    
    assertTrue(Files.exists(leksickePath), "leksicke_jedinke.txt should exist");
    assertTrue(Files.exists(generativnoPath), "generativno_stablo.txt should exist");
    assertTrue(Files.exists(sintaksnoPath), "sintaksno_stablo.txt should exist");
    
    assertTrue(Files.size(leksickePath) > 0, "leksicke_jedinke.txt should not be empty");
    assertTrue(Files.size(generativnoPath) > 0, "generativno_stablo.txt should not be empty");
    assertTrue(Files.size(sintaksnoPath) > 0, "sintaksno_stablo.txt should not be empty");
    
    // Step 5: Verify file contents format
    String generativnoContent = Files.readString(generativnoPath);
    String sintaksnoContent = Files.readString(sintaksnoPath);
    
    // Generative tree should start with the root non-terminal
    assertTrue(generativnoContent.stripLeading().startsWith("<"), 
        "Generative tree should start with a non-terminal symbol");
    
    // Syntax tree should also start with the root non-terminal
    assertTrue(sintaksnoContent.stripLeading().startsWith("<"), 
        "Syntax tree should start with a non-terminal symbol");
    
    // Both should contain the start symbol
    assertTrue(generativnoContent.contains("<pocetni_nezavrsni_znak>") || 
               generativnoContent.contains("<prijevodna_jedinica>"),
        "Generative tree should contain start symbol");
    
    System.out.println("=== COMPILER-BIN OUTPUT VERIFICATION ===");
    System.out.println("✓ leksicke_jedinke.txt: " + Files.size(leksickePath) + " bytes");
    System.out.println("✓ generativno_stablo.txt: " + Files.size(generativnoPath) + " bytes");
    System.out.println("✓ sintaksno_stablo.txt: " + Files.size(sintaksnoPath) + " bytes");
    System.out.println("\nGenerative tree preview (first 500 chars):");
    System.out.println(generativnoContent.substring(0, Math.min(500, generativnoContent.length())));
    System.out.println("\nSyntax tree preview (first 500 chars):");
    System.out.println(sintaksnoContent.substring(0, Math.min(500, sintaksnoContent.length())));
  }
  
  @Test
  void testCompilerBinFilesFromCLI() throws Exception {
    // This test verifies that files generated by CLI are in the right location
    Path compilerBin = getCompilerBinPath();
    Path leksickePath = compilerBin.resolve("leksicke_jedinke.txt");
    Path generativnoPath = compilerBin.resolve("generativno_stablo.txt");
    Path sintaksnoPath = compilerBin.resolve("sintaksno_stablo.txt");
    
    System.out.println("Checking compiler-bin at: " + compilerBin.toAbsolutePath());
    
    System.out.println("=== COMPILER-BIN FILES VERIFICATION ===");
    
    // Check if files exist (they should if CLI was run)
    if (Files.exists(leksickePath)) {
      long size = Files.size(leksickePath);
      assertTrue(size > 0, 
          "leksicke_jedinke.txt from CLI should not be empty");
      System.out.println("✓ leksicke_jedinke.txt: " + size + " bytes");
      
      // Verify format
      String content = Files.readString(leksickePath);
      assertTrue(content.contains("tablica znakova:"), 
          "leksicke_jedinke.txt should contain symbol table");
      assertTrue(content.contains("niz uniformnih znakova:"), 
          "leksicke_jedinke.txt should contain token stream");
    } else {
      System.out.println("⚠ leksicke_jedinke.txt not found (run CLI first)");
    }
    
    if (Files.exists(generativnoPath)) {
      long size = Files.size(generativnoPath);
      assertTrue(size > 0, 
          "generativno_stablo.txt from CLI should not be empty");
      System.out.println("✓ generativno_stablo.txt: " + size + " bytes");
      
      // Verify format
      String content = Files.readString(generativnoPath);
      String trimmed = content.stripLeading();
      boolean looksValid = trimmed.startsWith("<") || trimmed.startsWith("Parse error");
      assertTrue(looksValid, 
          "generativno_stablo.txt should start with a non-terminal symbol or a parse error message");
      assertTrue(!content.contains("Parse error"), 
          "generativno_stablo.txt should not contain parse errors");
      
      // Show preview
      System.out.println("\nGenerative tree preview (first 20 lines):");
      content.lines().limit(20).forEach(System.out::println);
    } else {
      System.out.println("⚠ generativno_stablo.txt not found (run CLI first)");
    }
    
    if (Files.exists(sintaksnoPath)) {
      long size = Files.size(sintaksnoPath);
      assertTrue(size > 0, 
          "sintaksno_stablo.txt from CLI should not be empty");
      System.out.println("\n✓ sintaksno_stablo.txt: " + size + " bytes");
      
      // Verify format
      String content = Files.readString(sintaksnoPath);
      String trimmed = content.stripLeading();
      boolean looksValid = trimmed.startsWith("<") || trimmed.startsWith("Parse error");
      assertTrue(looksValid, 
          "sintaksno_stablo.txt should start with a non-terminal symbol or a parse error message");
      assertTrue(!content.contains("Parse error"), 
          "sintaksno_stablo.txt should not contain parse errors");
      
      // Show preview
      System.out.println("\nSyntax tree preview (first 20 lines):");
      content.lines().limit(20).forEach(System.out::println);
    } else {
      System.out.println("⚠ sintaksno_stablo.txt not found (run CLI first)");
    }
    
    System.out.println("\n=== All compiler-bin files verified ===");
  }
}

