package hr.fer.ppj.examples;

import hr.fer.ppj.cli.FriscRunner;
// import hr.fer.ppj.codegen.CodeGenerator; // Codegen removed - IR focus only
// import hr.fer.ppj.codegen.util.FloatCodegenHelper; // Codegen removed - IR focus only
import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Lexer.SymbolTableEntry;
import hr.fer.ppj.lexer.io.Token;
import hr.fer.ppj.parser.Parser;
import hr.fer.ppj.parser.config.ParserConfig;
import hr.fer.ppj.parser.io.TokenReader;
import hr.fer.ppj.parser.tree.ParseTree;
import hr.fer.ppj.semantics.analysis.SemanticAnalyzer;
import hr.fer.ppj.semantics.io.SemanticReport;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
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
 * <p>For each .c program, runs lexer, parser, semantic analyzer, and code generator, 
 * collects outputs, and generates comprehensive HTML reports showing:
 * <ul>
 *   <li>Source code</li>
 *   <li>Lexical tokens output</li>
 *   <li>Generative tree</li>
 *   <li>Syntax tree</li>
 *   <li>Semantic analysis results</li>
 *   <li>Generated FRISC assembly code</li>
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
  private static final Pattern SEMANTIC_ERROR_PATTERN = Pattern.compile(
      "(?i).*::=\\s+.*\\(\\s*(\\d+)\\s*,.*", Pattern.DOTALL);
  
  private static final FriscRunner FRISC_RUNNER = new FriscRunner();
  
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
    final String semanticOutput;
    final String semanticErrors;
    final Integer semanticErrorLine;
    final String symbolTable;
    final String semanticTree;
    final String friscCode;
    final String friscErrors;
    final String expectedOutput;
    final String actualOutput;
    final String actualFloatValue;  // Float value converted from Q16.16
    final String runtimeError;
    final String simulatorOutput;
    final boolean outputMatches;
    final boolean lexerSuccess;
    final boolean parserSuccess;
    final boolean semanticSuccess;
    final boolean friscSuccess;
    
    ProgramResult(String programName, String sourceCode, String lexerOutput, 
                  String lexerErrors, Integer lexerErrorLine, String generativeTree,
                  String syntaxTree, String parserErrors, Integer parserErrorLine,
                  String semanticOutput, String semanticErrors, Integer semanticErrorLine,
                  String symbolTable, String semanticTree, String friscCode, String friscErrors,
                  String expectedOutput, String actualOutput, String actualFloatValue, String runtimeError, String simulatorOutput,
                  boolean outputMatches,
                  boolean lexerSuccess, boolean parserSuccess, boolean semanticSuccess, boolean friscSuccess) {
      this.programName = programName;
      this.sourceCode = sourceCode;
      this.lexerOutput = lexerOutput;
      this.lexerErrors = lexerErrors;
      this.lexerErrorLine = lexerErrorLine;
      this.generativeTree = generativeTree;
      this.syntaxTree = syntaxTree;
      this.parserErrors = parserErrors;
      this.parserErrorLine = parserErrorLine;
      this.semanticOutput = semanticOutput;
      this.semanticErrors = semanticErrors;
      this.semanticErrorLine = semanticErrorLine;
      this.symbolTable = symbolTable;
      this.semanticTree = semanticTree;
      this.friscCode = friscCode;
      this.friscErrors = friscErrors;
      this.expectedOutput = expectedOutput;
      this.actualOutput = actualOutput;
      this.actualFloatValue = actualFloatValue;
      this.runtimeError = runtimeError;
      this.outputMatches = outputMatches;
      this.simulatorOutput = simulatorOutput;
      this.lexerSuccess = lexerSuccess;
      this.parserSuccess = parserSuccess;
      this.semanticSuccess = semanticSuccess;
      this.friscSuccess = friscSuccess;
    }
  }
  
  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("Usage: ExamplesReportGenerator <examples-folder-path>");
      System.err.println("Example: ExamplesReportGenerator examples/floats");
      System.exit(1);
    }
    
    Path examplesFolder = Paths.get(args[0]);
    if (!Files.exists(examplesFolder) || !Files.isDirectory(examplesFolder)) {
      System.err.println("Error: Directory does not exist: " + examplesFolder);
      System.exit(1);
    }
    
    // Generate report name: report_<folder_name>.html
    String folderName = examplesFolder.getFileName().toString();
    Path reportFile = examplesFolder.resolve("report_" + folderName + ".html");
    
    System.out.println("Generating report for: " + examplesFolder);
    System.out.println("Output file: " + reportFile);
    
    // Determine if this is a "valid" folder (for error handling expectations)
    boolean valid = !folderName.equals("invalid");
    
    generateReport(examplesFolder, reportFile, valid);
    
    System.out.println("Report generated successfully!");
    System.out.println("  - " + reportFile);
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
            "",
            "Analysis failed: " + e.getMessage(),
            null,
            "",  // symbolTable
            "",  // semanticTree
            "",  // friscCode
            "Analysis failed: " + e.getMessage(),  // friscErrors
            "",  // expectedOutput
            "",  // actualOutput
            "",  // actualFloatValue
            "Not executed",  // runtimeError
            "",  // simulatorOutput
            false,  // outputMatches
            false,
            false,
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
      List<Token> lexerTokens = null;
      
      try {
        LexicalAnalysisResult lexResult = runLexerWithTokens(programFile);
        lexerOutput = lexResult.output();
        lexerTokens = lexResult.tokens();
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
      ParseTree parseTree = null;
      
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
          
          // Convert lexer tokens to parser tokens and get parse tree for semantic analysis
          if (lexerTokens != null) {
            List<TokenReader.Token> parserTokens = new ArrayList<>(lexerTokens.size());
            for (Token token : lexerTokens) {
              parserTokens.add(new TokenReader.Token(token.type(), token.line(), token.value()));
            }
            parseTree = parser.parseTokens(parserTokens);
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
      
      // Run semantic analysis if parser succeeded
      String semanticOutput = "";
      String semanticErrors = "";
      Integer semanticErrorLine = null;
      String symbolTable = "";
      String semanticTree = "";
      boolean semanticSuccess = false;
      
      if (parserSuccess && parseTree != null) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(outputStream, true, StandardCharsets.UTF_8);
        
        // Create semantic report for temp directory
        SemanticReport semanticReport = SemanticReport.forDirectory(tempDir.toString());
        
        try {
          SemanticAnalyzer analyzer = new SemanticAnalyzer();
          analyzer.analyze(parseTree, printStream, semanticReport);
          printStream.flush();
          semanticOutput = outputStream.toString(StandardCharsets.UTF_8);
          semanticSuccess = true;
          
          // Read generated debug files if semantic analysis succeeded
          Path symbolTableFile = tempDir.resolve("tablica_simbola.txt");
          Path semanticTreeFile = tempDir.resolve("semanticko_stablo.txt");
          
          if (Files.exists(symbolTableFile)) {
            symbolTable = Files.readString(symbolTableFile);
          }
          
          if (Files.exists(semanticTreeFile)) {
            semanticTree = Files.readString(semanticTreeFile);
          }
          
        } catch (hr.fer.ppj.semantics.errors.SemanticException e) {
          // Semantic error occurred - output was already written to printStream
          printStream.flush();
          semanticOutput = outputStream.toString(StandardCharsets.UTF_8);
          semanticErrors = "semantic error";
          
          // Try to extract line number from semantic output
          if (!semanticOutput.isEmpty()) {
            Matcher m = SEMANTIC_ERROR_PATTERN.matcher(semanticOutput);
            if (m.find()) {
              try {
                semanticErrorLine = Integer.parseInt(m.group(1));
              } catch (NumberFormatException ex) {
                // Ignore
              }
            }
          }
        } catch (Exception e) {
          printStream.flush();
          semanticOutput = outputStream.toString(StandardCharsets.UTF_8);
          semanticErrors = "Semantic analysis failed: " + e.getMessage();
        } finally {
          printStream.close();
        }
      }
      
      // Run code generation if semantic analysis succeeded
      String friscCode = "";
      String friscErrors = "";
      boolean friscSuccess = false;
      
      if (semanticSuccess && parseTree != null) {
        try {
          SemanticAnalyzer analyzer = new SemanticAnalyzer();
          SemanticAnalyzer.SemanticAnalysisResult semanticResults = 
              analyzer.analyzeWithResults(parseTree, new PrintStream(new ByteArrayOutputStream()), null);
          
          // Codegen removed - IR focus only
          // CodeGenerator codeGen = new CodeGenerator();
          // Path friscOutputPath = tempDir.resolve("a.frisc");
          // codeGen.generate(semanticResults.globalScope(), semanticResults.parseTree(), friscOutputPath);
          // 
          // if (Files.exists(friscOutputPath)) {
          //   friscCode = Files.readString(friscOutputPath);
          //   friscSuccess = true;
          // }
          
        } catch (Exception e) {
          friscErrors = "Code generation failed: " + e.getMessage();
        }
      }
      
      String expectedOutput = readExpectedOutput(programFile);
      String actualOutput = "";
      String actualFloatValue = "";
      String runtimeError = "";
      String simulatorOutput = "";
      boolean outputMatches = false;
      
      if (friscSuccess && !friscCode.isEmpty()) {
        Path friscOutputPath = tempDir.resolve("a.frisc");
        try {
          FriscRunner.Result execResult = FRISC_RUNNER.run(friscOutputPath);
          simulatorOutput = execResult.output() == null ? "" : execResult.output();
          if (execResult.success()) {
            // FRISC simulator outputs decimal R6 value to stdout (as integer)
            String r6IntValue = execResult.r6Value().trim();
            actualOutput = r6IntValue;
            
            // Always convert Q16.16 to float for display (using FloatCodegenHelper)
            try {
              int q16_16 = Integer.parseInt(r6IntValue);
              // TODO: Re-enable when codegen is added back
              // float floatValue = FloatCodegenHelper.q16_16ToFloat(q16_16);
              float floatValue = (float) q16_16 / 65536.0f; // Simple Q16.16 conversion
              // Format float value nicely
              if (floatValue == (int) floatValue) {
                actualFloatValue = String.format("%.1f", floatValue);
              } else {
                actualFloatValue = String.valueOf(floatValue);
              }
            } catch (NumberFormatException e) {
              actualFloatValue = "N/A";
            }
            
            if (!expectedOutput.isEmpty()) {
              // First, try comparing as integer
              boolean intMatch = expectedOutput.equals(r6IntValue);
              
              // If integer match fails, check if expected output is a float literal
              // Float literals contain '.' or 'e'/'E' (exponent notation)
              // TODO: Re-enable when codegen is added back
              // boolean expectedIsFloat = FloatCodegenHelper.isFloatLiteral(expectedOutput);
              boolean expectedIsFloat = expectedOutput.contains(".") || expectedOutput.toLowerCase().contains("e");
              
              if (!intMatch && expectedIsFloat) {
                // Try comparing as float (Q16.16 conversion)
                try {
                  float expectedFloat = Float.parseFloat(expectedOutput);
                  // TODO: Re-enable when codegen is added back
                  // float actualFloat = FloatCodegenHelper.q16_16ToFloat(Integer.parseInt(r6IntValue));
                  float actualFloat = (float) Integer.parseInt(r6IntValue) / 65536.0f; // Simple Q16.16 conversion
                  
                  // Compare floats with small epsilon for floating-point precision
                  float epsilon = 0.0001f;
                  outputMatches = Math.abs(expectedFloat - actualFloat) < epsilon;
                } catch (NumberFormatException e) {
                  // Expected output is not a valid float, fall back to integer comparison
                  outputMatches = false;
                }
              } else {
                // Integer comparison (or expected is not a float)
                outputMatches = intMatch;
              }
            }
          } else {
            runtimeError = execResult.errorMessage();
            if (execResult.output() != null && !execResult.output().isBlank()) {
              runtimeError += System.lineSeparator() + execResult.output().trim();
            }
          }
        } catch (IOException | InterruptedException e) {
          runtimeError = "Simulator execution failed: " + e.getMessage();
        }
      } else if (!friscSuccess) {
        runtimeError = friscErrors.isEmpty() ? "Code generation failed." : friscErrors;
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
          semanticOutput,
          semanticErrors,
          semanticErrorLine,
          symbolTable,
          semanticTree,
          friscCode,
          friscErrors,
          expectedOutput,
          actualOutput,
          actualFloatValue,
          runtimeError,
          simulatorOutput,
          outputMatches,
          lexerSuccess,
          parserSuccess,
          semanticSuccess,
          friscSuccess
      );
    } finally {
      // Cleanup temp directory
      deleteDirectory(tempDir);
    }
  }
  
  private static String readExpectedOutput(Path programFile) {
    String fileName = programFile.getFileName().toString();
    if (!fileName.endsWith(".c")) {
      return "";
    }
    
    String baseName = fileName.substring(0, fileName.length() - 2);
    Path parent = programFile.getParent();
    
    Path[] candidates = new Path[] {
        parent.resolve(baseName.replace("program", "output") + ".txt"),
        parent.resolve(baseName + ".out"),
        parent.resolve(baseName + ".expected")
    };
    
    for (Path candidate : candidates) {
      if (Files.exists(candidate)) {
        try {
          return Files.readString(candidate).trim();
        } catch (IOException e) {
          return "Error reading expected output: " + e.getMessage();
        }
      }
    }
    
    return "";
  }
  
  /**
   * Result of lexical analysis.
   */
  private static record LexicalAnalysisResult(String output, List<Token> tokens) {}
  
  /**
   * Runs lexer on a program file and returns the output and tokens.
   * Captures stderr to detect errors.
   */
  private static LexicalAnalysisResult runLexerWithTokens(Path programFile) throws Exception {
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
      
      return new LexicalAnalysisResult(sb.toString(), tokens);
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
        
        // Prominent pass/fail badge for execution results
        // Only show badge if Expected is present (don't show FAIL for tests without Expected)
        if (result.friscSuccess && !result.expectedOutput.isEmpty()) {
          String passFailClass = result.outputMatches ? "badge-pass" : "badge-fail";
          String passFailIcon = result.outputMatches ? "✅" : "❌";
          String passFailText = result.outputMatches ? "PASS" : "FAIL";
          writer.println("      <div class=\"result-badge\">");
          writer.println("        <span class=\"badge " + passFailClass + "\">" + passFailIcon + " " + passFailText + "</span>");
          writer.println("      </div>");
        }
        
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
        
        if (result.semanticSuccess) {
          writer.println("        <span class=\"badge badge-ok\">Semantic: OK</span>");
        } else if (result.parserSuccess) {
          String errorMsg = result.semanticErrorLine != null
              ? "Semantic error at line " + result.semanticErrorLine
              : "Semantic error";
          writer.println("        <span class=\"badge badge-error\">" + errorMsg + "</span>");
        } else {
          writer.println("        <span class=\"badge badge-skip\">Semantic: Skipped (parser failed)</span>");
        }
        
        // Code generation badge
        if (result.friscSuccess) {
          writer.println("        <span class=\"badge badge-ok\">CodeGen: OK</span>");
        } else if (result.semanticSuccess) {
          writer.println("        <span class=\"badge badge-error\">CodeGen: Failed</span>");
        } else {
          writer.println("        <span class=\"badge badge-skip\">CodeGen: Skipped (semantic failed)</span>");
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
        
        // Semantic analysis output
        if (result.parserSuccess) {
          if (result.semanticSuccess && result.semanticOutput.isEmpty()) {
            writer.println("      <details>");
            writer.println("        <summary>Semantic Analysis</summary>");
            writer.println("        <pre><code>No semantic errors found.</code></pre>");
            writer.println("      </details>");
          } else if (!result.semanticOutput.isEmpty()) {
            writer.println("      <details>");
            writer.println("        <summary>Semantic Analysis</summary>");
            writer.println("        <pre><code>" + escapeHtml(result.semanticOutput) + "</code></pre>");
            writer.println("      </details>");
          }
        }
        
        // Symbol table (only if semantic analysis succeeded)
        if (result.semanticSuccess && !result.symbolTable.isEmpty()) {
          writer.println("      <details>");
          writer.println("        <summary>Symbol Table</summary>");
          writer.println("        <pre><code>" + escapeHtml(result.symbolTable) + "</code></pre>");
          writer.println("      </details>");
        }
        
        // Semantic tree (only if semantic analysis succeeded)
        if (result.semanticSuccess && !result.semanticTree.isEmpty()) {
          writer.println("      <details>");
          writer.println("        <summary>Semantic Tree</summary>");
          writer.println("        <pre><code>" + escapeHtml(result.semanticTree) + "</code></pre>");
          writer.println("      </details>");
        }
        
        // FRISC code (only if code generation succeeded)
        if (result.friscSuccess && !result.friscCode.isEmpty()) {
          writer.println("      <details>");
          writer.println("        <summary>Generated FRISC Assembly Code</summary>");
          writer.println("        <pre><code>" + escapeHtml(result.friscCode) + "</code></pre>");
          writer.println("      </details>");
        }
        
        // FRISC simulator results and expected output
        if (result.friscSuccess || !result.expectedOutput.isEmpty() || !result.runtimeError.isEmpty()
            || (result.simulatorOutput != null && !result.simulatorOutput.isEmpty())) {
          writer.println("      <details>");
          writer.println("        <summary>FRISC Execution</summary>");
          writer.println("        <div class=\"simulator-results\">");
          
          if (result.friscSuccess && !result.actualOutput.isEmpty()) {
            // Show actual output with integer and float values
            writer.println("          <div class=\"output-comparison\">");
            writer.println("            <table class=\"output-table\">");
            writer.println("              <tr>");
            writer.println("                <th>Expected</th>");
            writer.println("                <th>Actual (Q16.16)</th>");
            writer.println("                <th>Q16.16 Float</th>");
            writer.println("                <th>Status</th>");
            writer.println("              </tr>");
            writer.println("              <tr>");
            writer.println("                <td><code>" + escapeHtml(result.expectedOutput.isEmpty() ? "—" : result.expectedOutput) + "</code></td>");
            writer.println("                <td><code>" + escapeHtml(result.actualOutput) + "</code></td>");
            writer.println("                <td><code>" + escapeHtml(result.actualFloatValue) + "</code></td>");
            // Status: PASS (green), FAIL (red), or UNKNOWN (grey) if Expected is empty
            String statusClass;
            String statusText;
            if (result.expectedOutput.isEmpty()) {
              statusClass = "status-unknown";
              statusText = "❔ UNKNOWN";
            } else {
              statusClass = result.outputMatches ? "status-pass" : "status-fail";
              statusText = result.outputMatches ? "✅ PASS" : "❌ FAIL";
            }
            writer.println("                <td class=\"" + statusClass + "\">" + statusText + "</td>");
            writer.println("              </tr>");
            writer.println("            </table>");
            writer.println("            <p><small>Note: Float values are compared using Q16.16 fixed-point representation. " +
                         "Q16.16 Float column shows the converted float value for comparison.</small></p>");
            writer.println("          </div>");
          } else if (!result.friscSuccess && result.simulatorOutput != null && !result.simulatorOutput.isEmpty()) {
            // Show simulator output only if execution failed
            writer.println("          <p><strong>Simulator Output:</strong></p>");
            writer.println("          <pre><code>" + escapeHtml(result.simulatorOutput) + "</code></pre>");
          }
          
          if (!result.runtimeError.isEmpty()) {
            writer.println("          <p><strong>Runtime Error:</strong></p>");
            writer.println("          <pre><code>" + escapeHtml(result.runtimeError) + "</code></pre>");
          }
          
          writer.println("          <p><strong>Manual Testing:</strong> Run <code>node node_modules/friscjs/consoleapp/frisc-console.js compiler-bin/a.frisc</code> after compiling this program to verify the return value in R6 register.</p>");
          
          writer.println("        </div>");
          writer.println("      </details>");
        }
        
        // Errors
        if (!result.lexerErrors.isEmpty() || !result.parserErrors.isEmpty() || 
            !result.semanticErrors.isEmpty() || !result.friscErrors.isEmpty()) {
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
          if (!result.semanticErrors.isEmpty()) {
            if (!result.lexerErrors.isEmpty() || !result.parserErrors.isEmpty()) {
              writer.println("\n---\n");
            }
            writer.println("SEMANTIC ERRORS:");
            writer.println(escapeHtml(result.semanticErrors));
          }
          if (!result.friscErrors.isEmpty()) {
            if (!result.lexerErrors.isEmpty() || !result.parserErrors.isEmpty() || !result.semanticErrors.isEmpty()) {
              writer.println("\n---\n");
            }
            writer.println("CODE GENERATION ERRORS:");
            writer.println(escapeHtml(result.friscErrors));
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
        
        .badge-pass {
          background-color: #10b981;
          color: white;
          font-size: 1.1em;
          padding: 10px 20px;
          font-weight: 700;
        }
        
        .badge-fail {
          background-color: #ef4444;
          color: white;
          font-size: 1.1em;
          padding: 10px 20px;
          font-weight: 700;
        }
        
        .result-badge {
          text-align: center;
          margin-bottom: 20px;
          padding: 15px;
          background: linear-gradient(135deg, #f0f9ff 0%, #e0f2fe 100%);
          border-radius: 8px;
          border: 2px solid #0ea5e9;
        }
        
        .output-comparison {
          margin: 20px 0;
        }
        
        .output-table {
          width: 100%;
          border-collapse: collapse;
          margin: 15px 0;
          background: white;
          border-radius: 8px;
          overflow: hidden;
          box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
        }
        
        .output-table th {
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          color: white;
          padding: 12px;
          text-align: left;
          font-weight: 600;
        }
        
        .output-table td {
          padding: 12px;
          border-bottom: 1px solid #e5e7eb;
        }
        
        .output-table tr:last-child td {
          border-bottom: none;
        }
        
        .output-table code {
          font-family: 'Courier New', monospace;
          font-size: 1.1em;
          background: #f3f4f6;
          padding: 4px 8px;
          border-radius: 4px;
        }
        
        .status-pass {
          color: #10b981;
          font-weight: 700;
          font-size: 1.1em;
        }
        
        .status-fail {
          color: #ef4444;
          font-weight: 700;
          font-size: 1.1em;
        }
        
        .status-unknown {
          color: #6b7280;
          font-weight: normal;
          font-size: 1.1em;
        }
        
        .badge-skip {
          background-color: #6b7280;
          color: white;
        }
        
        .simulator-results {
          padding: 10px;
        }
        
        .simulator-results p {
          margin: 8px 0;
        }
        
        .match-success {
          color: #10b981;
          font-weight: bold;
        }
        
        .match-failure {
          color: #ef4444;
          font-weight: bold;
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

