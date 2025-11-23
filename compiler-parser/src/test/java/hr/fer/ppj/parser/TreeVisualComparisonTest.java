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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Generates visual HTML comparison of expected vs actual parse trees for all test cases.
 * 
 * <p>Run this test to generate a detailed HTML report comparing trees.
 */
public final class TreeVisualComparisonTest {
  
  @Test
  void generateVisualComparison() throws Exception {
    // Use a fixed location in target directory for easier access
    Path outputDir = Paths.get("target/tree-comparison");
    Files.createDirectories(outputDir);
    
    // Setup lexer
    LexerGenerator generator = new LexerGenerator();
    Path lexerDefinitionPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
    LexerGeneratorResult lexerResult;
    try (FileReader reader = new FileReader(lexerDefinitionPath.toFile())) {
      lexerResult = generator.generate(reader);
    }
    
    // Find all test cases
    Path testResources = Paths.get("compiler-parser/src/test/resources");
    if (!Files.exists(testResources)) {
      // Try alternative path
      testResources = Paths.get("src/test/resources");
    }
    
    List<TestCase> testCases = new ArrayList<>();
    
    if (Files.exists(testResources)) {
      Files.list(testResources)
          .filter(Files::isDirectory)
          .filter(p -> p.getFileName().toString().startsWith("ppjc_case_"))
          .sorted()
          .forEach(caseDir -> {
            try {
              String caseId = caseDir.getFileName().toString();
              Path programFile = caseDir.resolve("program.c");
              Path expectedGenFile = caseDir.resolve("generativno_stablo.txt");
              Path expectedSynFile = caseDir.resolve("sintaksno_stablo.txt");
              
              if (Files.exists(programFile) && Files.exists(expectedGenFile) && Files.exists(expectedSynFile)) {
                testCases.add(new TestCase(
                    caseId,
                    caseDir,
                    programFile,
                    expectedGenFile,
                    expectedSynFile
                ));
              }
            } catch (Exception e) {
              System.err.println("Error loading test case from " + caseDir + ": " + e.getMessage());
            }
          });
    }
    
    System.out.println("Found " + testCases.size() + " test cases");
    
    // Process each test case
    List<ComparisonResult> results = new ArrayList<>();
    for (TestCase testCase : testCases) {
      System.out.println("Processing " + testCase.caseId + "...");
      ComparisonResult result = processTestCase(testCase, lexerResult, outputDir);
      results.add(result);
    }
    
    // Generate HTML report
    Path htmlReport = generateHTMLReport(results, outputDir);
    System.out.println("\n=== COMPARISON REPORT GENERATED ===");
    System.out.println("Report location: " + htmlReport.toAbsolutePath());
    System.out.println("Open this file in a web browser to view the visual comparison.");
  }
  
  private static ComparisonResult processTestCase(TestCase testCase, 
                                                  LexerGeneratorResult lexerResult,
                                                  Path outputDir) {
    ComparisonResult result = new ComparisonResult(testCase.caseId);
    
    try {
      // Read program
      String program = Files.readString(testCase.programFile);
      
      // Tokenize
      Lexer lexer = new Lexer(lexerResult);
      List<hr.fer.ppj.lexer.io.Token> tokens;
      try (java.io.StringReader reader = new java.io.StringReader(program)) {
        tokens = lexer.tokenize(reader);
      }
      
      // Format lexer output
      String lexerOutput = formatLexerOutput(lexer, tokens);
      Path caseDir = outputDir.resolve(testCase.caseId);
      Files.createDirectories(caseDir);
      Path tokenFile = caseDir.resolve("leksicke_jedinke.txt");
      Files.writeString(tokenFile, lexerOutput);
      
      // Run parser
      ParserConfig.Config config = ParserConfig.Config.createDefault(
          tokenFile,
          caseDir
      );
      
      Parser parser = new Parser();
      try {
        parser.parse(config);
        result.success = true;
        
        // Read actual outputs
        String actualGen = Files.readString(config.outputGenerativeTree());
        String actualSyn = Files.readString(config.outputSyntaxTree());
        
        // Read expected outputs
        String expectedGen = Files.readString(testCase.expectedGenFile);
        String expectedSyn = Files.readString(testCase.expectedSynFile);
        
        result.actualGenerative = actualGen;
        result.expectedGenerative = expectedGen;
        result.actualSyntax = actualSyn;
        result.expectedSyntax = expectedSyn;
        
        // Check if they match
        result.generativeMatch = normalizeWhitespace(actualGen).equals(normalizeWhitespace(expectedGen));
        result.syntaxMatch = normalizeWhitespace(actualSyn).equals(normalizeWhitespace(expectedSyn));
        
        // Save individual comparison files
        saveComparisonFile(caseDir, "generativno_stablo_comparison.txt", 
            "=== EXPECTED ===\n" + expectedGen + "\n\n=== ACTUAL ===\n" + actualGen);
        saveComparisonFile(caseDir, "sintaksno_stablo_comparison.txt",
            "=== EXPECTED ===\n" + expectedSyn + "\n\n=== ACTUAL ===\n" + actualSyn);
        
      } catch (Parser.ParserException e) {
        result.success = false;
        result.errorMessage = e.getMessage();
        System.err.println("Parse error for " + testCase.caseId + ": " + e.getMessage());
      }
      
    } catch (Exception e) {
      result.success = false;
      result.errorMessage = e.getMessage();
      System.err.println("Error processing " + testCase.caseId + ": " + e.getMessage());
      e.printStackTrace();
    }
    
    return result;
  }
  
