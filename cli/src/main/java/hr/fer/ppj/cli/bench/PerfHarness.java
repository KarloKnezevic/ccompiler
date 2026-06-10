package hr.fer.ppj.cli.bench;

import hr.fer.ppj.cli.reporting.CollectingReporter;
import hr.fer.ppj.codegen.frisc.FriscCodeGenerator;
import hr.fer.ppj.ir.IrPipeline;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.lexer.config.LexerConfig;
import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Token;
import hr.fer.ppj.opt.api.OptimizationOptions;
import hr.fer.ppj.opt.pipeline.IrPass;
import hr.fer.ppj.opt.pipeline.PassContext;
import hr.fer.ppj.opt.pipeline.PassResult;
import hr.fer.ppj.opt.rules.arith.Int32ArithmeticPass;
import hr.fer.ppj.opt.rules.arith.TypedConstantFoldingPass;
import hr.fer.ppj.opt.rules.cast.CastSimplificationPass;
import hr.fer.ppj.opt.rules.controlflow.ControlFlowSimplificationPass;
import hr.fer.ppj.opt.rules.controlflow.UnreachableBlockEliminationPass;
import hr.fer.ppj.opt.rules.flow.GlobalValuePropagationPass;
import hr.fer.ppj.opt.rules.inline.TinyFunctionInliningPass;
import hr.fer.ppj.opt.rules.loop.InductionStrengthReductionPass;
import hr.fer.ppj.opt.rules.loop.LoopInvariantCodeMotionPass;
import hr.fer.ppj.opt.rules.memory.DeadSlotStoreEliminationPass;
import hr.fer.ppj.opt.rules.memory.LoadForwardingPass;
import hr.fer.ppj.opt.rules.range.ValueRangeSimplificationPass;
import hr.fer.ppj.opt.rules.shift.Int32ShiftPass;
import hr.fer.ppj.opt.rules.temps.CommonSubexpressionEliminationPass;
import hr.fer.ppj.opt.rules.temps.CopyPropagationPass;
import hr.fer.ppj.opt.rules.temps.DeadTempEliminationPass;
import hr.fer.ppj.opt.validation.IrOptimizationValidator;
import hr.fer.ppj.parser.Parser;
import hr.fer.ppj.parser.io.TokenReader;
import hr.fer.ppj.parser.tree.ParseTree;
import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Benchmark-only harness for Chapter 14 (Performance) of the book. NOT part of
 * the production CLI surface; invoked directly via
 * {@code java -cp cli/target/ccompiler.jar hr.fer.ppj.cli.bench.PerfHarness ...}.
 *
 * <p>It reuses the real front end ({@link IrPipeline}) and back end
 * ({@link FriscCodeGenerator}) so that every number it produces is reproducible
 * from the same compiler state the production pipeline uses. The only thing it
 * adds is a <em>configurable</em> optimization pipeline: the canonical {@code -O1}
 * pass list, optionally with one named pass ablated, and a fixpoint loop that is
 * instrumented to report convergence behaviour.
 *
 * <p>The pass list mirrors {@code IrOptimizer.optimize} exactly; if that list
 * changes, this one must change in lock step.
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li>{@code compile <source.c> <outdir> <config>} where config is
 *       {@code O0}, {@code O1}, or {@code NO:<SimpleClassName>}. Writes
 *       {@code <outdir>/intermediate.ir} and {@code <outdir>/a.out}, and prints
 *       a one-line summary {@code irLines=<n>}.</li>
 *   <li>{@code converge <source.c>} runs the fixpoint loop and prints the number
 *       of iterations to a fixpoint plus, per iteration, the set of passes that
 *       reported a change.</li>
 *   <li>{@code passes} prints the canonical distinct pass class names, one per
 *       line (the ablation work-list).</li>
 * </ul>
 */
public final class PerfHarness {

  private PerfHarness() {}

  /** Canonical -O1 pipeline, identical in order to {@code IrOptimizer.optimize}. */
  private static List<IrPass> canonicalPipeline() {
    List<IrPass> passes = new ArrayList<>();
    passes.add(new Int32ArithmeticPass());
    passes.add(new TypedConstantFoldingPass());
    passes.add(new CastSimplificationPass());
    passes.add(new Int32ShiftPass());
    passes.add(new CommonSubexpressionEliminationPass());
    passes.add(new LoopInvariantCodeMotionPass());
    passes.add(new GlobalValuePropagationPass());
    passes.add(new TinyFunctionInliningPass());
    passes.add(new LoadForwardingPass());
    passes.add(new DeadSlotStoreEliminationPass());
    passes.add(new ValueRangeSimplificationPass());
    passes.add(new CopyPropagationPass());
    passes.add(new DeadTempEliminationPass());
    passes.add(new ControlFlowSimplificationPass());
    passes.add(new UnreachableBlockEliminationPass());
    passes.add(new InductionStrengthReductionPass());
    passes.add(new DeadTempEliminationPass());
    passes.add(new ControlFlowSimplificationPass());
    passes.add(new UnreachableBlockEliminationPass());
    return passes;
  }

