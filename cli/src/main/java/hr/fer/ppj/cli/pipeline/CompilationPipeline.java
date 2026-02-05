package hr.fer.ppj.cli.pipeline;

import hr.fer.ppj.ir.IrPipeline;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Lexer.SymbolTableEntry;
import hr.fer.ppj.lexer.io.Token;
import hr.fer.ppj.parser.Parser;
import hr.fer.ppj.parser.io.TokenReader;
import hr.fer.ppj.parser.tree.ParseTree;
import hr.fer.ppj.semantics.analysis.SemanticAnalyzer;
import hr.fer.ppj.semantics.analysis.SemanticAnalyzer.SemanticAnalysisResult;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Compilation pipeline that orchestrates all compiler phases.
 *
 * <p>
 * Phases:
 * <ol>
 * <li>Lexical analysis - tokenizes source code</li>
 * <li>Syntax analysis - builds parse tree</li>
 * <li>Semantic analysis - type checking and symbol resolution</li>
 * <li>IR generation - generates intermediate representation</li>
 * </ol>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class CompilationPipeline {

  private final Path lexerSpecPath;
  private final IrPointerValidator irValidator = new IrPointerValidator();

  public CompilationPipeline() {
    this.lexerSpecPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
  }

  /**
   * Performs lexical analysis on a source file.
   *
   * @param sourceFile the source file to tokenize
   * @return the lexical analysis result
   * @throws Exception if lexical analysis fails
   */
  public LexicalResult lexicalAnalysis(Path sourceFile) throws Exception {
    LexerGenerator generator = new LexerGenerator();
    LexerGeneratorResult result;
    try (FileReader reader = new FileReader(lexerSpecPath.toFile())) {
      result = generator.generate(reader);
    }

    Lexer lexer = new Lexer(result);
    List<Token> tokens;
    try (Reader reader = new FileReader(sourceFile.toFile())) {
      hr.fer.ppj.common.diagnostic.DiagnosticReporter reporter = new hr.fer.ppj.common.diagnostic.DiagnosticReporter() {
        private final List<hr.fer.ppj.common.diagnostic.Diagnostic> diagnostics = new ArrayList<>();
        private boolean hasErrors;

        @Override
        public void report(hr.fer.ppj.common.diagnostic.Diagnostic diagnostic) {
          diagnostics.add(diagnostic);
          if (diagnostic.severity() == hr.fer.ppj.common.diagnostic.Severity.ERROR) {
            hasErrors = true;
          }
          System.err.println("Leksička greška na retku " + diagnostic.location().line()
              + ", stupcu " + diagnostic.location().column() + ": " + diagnostic.message());
        }

        @Override
        public boolean hasErrors() {
          return hasErrors;
        }

        @Override
        public List<hr.fer.ppj.common.diagnostic.Diagnostic> getDiagnostics() {
          return List.copyOf(diagnostics);
        }
      };
      tokens = lexer.tokenize(reader, reporter);
      if (reporter.hasErrors()) {
        throw new Exception("Lexical analysis failed");
      }
    }

    return new LexicalResult(List.copyOf(tokens), lexer.getSymbolTable());
  }

  /**
   * Performs syntax analysis (parsing) on tokens.
   *
   * @param lexical the lexical analysis result
   * @return the parse tree
   * @throws Exception if parsing fails
   */
  public ParseTree syntaxAnalysis(LexicalResult lexical) throws Exception {
    List<TokenReader.Token> parserTokens = new ArrayList<>(lexical.tokens().size());
    for (Token token : lexical.tokens()) {
      parserTokens.add(new TokenReader.Token(token.type(), token.line(), token.value()));
    }
    Parser parser = new Parser();
    return parser.parseTokens(parserTokens);
  }

  /**
   * Performs semantic analysis on the parse tree.
   *
   * @param parseTree the parse tree to analyze
   * @return the semantic analysis result
   * @throws Exception if semantic analysis fails
   */
  public SemanticAnalysisResult semanticAnalysis(ParseTree parseTree) throws Exception {
    SemanticAnalyzer analyzer = new SemanticAnalyzer();
    hr.fer.ppj.common.diagnostic.DiagnosticReporter reporter = new hr.fer.ppj.common.diagnostic.DiagnosticReporter() {
      @Override
      public void report(hr.fer.ppj.common.diagnostic.Diagnostic diagnostic) {
        System.out.println(diagnostic.message());
      }

      @Override
      public boolean hasErrors() {
        return false;
      }

      @Override
      public java.util.List<hr.fer.ppj.common.diagnostic.Diagnostic> getDiagnostics() {
        return java.util.Collections.emptyList();
      }
    };
    return analyzer.analyzeWithResults(parseTree, reporter, null);
  }

  /**
   * Generates IR from the semantic analysis result.
   *
   * @param semantic the semantic analysis result
   * @return the IR program
   */
  public IrProgram generateIr(SemanticAnalysisResult semantic) {
    return IrPipeline.generate(semantic.globalScope(), semantic.parseTree());
  }

  /**
   * Pretty-prints an IR program to a string.
   *
   * @param program the IR program to print
   * @return the formatted IR string
   */
  public String printIr(IrProgram program) {
    return IrPipeline.print(program);
  }

  /**
   * Runs the full compilation pipeline from source to IR.
   *
   * @param sourceFile the source file to compile
   * @return the compilation result containing all artifacts
   * @throws Exception if any compilation phase fails
   */
  public CompilationResult compile(Path sourceFile) throws Exception {
    LexicalResult lexical = lexicalAnalysis(sourceFile);
    ParseTree parseTree = syntaxAnalysis(lexical);
    SemanticAnalysisResult semantic = semanticAnalysis(parseTree);
    IrProgram irProgram = generateIr(semantic);
    String irString = printIr(irProgram);
    irValidator.validate(irString);
    return new CompilationResult(lexical, parseTree, semantic, irProgram, irString);
  }

  /**
   * Result of lexical analysis.
   */
  public record LexicalResult(List<Token> tokens, List<SymbolTableEntry> symbolTable) {
  }

  /**
   * Result of the full compilation pipeline.
   */
  public record CompilationResult(
      LexicalResult lexical,
      ParseTree parseTree,
      SemanticAnalysisResult semantic,
      IrProgram irProgram,
      String irString) {
  }
}