  private static String formatLexerOutput(Lexer lexer, List<hr.fer.ppj.lexer.io.Token> tokens) {
    StringBuilder sb = new StringBuilder();
    
    // Symbol table
    sb.append("tablica znakova:\n");
    sb.append("indeks   uniformni znak   izvorni tekst\n");
    List<Lexer.SymbolTableEntry> symbolTable = lexer.getSymbolTable();
    for (int i = 0; i < symbolTable.size(); i++) {
      Lexer.SymbolTableEntry entry = symbolTable.get(i);
      sb.append(String.format("     %d   %-18s %s%n", i, entry.token(), entry.text()));
    }
    
    // Token stream
    sb.append("\nniz uniformnih znakova:\n");
    sb.append("uniformni znak    redak    indeks u tablicu znakova\n");
    for (hr.fer.ppj.lexer.io.Token token : tokens) {
      sb.append(String.format("%-18s %5d       %d%n",
          token.type(),
          token.line(),
          token.symbolTableIndex()));
    }
    
    return sb.toString();
  }
  
  private static String normalizeWhitespace(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    return text.lines()
        .map(line -> line.replaceAll("\\s+$", ""))
        .reduce((a, b) -> a + "\n" + b)
        .orElse("")
        .trim();
  }
  
  private static void saveComparisonFile(Path dir, String filename, String content) throws IOException {
    Files.writeString(dir.resolve(filename), content);
  }
  