  /** Distinct pass class simple names, in first-appearance order. */
  private static List<String> distinctPassNames() {
    Set<String> names = new LinkedHashSet<>();
    for (IrPass p : canonicalPipeline()) {
      names.add(p.getClass().getSimpleName());
    }
    return new ArrayList<>(names);
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 0) {
      System.err.println("usage: PerfHarness <compile|converge|passes> ...");
      System.exit(2);
      return;
    }
    switch (args[0]) {
      case "passes" -> distinctPassNames().forEach(System.out::println);
      case "compile" -> {
        if (args.length < 4) {
          System.err.println("usage: PerfHarness compile <source.c> <outdir> <O0|O1|NO:Class>");
          System.exit(2);
          return;
        }
        compile(Paths.get(args[1]), Paths.get(args[2]), args[3]);
      }
      case "converge" -> {
        if (args.length < 2) {
          System.err.println("usage: PerfHarness converge <source.c>");
          System.exit(2);
          return;
        }
        converge(Paths.get(args[1]));
      }
      default -> {
        System.err.println("unknown mode: " + args[0]);
        System.exit(2);
      }
    }
  }

  /** Lex + parse + lower to typed IR. Throws on any front-end error. */
  private static IrProgram frontEnd(Path source) throws Exception {
    LexerGenerator generator = new LexerGenerator();
    LexerGeneratorResult lexResult;
    try (FileReader reader = new FileReader(LexerConfig.getLexerDefinitionPath().toFile())) {
      lexResult = generator.generate(reader);
    }
    Lexer lexer = new Lexer(lexResult);
    CollectingReporter reporter = new CollectingReporter();
    List<Token> tokens;
    try (Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8)) {
      tokens = lexer.tokenize(reader, reporter);
    }
    if (reporter.hasErrors()) {
      throw new IllegalStateException("lexing failed for " + source);
    }

    List<TokenReader.Token> parserTokens = new ArrayList<>();
    for (Token token : tokens) {
      parserTokens.add(new TokenReader.Token(token.type(), token.line(), token.value()));
    }
    CollectingReporter parseReporter = new CollectingReporter();
    ParseTree parseTree = new Parser().parseTokens(parserTokens, parseReporter);
    if (parseReporter.hasErrors()) {
      throw new IllegalStateException("parsing failed for " + source);
    }
    return IrPipeline.generate(parseTree, System.err);
  }

  /** Runs the (possibly ablated) pipeline to a fixpoint, mirroring PassPipeline. */
  private static IrProgram runPipeline(IrProgram input, List<IrPass> passes, OptimizationOptions options) {
    IrOptimizationValidator validator = new IrOptimizationValidator();
    PassContext context = new PassContext(options, validator);
    IrProgram current = input;
    for (int iteration = 0; iteration < options.maxIterations(); iteration++) {
      boolean changed = false;
      for (IrPass pass : passes) {
        PassResult result = pass.run(current, context);
        current = result.program();
        changed |= result.changed();
      }
      if (!changed) {
        break;
      }
    }
    return current;
  }

  private static void compile(Path source, Path outDir, String config) throws Exception {
    IrProgram program = frontEnd(source);

    IrProgram optimized;
    if ("O0".equals(config)) {
      optimized = program;
    } else {
      List<IrPass> passes;
      if ("O1".equals(config)) {
        passes = canonicalPipeline();
      } else if (config.startsWith("NO:")) {
        String drop = config.substring(3);
        passes = new ArrayList<>();
        for (IrPass p : canonicalPipeline()) {
          if (!p.getClass().getSimpleName().equals(drop)) {
            passes.add(p);
          }
        }
        if (passes.size() == canonicalPipeline().size()) {
          throw new IllegalArgumentException("no such pass to ablate: " + drop);
        }
      } else {
        throw new IllegalArgumentException("bad config: " + config);
      }
      optimized = runPipeline(program, passes, OptimizationOptions.O1);
    }

    String irText = IrPipeline.print(optimized);
    Files.createDirectories(outDir);
    Path irFile = outDir.resolve("intermediate.ir");
    Files.writeString(irFile, irText, StandardCharsets.UTF_8);

    Path aout = outDir.resolve("a.out");
    new FriscCodeGenerator().generate(irText, aout, source.getFileName().toString());

    long irLines = irText.lines().filter(l -> !l.isBlank()).count();
    System.out.println("irLines=" + irLines);
  }

  private static void converge(Path source) throws Exception {
    IrProgram program = frontEnd(source);
    List<IrPass> passes = canonicalPipeline();
    IrOptimizationValidator validator = new IrOptimizationValidator();
    PassContext context = new PassContext(OptimizationOptions.O1, validator);

    IrProgram current = program;
    int iterationsRun = 0;
    int productiveIterations = 0;
    for (int iteration = 0; iteration < OptimizationOptions.O1.maxIterations(); iteration++) {
      iterationsRun++;
      boolean changedInIteration = false;
      Set<String> firedThisIteration = new LinkedHashSet<>();
      for (IrPass pass : passes) {
        PassResult result = pass.run(current, context);
        current = result.program();
        if (result.changed()) {
          changedInIteration = true;
          firedThisIteration.add(pass.getClass().getSimpleName());
        }
      }
      System.out.println("iter " + iteration + ": changed=" + firedThisIteration);
      if (!changedInIteration) {
        break;
      }
      productiveIterations++;
    }
    // productiveIterations = sweeps that changed something; the loop then runs
    // one more confirming sweep that changes nothing (unless the cap is hit).
    System.out.println("sweepsRun=" + iterationsRun);
    System.out.println("productiveSweeps=" + productiveIterations);
    System.out.println("cap=" + OptimizationOptions.O1.maxIterations());
  }
}
