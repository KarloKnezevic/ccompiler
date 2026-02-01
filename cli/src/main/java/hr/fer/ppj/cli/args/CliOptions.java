package hr.fer.ppj.cli.args;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Parsed command-line options for the compiler CLI.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class CliOptions {

  /** Default output directory name. */
  public static final String DEFAULT_OUTPUT_DIR = "compiler-bin";

  private final Command command;
  private final Path inputFile;
  private final Path outputDir;
  private final Path goldenDir;
  private final boolean recursive;
  private final String filter;

  private CliOptions(Builder builder) {
    this.command = builder.command;
    this.inputFile = builder.inputFile;
    this.outputDir = builder.outputDir != null ? builder.outputDir : Paths.get(DEFAULT_OUTPUT_DIR);
    this.goldenDir = builder.goldenDir;
    this.recursive = builder.recursive;
    this.filter = builder.filter;
  }

  public Command command() {
    return command;
  }

  public Optional<Path> inputFile() {
    return Optional.ofNullable(inputFile);
  }

  public Path outputDir() {
    return outputDir;
  }

  public Optional<Path> goldenDir() {
    return Optional.ofNullable(goldenDir);
  }

  public boolean recursive() {
    return recursive;
  }

  public Optional<String> filter() {
    return Optional.ofNullable(filter);
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Available CLI commands.
   */
  public enum Command {
    LEXER,
    SYNTAX,
    SEMANTIC,
    IR,
    IR_TEST,
    TEST,
    RUN,
    HELP
  }

  public static final class Builder {
    private Command command;
    private Path inputFile;
    private Path outputDir;
    private Path goldenDir;
    private boolean recursive;
    private String filter;

    private Builder() {
    }

    public Builder command(Command command) {
      this.command = command;
      return this;
    }

    public Builder inputFile(Path inputFile) {
      this.inputFile = inputFile;
      return this;
    }

    public Builder outputDir(Path outputDir) {
      this.outputDir = outputDir;
      return this;
    }

    public Builder goldenDir(Path goldenDir) {
      this.goldenDir = goldenDir;
      return this;
    }

    public Builder recursive(boolean recursive) {
      this.recursive = recursive;
      return this;
    }

    public Builder filter(String filter) {
      this.filter = filter;
      return this;
    }

    public CliOptions build() {
      return new CliOptions(this);
    }
  }
}