  private static Path generateHTMLReport(List<ComparisonResult> results, Path outputDir) throws IOException {
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html>\n");
    html.append("<html><head><title>Parse Tree Comparison</title>\n");
    html.append("<meta charset='UTF-8'>\n");
    html.append("<style>\n");
    html.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 20px; background: #f5f5f5; }\n");
    html.append("h1 { color: #333; border-bottom: 3px solid #4CAF50; padding-bottom: 10px; }\n");
    html.append("h2 { color: #666; margin-top: 30px; }\n");
    html.append("h3 { color: #444; margin-top: 20px; }\n");
    html.append("h4 { color: #555; margin-top: 15px; font-size: 14px; }\n");
    html.append(".test-case { border: 2px solid #ccc; margin: 20px 0; padding: 15px; background: white; border-radius: 5px; }\n");
    html.append(".success { border-color: #4CAF50; background: #e8f5e9; }\n");
    html.append(".failure { border-color: #f44336; background: #ffebee; }\n");
    html.append(".error { border-color: #ff9800; background: #fff3e0; }\n");
    html.append(".comparison { display: flex; gap: 20px; margin: 10px 0; }\n");
    html.append(".tree-panel { flex: 1; border: 1px solid #ddd; padding: 10px; background: #fafafa; border-radius: 3px; }\n");
    html.append(".tree-panel h3 { margin-top: 0; color: #333; font-size: 16px; }\n");
    html.append(".tree-content { white-space: pre-wrap; font-family: 'Courier New', monospace; font-size: 11px; max-height: 600px; overflow: auto; background: white; padding: 10px; border: 1px solid #eee; }\n");
    html.append(".match { color: #4CAF50; font-weight: bold; }\n");
    html.append(".mismatch { color: #f44336; font-weight: bold; }\n");
    html.append(".summary { background: white; padding: 15px; border-radius: 5px; margin: 20px 0; }\n");
    html.append(".summary p { margin: 5px 0; font-size: 16px; }\n");
    html.append("</style>\n");
    html.append("</head><body>\n");
    html.append("<h1>🌳 Parse Tree Comparison Report</h1>\n");
    
    // Summary
    long successCount = results.stream().filter(r -> r.success && r.generativeMatch && r.syntaxMatch).count();
    long failureCount = results.stream().filter(r -> r.success && (!r.generativeMatch || !r.syntaxMatch)).count();
    long errorCount = results.stream().filter(r -> !r.success).count();
    
    html.append("<div class='summary'>\n");
    html.append("<h2>Summary</h2>\n");
    html.append("<p><strong>Total test cases:</strong> " + results.size() + "</p>\n");
    html.append("<p><span class='match'>✓ Passed: " + successCount + "</span></p>\n");
    html.append("<p><span class='mismatch'>✗ Failed: " + failureCount + "</span></p>\n");
    html.append("<p><span class='mismatch'>⚠ Errors: " + errorCount + "</span></p>\n");
    html.append("</div>\n");
    
    // Detailed results
    html.append("<h2>Detailed Results</h2>\n");
    for (ComparisonResult result : results) {
      String statusClass = result.success 
          ? (result.generativeMatch && result.syntaxMatch ? "success" : "failure")
          : "error";
      
      html.append("<div class='test-case " + statusClass + "'>\n");
      html.append("<h3>" + result.caseId + "</h3>\n");
      
      if (!result.success) {
        html.append("<p><strong>Error:</strong> <span style='color: red;'>" + escapeHtml(result.errorMessage) + "</span></p>\n");
      } else {
        html.append("<p><strong>Generative Tree:</strong> <span class='" + 
            (result.generativeMatch ? "match" : "mismatch") + "'>" +
            (result.generativeMatch ? "✓ Match" : "✗ Mismatch") + "</span></p>\n");
        html.append("<p><strong>Syntax Tree:</strong> <span class='" + 
            (result.syntaxMatch ? "match" : "mismatch") + "'>" +
            (result.syntaxMatch ? "✓ Match" : "✗ Mismatch") + "</span></p>\n");
        
        // Generative tree comparison
        html.append("<h4>Generative Tree Comparison</h4>\n");
        html.append("<div class='comparison'>\n");
        html.append("<div class='tree-panel'><h3>Expected</h3><div class='tree-content'>" + 
            escapeHtml(result.expectedGenerative) + "</div></div>\n");
        html.append("<div class='tree-panel'><h3>Actual</h3><div class='tree-content'>" + 
            escapeHtml(result.actualGenerative) + "</div></div>\n");
        html.append("</div>\n");
        
        // Syntax tree comparison
        html.append("<h4>Syntax Tree Comparison</h4>\n");
        html.append("<div class='comparison'>\n");
        html.append("<div class='tree-panel'><h3>Expected</h3><div class='tree-content'>" + 
            escapeHtml(result.expectedSyntax) + "</div></div>\n");
        html.append("<div class='tree-panel'><h3>Actual</h3><div class='tree-content'>" + 
            escapeHtml(result.actualSyntax) + "</div></div>\n");
        html.append("</div>\n");
      }
      
      html.append("</div>\n");
    }
    
    html.append("</body></html>\n");
    
    Path htmlFile = outputDir.resolve("comparison.html");
    Files.writeString(htmlFile, html.toString());
    return htmlFile;
  }
  
  private static String escapeHtml(String text) {
    if (text == null) return "";
    return text.replace("&", "&amp;")
               .replace("<", "&lt;")
               .replace(">", "&gt;")
               .replace("\"", "&quot;")
               .replace("'", "&#39;");
  }
  
  private static class TestCase {
    final String caseId;
    final Path caseDir;
    final Path programFile;
    final Path expectedGenFile;
    final Path expectedSynFile;
    
    TestCase(String caseId, Path caseDir, Path programFile, 
             Path expectedGenFile, Path expectedSynFile) {
      this.caseId = caseId;
      this.caseDir = caseDir;
      this.programFile = programFile;
      this.expectedGenFile = expectedGenFile;
      this.expectedSynFile = expectedSynFile;
    }
  }
  
  private static class ComparisonResult {
    final String caseId;
    boolean success = false;
    String errorMessage;
    String actualGenerative;
    String expectedGenerative;
    String actualSyntax;
    String expectedSyntax;
    boolean generativeMatch = false;
    boolean syntaxMatch = false;
    
    ComparisonResult(String caseId) {
      this.caseId = caseId;
    }
  }
}

