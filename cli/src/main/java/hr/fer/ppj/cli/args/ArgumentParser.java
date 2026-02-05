package hr.fer.ppj.cli.args;

import hr.fer.ppj.cli.pipeline.PipelineStage;
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

    CliOptions options = new CliOptions(sourceFile, outputDir, stages, runAll, help);

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
}
