package hr.fer.ppj.cli.support;

import hr.fer.ppj.ir.IrPipeline;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
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
 * Test-only compilation helper from source code to typed IR.
 */
public final class TestCompilationPipeline {

  private final Path lexerSpecPath;

  public TestCompilationPipeline() {
    this.lexerSpecPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
  }

  public CompilationResult compile(Path sourceFile) throws Exception {
    LexicalResult lexical = lexicalAnalysis(sourceFile);
    ParseTree parseTree = syntaxAnalysis(lexical);
    SemanticAnalysisResult semantic = semanticAnalysis(parseTree);
    IrProgram irProgram = IrPipeline.generate(semantic.globalScope(), semantic.parseTree());
    String irString = IrPipeline.print(irProgram);
    return new CompilationResult(lexical, parseTree, semantic, irProgram, irString);
  }

  private LexicalResult lexicalAnalysis(Path sourceFile) throws Exception {
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

  private ParseTree syntaxAnalysis(LexicalResult lexical) throws Exception {
    List<TokenReader.Token> parserTokens = new ArrayList<>(lexical.tokens().size());
    for (Token token : lexical.tokens()) {
      parserTokens.add(new TokenReader.Token(token.type(), token.line(), token.value()));
    }
    Parser parser = new Parser();
    return parser.parseTokens(parserTokens);
  }

  private SemanticAnalysisResult semanticAnalysis(ParseTree parseTree) {
    SemanticAnalyzer analyzer = new SemanticAnalyzer();
    hr.fer.ppj.common.diagnostic.DiagnosticReporter reporter = new hr.fer.ppj.common.diagnostic.DiagnosticReporter() {
      @Override
      public void report(hr.fer.ppj.common.diagnostic.Diagnostic diagnostic) {
      }

      @Override
      public boolean hasErrors() {
        return false;
      }

      @Override
      public List<hr.fer.ppj.common.diagnostic.Diagnostic> getDiagnostics() {
        return List.of();
      }
    };
    return analyzer.analyzeWithResults(parseTree, reporter, null);
  }

  public record LexicalResult(List<Token> tokens, List<Lexer.SymbolTableEntry> symbolTable) {
  }

  public record CompilationResult(
      LexicalResult lexical,
      ParseTree parseTree,
      SemanticAnalysisResult semantic,
      IrProgram irProgram,
      String irString) {
  }
}
