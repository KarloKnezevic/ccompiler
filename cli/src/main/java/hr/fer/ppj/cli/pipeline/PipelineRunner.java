package hr.fer.ppj.cli.pipeline;

import hr.fer.ppj.cli.FriscRunner;
import hr.fer.ppj.cli.io.BinDirectoryManager;
import hr.fer.ppj.cli.io.CompilerBinLayout;
import hr.fer.ppj.cli.io.LexerOutputWriter;
import hr.fer.ppj.cli.reporting.CollectingReporter;
import hr.fer.ppj.cli.reporting.ConsoleReporter;
import hr.fer.ppj.codegen.frisc.FriscCodeGenerator;
import hr.fer.ppj.common.diagnostic.Diagnostic;
import hr.fer.ppj.ir.IrPipeline;
import hr.fer.ppj.ir.diagnostic.IrCompilationException;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.lexer.config.LexerConfig;
import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Token;
import hr.fer.ppj.parser.Parser;
import hr.fer.ppj.parser.io.TokenReader;
import hr.fer.ppj.parser.tree.ParseTree;
import hr.fer.ppj.semantics.analysis.SemanticAnalyzer;
import hr.fer.ppj.semantics.errors.SemanticException;
import hr.fer.ppj.semantics.io.SemanticReport;
import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes compiler stages in order and reports progress.
 */
public final class PipelineRunner {

  private final ConsoleReporter reporter;
  private final BinDirectoryManager binManager;
  private final LexerOutputWriter lexerOutputWriter;
  private final IrPointerValidator pointerValidator;

  public PipelineRunner(ConsoleReporter reporter) {
    this(reporter, new BinDirectoryManager(), new LexerOutputWriter(), new IrPointerValidator());
  }

  PipelineRunner(
      ConsoleReporter reporter,
      BinDirectoryManager binManager,
      LexerOutputWriter lexerOutputWriter,
      IrPointerValidator pointerValidator) {
    this.reporter = reporter;
    this.binManager = binManager;
    this.lexerOutputWriter = lexerOutputWriter;
    this.pointerValidator = pointerValidator;
  }

  public boolean run(PipelinePlan plan, Path sourceFile, Path outputDir) {
    CompilerBinLayout layout = new CompilerBinLayout(outputDir);
    PipelineContext context = new PipelineContext(sourceFile, layout);

    try {
      binManager.prepare(outputDir);
    } catch (Exception ex) {
      reporter.stageFailed(PipelineStage.LEX, Duration.ZERO, "Failed to prepare output directory",
          List.of(ex.getMessage()), "Check output directory permissions");
      return false;
    }

    reporter.printHeader(sourceFile, outputDir, plan.stages());

    int total = plan.stages().size();
    int index = 1;
    for (PipelineStage stage : plan.stages()) {
      reporter.stageStarted(stage, index, total);
      Instant start = Instant.now();
      try {
        StageArtifacts artifacts = executeStage(stage, context);
        Duration elapsed = Duration.between(start, Instant.now());
        reporter.stageSucceeded(stage, elapsed, artifacts.artifacts());
        if (artifacts.runtimeOutput() != null) {
          reporter.printRuntimeOutput(artifacts.runtimeOutput());
        }
      } catch (StageFailure failure) {
        Duration elapsed = Duration.between(start, Instant.now());
        reporter.stageFailed(stage, elapsed, failure.getMessage(), failure.details(), failure.hint());
        return false;
      }
      index++;
    }

    return true;
  }

  private StageArtifacts executeStage(PipelineStage stage, PipelineContext context) throws StageFailure {
    return switch (stage) {
      case LEX -> runLex(context);
      case PARSE -> runParse(context);
      case SEMANTIC -> runSemantic(context);
      case IR -> runIr(context);
      case FRISC -> runFrisc(context);
      case RUN -> runFriscExecution(context);
    };
  }

