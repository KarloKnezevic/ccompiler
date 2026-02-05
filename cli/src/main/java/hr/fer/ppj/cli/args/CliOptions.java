package hr.fer.ppj.cli.args;

import hr.fer.ppj.cli.pipeline.PipelineStage;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Objects;

/**
 * Parsed CLI options for the compiler entrypoint.
 */
public record CliOptions(
    Path sourceFile,
    Path outputDir,
    EnumSet<PipelineStage> requestedStages,
    boolean runAll,
    boolean help
) {

  public CliOptions {
    Objects.requireNonNull(outputDir, "outputDir must not be null");
    Objects.requireNonNull(requestedStages, "requestedStages must not be null");
  }

  public boolean hasSourceFile() {
    return sourceFile != null;
  }
}
