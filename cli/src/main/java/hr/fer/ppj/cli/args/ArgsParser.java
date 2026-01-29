package hr.fer.ppj.cli.args;

import hr.fer.ppj.cli.args.CliOptions.Command;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Parses command-line arguments into {@link CliOptions}.
 *
 * <p>Supported commands and flags:
 * <ul>
 *   <li>{@code lexer <file>} - Lexical analysis only</li>
 *   <li>{@code syntax <file>} - Lexical + syntax analysis</li>
 *   <li>{@code semantic <file>} - Full compilation pipeline</li>
 *   <li>{@code ir --in <file> [--out <dir>]} - Generate IR for a single file</li>
 *   <li>{@code ir-test --golden <dir> [--out <dir>] [--recursive]} - Run golden IR tests</li>
 *   <li>{@code run <frisc-file>} - Execute FRISC assembly</li>
 *   <li>{@code <file>} - Full compilation (default, same as semantic)</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ArgsParser {

  private ArgsParser() {}

  /**
   * Parses command-line arguments.
   *
   * @param args the command-line arguments
   * @return the parsed options
   * @throws ArgsParseException if the arguments are invalid
   */
  public static CliOptions parse(String[] args) throws ArgsParseException {
    if (args.length == 0) {
      throw new ArgsParseException("No arguments provided");
    }

    String first = args[0];

    // Check for help
    if (first.equals("--help") || first.equals("-h") || first.equals("help")) {
      return CliOptions.builder().command(Command.HELP).build();
    }

    // Check if first argument is a command
    return switch (first) {
      case "lexer" -> parseLexer(args);
      case "syntax" -> parseSyntax(args);
      case "semantic" -> parseSemantic(args);
      case "ir" -> parseIr(args);
      case "ir-test" -> parseIrTest(args);
      case "run" -> parseRun(args);
      default -> parseDefault(args);
    };
  }

  private static CliOptions parseLexer(String[] args) throws ArgsParseException {
    if (args.length < 2) {
      throw new ArgsParseException("lexer command requires a file path");
    }
    return CliOptions.builder()
        .command(Command.LEXER)
        .inputFile(Paths.get(args[1]))
        .build();
  }

  private static CliOptions parseSyntax(String[] args) throws ArgsParseException {
    if (args.length < 2) {
      throw new ArgsParseException("syntax command requires a file path");
    }
    return CliOptions.builder()
        .command(Command.SYNTAX)
        .inputFile(Paths.get(args[1]))
        .build();
  }

  private static CliOptions parseSemantic(String[] args) throws ArgsParseException {
    if (args.length < 2) {
      throw new ArgsParseException("semantic command requires a file path");
    }
    return CliOptions.builder()
        .command(Command.SEMANTIC)
        .inputFile(Paths.get(args[1]))
        .build();
  }

  private static CliOptions parseRun(String[] args) throws ArgsParseException {
    if (args.length < 2) {
      throw new ArgsParseException("run command requires a FRISC file path");
    }
    return CliOptions.builder()
        .command(Command.RUN)
        .inputFile(Paths.get(args[1]))
        .build();
  }

  private static CliOptions parseIr(String[] args) throws ArgsParseException {
    CliOptions.Builder builder = CliOptions.builder().command(Command.IR);
    Path inputFile = null;
    Path outputDir = null;

    for (int i = 1; i < args.length; i++) {
      String arg = args[i];
      if (arg.equals("--in") || arg.equals("-i")) {
        if (i + 1 >= args.length) {
          throw new ArgsParseException("--in requires a file path");
        }
        inputFile = Paths.get(args[++i]);
      } else if (arg.equals("--out") || arg.equals("-o")) {
        if (i + 1 >= args.length) {
          throw new ArgsParseException("--out requires a directory path");
        }
        outputDir = Paths.get(args[++i]);
      } else if (!arg.startsWith("-")) {
        // Positional argument - treat as input file
        inputFile = Paths.get(arg);
      } else {
        throw new ArgsParseException("Unknown flag: " + arg);
      }
    }

    if (inputFile == null) {
      throw new ArgsParseException("ir command requires --in <file> or a positional file argument");
    }

    builder.inputFile(inputFile);
    if (outputDir != null) {
      builder.outputDir(outputDir);
    }

    return builder.build();
  }

  private static CliOptions parseIrTest(String[] args) throws ArgsParseException {
    CliOptions.Builder builder = CliOptions.builder().command(Command.IR_TEST);
    Path goldenDir = null;
    Path outputDir = null;
    boolean recursive = false;

    for (int i = 1; i < args.length; i++) {
      String arg = args[i];
      if (arg.equals("--golden") || arg.equals("-g")) {
        if (i + 1 >= args.length) {
          throw new ArgsParseException("--golden requires a directory path");
        }
        goldenDir = Paths.get(args[++i]);
      } else if (arg.equals("--out") || arg.equals("-o")) {
        if (i + 1 >= args.length) {
          throw new ArgsParseException("--out requires a directory path");
        }
        outputDir = Paths.get(args[++i]);
      } else if (arg.equals("--recursive") || arg.equals("-r")) {
        recursive = true;
      } else {
        throw new ArgsParseException("Unknown flag: " + arg);
      }
    }

    if (goldenDir == null) {
      throw new ArgsParseException("ir-test command requires --golden <directory>");
    }

    builder.goldenDir(goldenDir);
    builder.recursive(recursive);
    if (outputDir != null) {
      builder.outputDir(outputDir);
    }

    return builder.build();
  }

  private static CliOptions parseDefault(String[] args) {
    // First argument is a file path - treat as full compilation
    return CliOptions.builder()
        .command(Command.SEMANTIC)
        .inputFile(Paths.get(args[0]))
        .build();
  }

  /**
   * Exception thrown when argument parsing fails.
   */
  public static final class ArgsParseException extends Exception {
    public ArgsParseException(String message) {
      super(message);
    }
  }
}
