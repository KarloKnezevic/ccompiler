package hr.fer.ppj.examples;

import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Lexer.SymbolTableEntry;
import hr.fer.ppj.lexer.io.Token;
import hr.fer.ppj.parser.Parser;
import hr.fer.ppj.parser.config.ParserConfig;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Generates HTML reports for test programs in examples/valid and examples/invalid directories.
 * 
 * <p>For each .c program, runs lexer and parser, collects outputs, and generates
 * comprehensive HTML reports showing:
 * <ul>
 *   <li>Source code</li>
 *   <li>Lexical tokens output</li>
 *   <li>Generative tree</li>
 *   <li>Syntax tree</li>
 *   <li>Error messages (if any)</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ExamplesReportGenerator {
  
  private static final String COMPILER_BIN_DIR = "compiler-bin";
  private static final Pattern LEXER_ERROR_PATTERN = Pattern.compile(
      "(?i).*error.*line\\s+(\\d+).*", Pattern.DOTALL);
  private static final Pattern PARSER_ERROR_PATTERN = Pattern.compile(
      "(?i).*sintaksna\\s+greška\\s+na\\s+retku\\s+(\\d+).*", Pattern.DOTALL);
  
  /**
   * Result of analyzing a single program.
   */
  private static final class ProgramResult {
    final String programName;
    final String sourceCode;
    final String lexerOutput;
    final String lexerErrors;
    final Integer lexerErrorLine;
    final String generativeTree;
    final String syntaxTree;
    final String parserErrors;
    final Integer parserErrorLine;
    final boolean lexerSuccess;
    final boolean parserSuccess;
    
    ProgramResult(String programName, String sourceCode, String lexerOutput, 
                  String lexerErrors, Integer lexerErrorLine, String generativeTree,
                  String syntaxTree, String parserErrors, Integer parserErrorLine,
                  boolean lexerSuccess, boolean parserSuccess) {
      this.programName = programName;
      this.sourceCode = sourceCode;
      this.lexerOutput = lexerOutput;
      this.lexerErrors = lexerErrors;
      this.lexerErrorLine = lexerErrorLine;
      this.generativeTree = generativeTree;
      this.syntaxTree = syntaxTree;
      this.parserErrors = parserErrors;
      this.parserErrorLine = parserErrorLine;
      this.lexerSuccess = lexerSuccess;
      this.parserSuccess = parserSuccess;
    }
  }
  
  public static void main(String[] args) throws Exception {
    Path root = Paths.get("examples");
    Path validDir = root.resolve("valid");
    Path invalidDir = root.resolve("invalid");
    
    System.out.println("Generating report for valid programs...");
    generateReport(validDir, root.resolve("report_valid.html"), true);
    
    System.out.println("Generating report for invalid programs...");
    generateReport(invalidDir, root.resolve("report_invalid.html"), false);
    
    System.out.println("Reports generated successfully!");
    System.out.println("  - examples/report_valid.html");
    System.out.println("  - examples/report_invalid.html");
  }
  
  /**
   * Generates HTML report for all .c programs in the given directory.
   */
  private static void generateReport(Path programsDir, Path reportFile, boolean valid) 
      throws Exception {
    if (!Files.exists(programsDir)) {
      System.err.println("Directory does not exist: " + programsDir);
      return;
    }
    
    // Find all .c files
    List<Path> programFiles;
    try (Stream<Path> stream = Files.list(programsDir)) {
      programFiles = stream
          .filter(Files::isRegularFile)
          .filter(p -> p.getFileName().toString().endsWith(".c"))
          .sorted()
          .toList();
    }
    
    if (programFiles.isEmpty()) {
      System.err.println("No .c files found in " + programsDir);
      return;
    }
    
    System.out.println("Found " + programFiles.size() + " programs in " + programsDir);
    
    // Analyze each program
    List<ProgramResult> results = new ArrayList<>();
    int processed = 0;
    for (Path programFile : programFiles) {
      processed++;
      System.out.print("Processing " + programFile.getFileName() + 
          " (" + processed + "/" + programFiles.size() + ")... ");
      try {
        ProgramResult result = analyzeProgram(programFile, valid);
        results.add(result);
        System.out.println("OK");
      } catch (Exception e) {
        System.out.println("ERROR: " + e.getMessage());
        // Create error result
        String sourceCode;
        try {
          sourceCode = Files.readString(programFile);
        } catch (IOException ex) {
          sourceCode = "Error reading file: " + ex.getMessage();
        }
        results.add(new ProgramResult(
            programFile.getFileName().toString(),
            sourceCode,
            "",
            "Analysis failed: " + e.getMessage(),
            null,
            "",
            "",
            "Analysis failed: " + e.getMessage(),
            null,
            false,
            false
        ));
      }
    }
    
    // Generate HTML
    generateHTML(results, reportFile, valid);
  }
  
  /**
   * Analyzes a single program by running lexer and parser.
   */
  private static ProgramResult analyzeProgram(Path programFile, boolean valid) 
      throws Exception {
    String programName = programFile.getFileName().toString();
    String sourceCode = Files.readString(programFile);
    
    // Create temporary directory for this program's output
    Path tempDir = Files.createTempDirectory("ppj_report_" + programName);
    Path binDir = tempDir.resolve(COMPILER_BIN_DIR);
    Files.createDirectories(binDir);
    
    try {
      // Run lexer
      String lexerOutput = "";
      String lexerErrors = "";
      Integer lexerErrorLine = null;
      boolean lexerSuccess = false;
      
      try {
        lexerOutput = runLexer(programFile);
        lexerSuccess = true;
      } catch (Exception e) {
        lexerErrors = e.getMessage();
        if (e.getMessage() != null) {
          Matcher m = LEXER_ERROR_PATTERN.matcher(e.getMessage());
          if (m.matches()) {
            try {
              lexerErrorLine = Integer.parseInt(m.group(1));
            } catch (NumberFormatException ex) {
              // Ignore
            }
          }
        }
      }
      
      // Run parser if lexer succeeded
      String generativeTree = "";
      String syntaxTree = "";
      String parserErrors = "";
      Integer parserErrorLine = null;
      boolean parserSuccess = false;
      
      if (lexerSuccess) {
        try {
          // Generate leksicke_jedinke.txt
          Path leksickePath = binDir.resolve("leksicke_jedinke.txt");
          try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(leksickePath))) {
            writer.print(lexerOutput);
          }
          
          // Run parser
          ParserConfig.Config parserConfig = ParserConfig.Config.createDefault(
              leksickePath,
              binDir
          );
          
          Parser parser = new Parser();
          parser.parse(parserConfig);
          
          // Read generated trees
          Path generativnoPath = binDir.resolve("generativno_stablo.txt");
          Path sintaksnoPath = binDir.resolve("sintaksno_stablo.txt");
          
          if (Files.exists(generativnoPath)) {
            generativeTree = Files.readString(generativnoPath);
          }
          if (Files.exists(sintaksnoPath)) {
            syntaxTree = Files.readString(sintaksnoPath);
          }
          
          parserSuccess = true;
        } catch (Exception e) {
          parserErrors = e.getMessage();
          if (e.getMessage() != null) {
            Matcher m = PARSER_ERROR_PATTERN.matcher(e.getMessage());
            if (m.matches()) {
              try {
                parserErrorLine = Integer.parseInt(m.group(1));
              } catch (NumberFormatException ex) {
                // Ignore
              }
            }
          }
          
          // Try to read partial results
          Path generativnoPath = binDir.resolve("generativno_stablo.txt");
          Path sintaksnoPath = binDir.resolve("sintaksno_stablo.txt");
          
          if (Files.exists(generativnoPath)) {
            generativeTree = Files.readString(generativnoPath);
          }
          if (Files.exists(sintaksnoPath)) {
            syntaxTree = Files.readString(sintaksnoPath);
          }
        }
      }
      
      return new ProgramResult(
          programName,
          sourceCode,
          lexerOutput,
          lexerErrors,
          lexerErrorLine,
          generativeTree,
          syntaxTree,
          parserErrors,
          parserErrorLine,
          lexerSuccess,
          parserSuccess
      );
    } finally {
      // Cleanup temp directory
      deleteDirectory(tempDir);
    }
  }
  
  /**
   * Runs lexer on a program file and returns the output.
   * Captures stderr to detect errors.
   */
  private static String runLexer(Path programFile) throws Exception {
    // Capture stderr
    java.io.ByteArrayOutputStream errStream = new java.io.ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    System.setErr(new PrintStream(errStream));
    
    try {
      // Load lexer definition
      Path specPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
      LexerGenerator generator = new LexerGenerator();
      LexerGeneratorResult result;
      try (FileReader reader = new FileReader(specPath.toFile())) {
        result = generator.generate(reader);
      }
      
      // Tokenize
      Lexer lexer = new Lexer(result);
      List<Token> tokens;
      try (FileReader reader = new FileReader(programFile.toFile())) {
        tokens = lexer.tokenize(reader);
      }
      
      // Get stderr output
      String stderrOutput = errStream.toString();
      System.setErr(originalErr);
      
      // If there are errors in stderr, throw exception
      if (!stderrOutput.isEmpty()) {
        throw new Exception(stderrOutput);
      }
      
      // Format output
      StringBuilder sb = new StringBuilder();
      sb.append("tablica znakova:\n");
      sb.append("indeks   uniformni znak   izvorni tekst\n");
      List<SymbolTableEntry> symbolTable = lexer.getSymbolTable();
      for (int i = 0; i < symbolTable.size(); i++) {
        SymbolTableEntry entry = symbolTable.get(i);
        sb.append(String.format("     %d   %-18s %s%n", i, entry.token(), entry.text()));
      }
      
      sb.append("\nniz uniformnih znakova:\n");
      sb.append("uniformni znak    redak    indeks u tablicu znakova\n");
      for (Token token : tokens) {
        sb.append(String.format("%-18s %5d       %d%n",
            token.type(),
            token.line(),
            token.symbolTableIndex()));
      }
      
      return sb.toString();
    } finally {
      System.setErr(originalErr);
    }
  }
  
  /**
   * Generates HTML report from program results.
   */
  private static void generateHTML(List<ProgramResult> results, Path reportFile, 
                                   boolean valid) throws IOException {
    try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(reportFile))) {
      writer.println("<!DOCTYPE html>");
      writer.println("<html lang=\"en\">");
      writer.println("<head>");
      writer.println("  <meta charset=\"UTF-8\">");
      writer.println("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
      writer.println("  <title>PPJ Compiler - " + (valid ? "Valid" : "Invalid") + " Programs Report</title>");
      writer.println("  <style>");
      writer.println(getCSS());
      writer.println("  </style>");
      writer.println("</head>");
      writer.println("<body>");
      writer.println("  <div class=\"container\">");
      writer.println("    <header>");
      writer.println("      <h1>PPJ Compiler - " + (valid ? "Valid" : "Invalid") + " Programs Report</h1>");
      writer.println("      <p class=\"subtitle\">Total programs: " + results.size() + "</p>");
      writer.println("    </header>");
      
      // Navigation index
      writer.println("    <nav class=\"index\">");
      writer.println("      <h2>Program Index</h2>");
      writer.println("      <ul>");
      for (ProgramResult result : results) {
        String anchor = result.programName.replace(".c", "");
        writer.println("        <li><a href=\"#" + anchor + "\">" + result.programName + "</a></li>");
      }
      writer.println("      </ul>");
      writer.println("    </nav>");
      
      // Program cards
      for (ProgramResult result : results) {
        String anchor = result.programName.replace(".c", "");
        writer.println("    <section id=\"" + anchor + "\" class=\"program-card\">");
        writer.println("      <h2>" + result.programName + "</h2>");
        
        // Status badges
        writer.println("      <div class=\"status-row\">");
        if (result.lexerSuccess) {
          writer.println("        <span class=\"badge badge-ok\">Lexer: OK</span>");
        } else {
          String errorMsg = result.lexerErrorLine != null 
              ? "Lexer error at line " + result.lexerErrorLine
              : "Lexer error";
          writer.println("        <span class=\"badge badge-error\">" + errorMsg + "</span>");
        }
        
        if (result.parserSuccess) {
          writer.println("        <span class=\"badge badge-ok\">Parser: OK</span>");
        } else if (result.lexerSuccess) {
          String errorMsg = result.parserErrorLine != null
              ? "Parser error at line " + result.parserErrorLine
              : "Parser error";
          writer.println("        <span class=\"badge badge-error\">" + errorMsg + "</span>");
        } else {
          writer.println("        <span class=\"badge badge-skip\">Parser: Skipped (lexer failed)</span>");
        }
        writer.println("      </div>");
        
        // Source code
        writer.println("      <details open>");
        writer.println("        <summary>Source Code</summary>");
        writer.println("        <pre><code>" + escapeHtml(result.sourceCode) + "</code></pre>");
        writer.println("      </details>");
        
        // Lexer output
        if (result.lexerSuccess && !result.lexerOutput.isEmpty()) {
          writer.println("      <details>");
          writer.println("        <summary>Lexical Tokens</summary>");
          writer.println("        <pre><code>" + escapeHtml(result.lexerOutput) + "</code></pre>");
          writer.println("      </details>");
        }
        
        // Generative tree
        if (!result.generativeTree.isEmpty()) {
          writer.println("      <details>");
          writer.println("        <summary>Generative Tree</summary>");
          writer.println("        <pre><code>" + escapeHtml(result.generativeTree) + "</code></pre>");
          writer.println("      </details>");
        }
        
        // Syntax tree
        if (!result.syntaxTree.isEmpty()) {
          writer.println("      <details>");
          writer.println("        <summary>Syntax Tree</summary>");
          writer.println("        <pre><code>" + escapeHtml(result.syntaxTree) + "</code></pre>");
          writer.println("      </details>");
        }
        
        // Errors
        if (!result.lexerErrors.isEmpty() || !result.parserErrors.isEmpty()) {
          writer.println("      <details>");
          writer.println("        <summary>Analysis Errors</summary>");
          writer.println("        <pre><code>");
          if (!result.lexerErrors.isEmpty()) {
            writer.println("LEXER ERRORS:");
            writer.println(escapeHtml(result.lexerErrors));
          }
          if (!result.parserErrors.isEmpty()) {
            if (!result.lexerErrors.isEmpty()) {
              writer.println("\n---\n");
            }
            writer.println("PARSER ERRORS:");
            writer.println(escapeHtml(result.parserErrors));
          }
          writer.println("        </code></pre>");
          writer.println("      </details>");
        }
        
        writer.println("    </section>");
      }
      
      writer.println("  </div>");
      writer.println("</body>");
      writer.println("</html>");
    }
  }
  
  /**
   * Returns CSS styles for the HTML report.
   */
  private static String getCSS() {
    return """
        * {
          margin: 0;
          padding: 0;
          box-sizing: border-box;
        }
        
        body {
          font-family: system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', 
                       Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
          background-color: #f5f5f5;
          color: #333;
          line-height: 1.6;
        }
        
        .container {
          max-width: 1200px;
          margin: 0 auto;
          padding: 20px;
        }
        
        header {
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          color: white;
          padding: 30px;
          border-radius: 10px;
          margin-bottom: 30px;
          box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
        }
        
        header h1 {
          font-size: 2.5em;
          margin-bottom: 10px;
        }
        
        .subtitle {
          font-size: 1.1em;
          opacity: 0.9;
        }
        
        nav.index {
          background: white;
          padding: 20px;
          border-radius: 8px;
          margin-bottom: 30px;
          box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
        }
        
        nav.index h2 {
          margin-bottom: 15px;
          color: #667eea;
        }
        
        nav.index ul {
          list-style: none;
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
          gap: 10px;
        }
        
        nav.index a {
          color: #667eea;
          text-decoration: none;
          padding: 8px 12px;
          border-radius: 4px;
          display: block;
          transition: background-color 0.2s;
        }
        
        nav.index a:hover {
          background-color: #f0f0f0;
        }
        
        .program-card {
          background: white;
          border-radius: 8px;
          padding: 25px;
          margin-bottom: 30px;
          box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
          transition: box-shadow 0.3s;
        }
        
        .program-card:hover {
          box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
        }
        
        .program-card h2 {
          color: #667eea;
          margin-bottom: 15px;
          font-size: 1.8em;
        }
        
        .status-row {
          display: flex;
          gap: 10px;
          margin-bottom: 20px;
          flex-wrap: wrap;
        }
        
        .badge {
          display: inline-block;
          padding: 6px 12px;
          border-radius: 20px;
          font-size: 0.85em;
          font-weight: 600;
          text-transform: uppercase;
          letter-spacing: 0.5px;
        }
        
        .badge-ok {
          background-color: #10b981;
          color: white;
        }
        
        .badge-error {
          background-color: #ef4444;
          color: white;
        }
        
        .badge-skip {
          background-color: #6b7280;
          color: white;
        }
        
        details {
          margin-bottom: 15px;
          border: 1px solid #e5e7eb;
          border-radius: 6px;
          overflow: hidden;
        }
        
        summary {
          padding: 12px 16px;
          background-color: #f9fafb;
          cursor: pointer;
          font-weight: 600;
          user-select: none;
          transition: background-color 0.2s;
        }
        
        summary:hover {
          background-color: #f3f4f6;
        }
        
        details[open] summary {
          border-bottom: 1px solid #e5e7eb;
        }
        
        details pre {
          margin: 0;
          padding: 16px;
          background-color: #1e293b;
          color: #e2e8f0;
          overflow-x: auto;
          font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', 
                       'source-code-pro', monospace;
          font-size: 0.9em;
          line-height: 1.5;
        }
        
        details code {
          font-family: inherit;
        }
        
        @media (max-width: 768px) {
          .container {
            padding: 10px;
          }
          
          header h1 {
            font-size: 1.8em;
          }
          
          nav.index ul {
            grid-template-columns: 1fr;
          }
        }
        """;
  }
  
  /**
   * Escapes HTML special characters.
   */
  private static String escapeHtml(String text) {
    if (text == null) {
      return "";
    }
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }
  
  /**
   * Recursively deletes a directory.
   */
  private static void deleteDirectory(Path dir) throws IOException {
    if (Files.exists(dir)) {
      try (var stream = Files.walk(dir)) {
        stream.sorted((a, b) -> -a.compareTo(b))
            .forEach(path -> {
              try {
                Files.delete(path);
              } catch (IOException e) {
                // Ignore
              }
            });
      }
    }
  }
}

