package hr.fer.ppj.cli.pipeline;

import hr.fer.ppj.cli.FriscRunner;
import hr.fer.ppj.cli.io.BinDirectoryManager;
import hr.fer.ppj.cli.io.CompilerBinLayout;
import hr.fer.ppj.cli.io.IrDumpWriter;
import hr.fer.ppj.cli.io.IrOutputWriter;
import hr.fer.ppj.cli.io.LexerOutputWriter;
import hr.fer.ppj.cli.io.SemanticOutputWriter;
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
import hr.fer.ppj.opt.api.IrOptimizer;
import hr.fer.ppj.opt.api.OptimizationLevel;
import hr.fer.ppj.opt.api.OptimizationOptions;
import hr.fer.ppj.semantics.analysis.SemanticAnalyzer;
import hr.fer.ppj.semantics.errors.SemanticException;
import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes compiler stages in order and reports progress.
 */
public final class PipelineRunner {

  private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final ConsoleReporter reporter;
  private final BinDirectoryManager binManager;
  private final LexerOutputWriter lexerOutputWriter;
  private final SemanticOutputWriter semanticOutputWriter;
  private final IrOutputWriter irOutputWriter;
  private final IrDumpWriter irDumpWriter;
  private final IrPointerValidator pointerValidator;
  private final IrOptimizer irOptimizer;

  public PipelineRunner(ConsoleReporter reporter) {
    this(
        reporter,
        new BinDirectoryManager(),
        new LexerOutputWriter(),
        new SemanticOutputWriter(),
        new IrOutputWriter(),
        new IrDumpWriter(),
        new IrPointerValidator(),
        new IrOptimizer());
  }

  PipelineRunner(
      ConsoleReporter reporter,
      BinDirectoryManager binManager,
      LexerOutputWriter lexerOutputWriter,
      SemanticOutputWriter semanticOutputWriter,
      IrOutputWriter irOutputWriter,
      IrDumpWriter irDumpWriter,
      IrPointerValidator pointerValidator,
      IrOptimizer irOptimizer) {
    this.reporter = reporter;
    this.binManager = binManager;
    this.lexerOutputWriter = lexerOutputWriter;
    this.semanticOutputWriter = semanticOutputWriter;
    this.irOutputWriter = irOutputWriter;
    this.irDumpWriter = irDumpWriter;
    this.pointerValidator = pointerValidator;
    this.irOptimizer = irOptimizer;
  }

  public boolean run(PipelinePlan plan, Path sourceFile, Path outputDir) {
    CompilerBinLayout layout = new CompilerBinLayout(outputDir);
    PipelineContext context = new PipelineContext(sourceFile, layout);

    try {
      binManager.prepare(outputDir);
    } catch (Exception ex) {
      reporter.stageFailed(
          PipelineStage.LEX,
          Duration.ZERO,
          "Failed to prepare output directory",
          List.of(ex.getMessage()),
          "Expected a writable output directory.");
      writeErrorsFile(layout, sourceFile, PipelineStage.LEX,
          new StageFailure("Failed to prepare output directory", ex, List.of(ex.getMessage()),
              "Expected a writable output directory."));
      return false;
    }

    reporter.printHeader(sourceFile, outputDir, plan.stages());

    int total = plan.stages().size();
    int index = 1;
    for (PipelineStage stage : plan.stages()) {
      reporter.stageStarted(stage, index, total);
      Instant start = Instant.now();
      try {
        StageArtifacts artifacts = executeStage(stage, context, plan);
        Duration elapsed = Duration.between(start, Instant.now());
        reporter.stageSucceeded(stage, elapsed, artifacts.artifacts());
        if (artifacts.runtimeOutput() != null) {
          reporter.printRuntimeOutput(artifacts.runtimeOutput());
        }
      } catch (StageFailure failure) {
        Duration elapsed = Duration.between(start, Instant.now());
        reporter.stageFailed(stage, elapsed, failure.getMessage(), failure.details(), failure.hint());
        writeErrorsFile(layout, sourceFile, stage, failure);
        reporter.printErrorArtifact(layout.errorsFile());
        return false;
      }
      index++;
    }

    return true;
  }