  private StageArtifacts runLex(PipelineContext context) throws StageFailure {
    try {
      LexerGenerator generator = new LexerGenerator();
      LexerGeneratorResult result;
      try (FileReader reader = new FileReader(LexerConfig.getLexerDefinitionPath().toFile())) {
        result = generator.generate(reader);
      }

      Lexer lexer = new Lexer(result);
      CollectingReporter reporter = new CollectingReporter();
      List<Token> tokens;
      try (Reader reader = Files.newBufferedReader(context.sourceFile(), StandardCharsets.UTF_8)) {
        tokens = lexer.tokenize(reader, reporter);
      }

      if (reporter.hasErrors()) {
        throw new StageFailure("Lexical analysis failed", formatDiagnostics(reporter.getDiagnostics()),
            "Check leksicke_jedinke.txt for token stream details");
      }

      List<Lexer.SymbolTableEntry> symbolTable = lexer.getSymbolTable();
      lexerOutputWriter.write(context.layout().lexerOutput(), symbolTable, tokens);

      context.tokens(tokens);
      context.symbolTable(symbolTable);

      return StageArtifacts.of(context.layout().artifactsForStage(PipelineStage.LEX));
    } catch (StageFailure failure) {
      throw failure;
    } catch (Exception ex) {
      throw new StageFailure("Lexical analysis failed", ex, List.of(ex.getMessage()),
          "Verify the lexer definition and input file");
    }
  }

  private StageArtifacts runParse(PipelineContext context) throws StageFailure {
    try {
      List<TokenReader.Token> parserTokens = new ArrayList<>();
      for (Token token : context.tokens()) {
        parserTokens.add(new TokenReader.Token(token.type(), token.line(), token.value()));
      }

      CollectingReporter reporter = new CollectingReporter();
      Parser parser = new Parser();
      ParseTree parseTree = parser.parseTokens(parserTokens, reporter);

      if (reporter.hasErrors()) {
        throw new StageFailure("Parsing failed", formatDiagnostics(reporter.getDiagnostics()),
            "See sintaksno_stablo.txt for parser output");
      }

      Files.writeString(context.layout().generativeTree(), parseTree.toGenerativeTreeString(), StandardCharsets.UTF_8);
      Files.writeString(context.layout().syntaxTree(), parseTree.toSyntaxTreeString(), StandardCharsets.UTF_8);

      context.parseTree(parseTree);
      return StageArtifacts.of(context.layout().artifactsForStage(PipelineStage.PARSE));
    } catch (StageFailure failure) {
      writeParseError(context);
      throw failure;
    } catch (Parser.ParserException ex) {
      writeParseError(context, ex.getMessage());
      throw new StageFailure("Parsing failed", ex, List.of(ex.getMessage()),
          "Check syntax tree outputs for details");
    } catch (Exception ex) {
      writeParseError(context, ex.getMessage());
      throw new StageFailure("Parsing failed", ex, List.of(ex.getMessage()),
          "Verify the parser definition and token stream");
    }
  }

  private StageArtifacts runSemantic(PipelineContext context) throws StageFailure {
    try {
      CollectingReporter reporter = new CollectingReporter();
      SemanticAnalyzer analyzer = new SemanticAnalyzer();
      SemanticReport report = new SemanticReport(context.layout().outputDir());
      SemanticAnalyzer.SemanticAnalysisResult result =
          analyzer.analyzeWithResults(context.parseTree(), reporter, report);

      if (reporter.hasErrors()) {
        throw new StageFailure("Semantic analysis failed", formatDiagnostics(reporter.getDiagnostics()),
            "Review tablica_simbola.txt and semanticko_stablo.txt");
      }

      context.semanticResult(result);
      return StageArtifacts.of(context.layout().artifactsForStage(PipelineStage.SEMANTIC));
    } catch (SemanticException ex) {
      throw new StageFailure("Semantic analysis failed", ex, List.of(ex.getMessage()),
          "Review semanticko_stablo.txt for context");
    } catch (Exception ex) {
      throw new StageFailure("Semantic analysis failed", ex, List.of(ex.getMessage()),
          "Check semantic rules and input program");
    }
  }

