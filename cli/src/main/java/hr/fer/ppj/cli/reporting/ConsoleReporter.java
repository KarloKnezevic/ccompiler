package hr.fer.ppj.cli.reporting;

import hr.fer.ppj.cli.pipeline.PipelineStage;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Console output formatter for CLI execution.
 */
public final class ConsoleReporter {

  private final PrintStream out;
  private final PrintStream err;

  public ConsoleReporter() {
    this(System.out, System.err);
  }

  public ConsoleReporter(PrintStream out, PrintStream err) {
    this.out = Objects.requireNonNull(out, "out must not be null");
    this.err = Objects.requireNonNull(err, "err must not be null");
  }

  public void printHeader(Path sourceFile, Path outputDir, List<PipelineStage> stages) {
    out.println("PPJ C Compiler CLI");
    out.println("Source: " + sourceFile.toAbsolutePath().normalize());
    out.println("Output: " + outputDir.toAbsolutePath().normalize());
    out.println("Stages: " + stages.stream()
        .map(stage -> stage.name().toLowerCase())
        .collect(Collectors.joining(", ")));
    out.println();
  }

  public void printHelp(String helpText) {
    out.println(helpText);
  }

  public void printArgumentError(String message) {
    err.println("Argument error: " + message);
    err.println("Use --help for usage.");
  }

  public void stageStarted(PipelineStage stage, int index, int total) {
    out.println("[" + index + "/" + total + "] " + stage.displayName());
  }

  public void stageSucceeded(PipelineStage stage, Duration elapsed, List<Path> artifacts) {
    out.println("Status: OK (" + formatDuration(elapsed) + ")");
    for (Path artifact : artifacts) {
      out.println("Output: " + artifact.toAbsolutePath().normalize());
    }
    out.println();
  }

  public void stageFailed(PipelineStage stage, Duration elapsed, String message, List<String> details, String hint) {
    err.println("Status: FAILED (" + formatDuration(elapsed) + ")");
    err.println("Stage: " + stage.displayName());
    err.println("Cause: " + message);
    if (details != null) {
      for (String detail : details) {
        err.println("Detail: " + detail);
      }
    }
    if (hint != null && !hint.isBlank()) {
      err.println("Hint: " + hint);
    }
    err.println();
  }

  public void printRuntimeOutput(String output) {
    out.println("Program output:");
    if (output == null || output.isBlank()) {
      out.println("(no output)");
    } else {
      out.print(output);
      if (!output.endsWith(System.lineSeparator())) {
        out.println();
      }
    }
  }

  private String formatDuration(Duration duration) {
    if (duration == null) {
      return "n/a";
    }
    long millis = duration.toMillis();
    if (millis < 1000) {
      return millis + " ms";
    }
    double seconds = millis / 1000.0;
    return String.format("%.2f s", seconds);
  }
}