  private StageArtifacts executeStage(PipelineStage stage, PipelineContext context, PipelinePlan plan) throws StageFailure {
    return switch (stage) {
      case LEX -> runLex(context);
      case PARSE -> runParse(context);
      case SEMANTIC -> runSemantic(context);
      case IR -> runIr(context);
      case OPT -> runOptimization(context, plan);
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
      CollectingReporter diagnosticReporter = new CollectingReporter();
      List<Token> tokens;
      try (Reader reader = java.nio.file.Files.newBufferedReader(context.sourceFile(), StandardCharsets.UTF_8)) {
        tokens = lexer.tokenize(reader, diagnosticReporter);
      }

      if (diagnosticReporter.hasErrors()) {
        throw new StageFailure(
            "Lexical analysis failed",
            formatDiagnostics(diagnosticReporter.getDiagnostics()),
            "Expected a source file that conforms to lexer token definitions.");
      }

      List<Lexer.SymbolTableEntry> symbolTable = lexer.getSymbolTable();
      lexerOutputWriter.write(context.layout().tokensFile(), symbolTable, tokens);

      context.tokens(tokens);
      context.symbolTable(symbolTable);

      return StageArtifacts.of(context.layout().artifactsForStage(PipelineStage.LEX));
    } catch (StageFailure failure) {
      throw failure;
    } catch (Exception ex) {
      throw new StageFailure(
          "Lexical analysis failed",
          ex,
          List.of(ex.getMessage()),
          "Expected valid lexer configuration and readable source input.");
    }
  }

  private StageArtifacts runParse(PipelineContext context) throws StageFailure {
    try {
      List<TokenReader.Token> parserTokens = new ArrayList<>();
      for (Token token : context.tokens()) {
        parserTokens.add(new TokenReader.Token(token.type(), token.line(), token.value()));
      }

      CollectingReporter diagnosticReporter = new CollectingReporter();
      Parser parser = new Parser();
      ParseTree parseTree = parser.parseTokens(parserTokens, diagnosticReporter);

      if (diagnosticReporter.hasErrors()) {
        throw new StageFailure(
            "Parsing failed",
            formatDiagnostics(diagnosticReporter.getDiagnostics()),
            "Expected token sequence that matches parser grammar.");
      }

      java.nio.file.Files.writeString(context.layout().astFile(), parseTree.toSyntaxTreeString(), StandardCharsets.UTF_8);
      context.parseTree(parseTree);
      return StageArtifacts.of(context.layout().artifactsForStage(PipelineStage.PARSE));
    } catch (StageFailure failure) {
      throw failure;
    } catch (Parser.ParserException ex) {
      throw new StageFailure(
          "Parsing failed",
          ex,
          List.of(ex.getMessage()),
          "Expected source syntax compatible with parser grammar.");
    } catch (Exception ex) {
      throw new StageFailure(
          "Parsing failed",
          ex,
          List.of(ex.getMessage()),
          "Expected source syntax compatible with parser grammar.");
    }
  }

  private StageArtifacts runSemantic(PipelineContext context) throws StageFailure {
    try {
      CollectingReporter diagnosticReporter = new CollectingReporter();
      SemanticAnalyzer analyzer = new SemanticAnalyzer();
      SemanticAnalyzer.SemanticAnalysisResult result =
          analyzer.analyzeWithResults(context.parseTree(), diagnosticReporter, null);

      if (diagnosticReporter.hasErrors()) {
        throw new StageFailure(
            "Semantic analysis failed",
            formatDiagnostics(diagnosticReporter.getDiagnostics()),
            "Expected type-correct program according to semantic rules.");
      }

      semanticOutputWriter.write(context.layout().semanticFile(), result.globalScope(), result.parseTree());
      context.semanticResult(result);
      return StageArtifacts.of(context.layout().artifactsForStage(PipelineStage.SEMANTIC));
    } catch (StageFailure failure) {
      throw failure;
    } catch (SemanticException ex) {
      throw new StageFailure(
          "Semantic analysis failed",
          ex,
          List.of(ex.getMessage()),
          "Expected declarations, types, and expressions to satisfy semantic constraints.");
    } catch (Exception ex) {
      throw new StageFailure(
          "Semantic analysis failed",
          ex,
          List.of(ex.getMessage()),
          "Expected declarations, types, and expressions to satisfy semantic constraints.");
    }
  }