  private StageArtifacts runIr(PipelineContext context) throws StageFailure {
    try {
      IrProgram program = IrPipeline.generate(
          context.semanticResult().globalScope(),
          context.semanticResult().parseTree());
      String irText = IrPipeline.print(program);
      pointerValidator.validate(irText);
      Files.writeString(context.layout().irFile(), irText, StandardCharsets.UTF_8);

      context.irProgram(program);
      context.irText(irText);

      return StageArtifacts.of(context.layout().artifactsForStage(PipelineStage.IR));
    } catch (IrCompilationException ex) {
      throw new StageFailure("IR generation failed", formatIrDiagnostics(ex),
          "Check medukod.ir for partial output if available");
    } catch (Exception ex) {
      throw new StageFailure("IR generation failed", ex, List.of(ex.getMessage()),
          "Verify semantic analysis output and IR generation rules");
    }
  }

  private StageArtifacts runFrisc(PipelineContext context) throws StageFailure {
    try {
      FriscCodeGenerator codegen = new FriscCodeGenerator();
      Path friscFile = context.layout().friscFile();
      codegen.generate(context.irText(), friscFile, context.sourceFile().getFileName().toString());
      context.friscFile(friscFile);
      return StageArtifacts.of(context.layout().artifactsForStage(PipelineStage.FRISC));
    } catch (Exception ex) {
      throw new StageFailure("FRISC code generation failed", ex, List.of(ex.getMessage()),
          "Review IR input and code generator diagnostics");
    }
  }

  private StageArtifacts runFriscExecution(PipelineContext context) throws StageFailure {
    try {
      FriscRunner runner = new FriscRunner();
      FriscRunner.Result result = runner.run(context.friscFile());
      if (!result.success()) {
        List<String> details = new ArrayList<>();
        details.add("Simulator output:");
        details.addAll(splitLines(result.output()));
        throw new StageFailure("FRISC execution failed: " + result.errorMessage(), details,
            "Ensure a.frisc is valid and the simulator is available");
      }
      return StageArtifacts.withOutput(List.of(), result.output());
    } catch (StageFailure failure) {
      throw failure;
    } catch (Exception ex) {
      throw new StageFailure("FRISC execution failed", ex, List.of(ex.getMessage()),
          "Ensure the FRISC simulator is installed in node_modules");
    }
  }

  private void writeParseError(PipelineContext context) {
    writeParseError(context, "Unknown parse error");
  }

  private void writeParseError(PipelineContext context, String message) {
    try {
      String output = "Parse error: " + message;
      Files.writeString(context.layout().generativeTree(), output, StandardCharsets.UTF_8);
      Files.writeString(context.layout().syntaxTree(), output, StandardCharsets.UTF_8);
    } catch (Exception ignored) {
      // Ignore secondary failures
    }
  }

  private List<String> formatDiagnostics(List<Diagnostic> diagnostics) {
    List<String> lines = new ArrayList<>();
    for (Diagnostic diagnostic : diagnostics) {
      String location = diagnostic.location().toString();
      lines.add(location + ": " + diagnostic.message());
    }
    return lines;
  }

  private List<String> formatIrDiagnostics(IrCompilationException ex) {
    List<String> lines = new ArrayList<>();
    ex.getDiagnostics().forEach(diagnostic -> lines.add(diagnostic.toString()));
    return lines;
  }

  private List<String> splitLines(String text) {
    if (text == null || text.isBlank()) {
      return List.of("(no output)");
    }
    return List.of(text.split("\\R"));
  }

  private record StageArtifacts(List<Path> artifacts, String runtimeOutput) {
    static StageArtifacts of(List<Path> artifacts) {
      return new StageArtifacts(artifacts, null);
    }

    static StageArtifacts withOutput(List<Path> artifacts, String runtimeOutput) {
      return new StageArtifacts(artifacts, runtimeOutput);
    }
  }
}
