package hr.fer.ppj.cli.args;

import hr.fer.ppj.cli.pipeline.PipelineStage;
import hr.fer.ppj.cli.ir.IrInterpreterOptions;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.Locale;

/**
 * Minimal argument parser for the compiler CLI.
 */
public final class ArgumentParser {

  /**
   * Result of parsing arguments.
   *
   * @param options parsed options (null if parsing failed)
   * @param error error message when parsing failed
   */
  public record ParseResult(CliOptions options, String error) {
    public boolean success() {
      return error == null || error.isBlank();
    }
  }

  public ParseResult parse(String[] args) {
    if (args.length > 0 && ("run-ir".equals(args[0]) || "--run-ir".equals(args[0]))) {
      return parseRunIrCommand(args);
    }

    EnumSet<PipelineStage> stages = EnumSet.noneOf(PipelineStage.class);
    Path outputDir = Paths.get("compiler-bin");
    Path sourceFile = null;
    boolean help = false;
    boolean runAll = false;

    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if (arg == null || arg.isBlank()) {
        continue;
      }

      switch (arg) {
        case "-h", "--help" -> help = true;
        case "--lex" -> stages.add(PipelineStage.LEX);
        case "--parse" -> stages.add(PipelineStage.PARSE);
        case "--sem" -> stages.add(PipelineStage.SEMANTIC);
        case "--ir" -> stages.add(PipelineStage.IR);
        case "--frisc" -> stages.add(PipelineStage.FRISC);
        case "--run" -> stages.add(PipelineStage.RUN);
        case "--all" -> runAll = true;
        case "--bin" -> {
          if (i + 1 >= args.length) {
            return new ParseResult(null, "--bin requires a directory argument");
          }
          outputDir = Paths.get(args[++i]);
        }
        default -> {
          if (arg.startsWith("-")) {
            return new ParseResult(null, "Unknown option: " + arg.toLowerCase(Locale.ROOT));
          }
          if (sourceFile != null) {
            return new ParseResult(null, "Multiple source files provided: " + sourceFile + ", " + arg);
          }
          sourceFile = Paths.get(arg);
        }
      }
    }

    CliOptions options = new CliOptions(
        sourceFile,
        outputDir,
        stages,
        runAll,
        help,
        false,
        null,
        IrInterpreterOptions.DEFAULT_STEP_LIMIT,
        false);

    if (help) {
      return new ParseResult(options, null);
    }

    if (!options.hasSourceFile()) {
      return new ParseResult(null, "Missing source file path");
    }

    if (!runAll && stages.isEmpty()) {
      return new ParseResult(null, "No stages selected. Use --help for usage.");
    }

    return new ParseResult(options, null);
  }

  private ParseResult parseRunIrCommand(String[] args) {
    Path irFile = null;
    boolean trace = false;
    int stepLimit = IrInterpreterOptions.DEFAULT_STEP_LIMIT;

    for (int i = 1; i < args.length; i++) {
      String arg = args[i];
      if (arg == null || arg.isBlank()) {
        continue;
      }
      switch (arg) {
        case "--trace-ir" -> trace = true;
        case "--ir-step-limit" -> {
          if (i + 1 >= args.length) {
            return new ParseResult(null, "--ir-step-limit requires a number");
          }
          String rawLimit = args[++i];
          try {
            stepLimit = Integer.parseInt(rawLimit);
          } catch (NumberFormatException ex) {
            return new ParseResult(null, "Invalid --ir-step-limit value: " + rawLimit);
          }
          if (stepLimit <= 0) {
            return new ParseResult(null, "--ir-step-limit must be positive");
          }
        }
        default -> {
          if (arg.startsWith("-")) {
            return new ParseResult(null, "Unknown option for run-ir: " + arg.toLowerCase(Locale.ROOT));
          }
          if (irFile != null) {
            return new ParseResult(null, "Multiple IR files provided: " + irFile + ", " + arg);
          }
          irFile = Paths.get(arg);
        }
      }
    }

    if (irFile == null) {
      return new ParseResult(null, "run-ir requires a path to an .ir file");
    }

    CliOptions options = new CliOptions(
        null,
        Paths.get("compiler-bin"),
        EnumSet.noneOf(PipelineStage.class),
        false,
        false,
        true,
        irFile,
        stepLimit,
        trace);
    return new ParseResult(options, null);
  }
}