  private StageArtifacts runIr(PipelineContext context) throws StageFailure {
    try {
      IrProgram program = IrPipeline.generate(
          context.semanticResult().globalScope(),
          context.semanticResult().parseTree());
      String irText = IrPipeline.print(program);
      pointerValidator.validate(irText);
      irOutputWriter.write(context.layout().irFile(), irText, context.sourceFile());

      context.irProgram(program);
      context.irText(irText);

      return StageArtifacts.of(context.layout().artifactsForStage(PipelineStage.IR));
    } catch (IrCompilationException ex) {
      throw new StageFailure(
          "IR generation failed",
          formatIrDiagnostics(ex),
          "Expected semantically valid program to lower into typed IR.");
    } catch (Exception ex) {
      throw new StageFailure(
          "IR generation failed",
          ex,
          List.of(ex.getMessage()),
          "Expected semantically valid program to lower into typed IR.");
    }
  }

  private StageArtifacts runOptimization(PipelineContext context, PipelinePlan plan) throws StageFailure {
    try {
      String preOptimizationIr = context.irText();
      OptimizationOptions options =
          plan.optimizationLevel() == OptimizationLevel.O1 ? OptimizationOptions.O1 : OptimizationOptions.O0;

      IrProgram optimizedProgram = irOptimizer.optimize(context.irProgram(), options);
      String optimizedIr = IrPipeline.print(optimizedProgram);
      pointerValidator.validate(optimizedIr);
      irOutputWriter.write(context.layout().irFile(), optimizedIr, context.sourceFile());

      context.irProgram(optimizedProgram);
      context.irText(optimizedIr);

      List<Path> artifacts = new ArrayList<>(context.layout().artifactsForStage(PipelineStage.OPT));
      if (plan.dumpIr()) {
        artifacts.addAll(irDumpWriter.write(
            context.layout().outputDir(),
            context.sourceFile(),
            preOptimizationIr,
            optimizedIr));
      }

      return StageArtifacts.of(artifacts);
    } catch (Exception ex) {
      throw new StageFailure(
          "IR optimization failed",
          ex,
          List.of(ex.getMessage()),
          "Expected valid canonical IR and compatible optimization options.");
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
      throw new StageFailure(
          "FRISC code generation failed",
          ex,
          List.of(ex.getMessage()),
          "Expected typed IR compatible with FRISC backend lowering rules.");
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
        throw new StageFailure(
            "FRISC execution failed: " + result.errorMessage(),
            details,
            "Expected executable FRISC program in a.out and available simulator runtime.");
      }
      return StageArtifacts.withOutput(List.of(), result.output());
    } catch (StageFailure failure) {
      throw failure;
    } catch (Exception ex) {
      throw new StageFailure(
          "FRISC execution failed",
          ex,
          List.of(ex.getMessage()),
          "Expected available FRISC simulator runtime in node_modules.");
    }
  }

  private void writeErrorsFile(
      CompilerBinLayout layout,
      Path sourceFile,
      PipelineStage stage,
      StageFailure failure) {
    String report = formatErrorReport(sourceFile, stage, failure);
    try {
      binManager.replaceWithSingleFile(layout.outputDir(), layout.errorsFile(), report);
    } catch (Exception ex) {
      System.err.println("Failed to write " + layout.errorsFile().toAbsolutePath().normalize() + ": " + ex.getMessage());
    }
  }

  private String formatErrorReport(Path sourceFile, PipelineStage stage, StageFailure failure) {
    StringBuilder sb = new StringBuilder(4096);
    sb.append("COMPILATION FAILURE REPORT\n");
    sb.append("==========================\n\n");
    sb.append("Timestamp\n");
    sb.append("- ").append(LocalDateTime.now().format(TIMESTAMP_FORMAT)).append('\n');
    sb.append("Source File\n");
    sb.append("- ").append(sourceFile.toAbsolutePath().normalize()).append('\n');
    sb.append("Failure Phase\n");
    sb.append("- ").append(stage.displayName()).append('\n');
    sb.append("What Broke\n");
    sb.append("- ").append(failure.getMessage()).append("\n\n");

    if (!failure.details().isEmpty()) {
      sb.append("Diagnostics\n");
      for (String detail : failure.details()) {
        sb.append("- ").append(detail).append('\n');
      }
      sb.append('\n');
    }

    if (failure.hint() != null && !failure.hint().isBlank()) {
      sb.append("Expected\n");
      sb.append("- ").append(failure.hint()).append('\n');
    }

    Throwable cause = failure.getCause();
    if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
      sb.append('\n');
      sb.append("Root Cause\n");
      sb.append("- ").append(cause.getMessage()).append('\n');
    }

    return sb.toString();
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
