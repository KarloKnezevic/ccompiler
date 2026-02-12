package hr.fer.ppj.cli.args;

import hr.fer.ppj.cli.pipeline.PipelineStage;
import hr.fer.ppj.opt.api.OptimizationLevel;
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
    boolean help,
    boolean runIrCommand,
    Path irFile,
    int irStepLimit,
    boolean irTrace,
    OptimizationLevel optimizationLevel,
    boolean dumpIr
) {

  public CliOptions {
    Objects.requireNonNull(outputDir, "outputDir must not be null");
    Objects.requireNonNull(requestedStages, "requestedStages must not be null");
    Objects.requireNonNull(optimizationLevel, "optimizationLevel must not be null");
    if (runIrCommand && irFile == null) {
      throw new IllegalArgumentException("irFile must be set when runIrCommand is true");
    }
    if (irStepLimit <= 0) {
      throw new IllegalArgumentException("irStepLimit must be positive");
    }
  }

  public boolean hasSourceFile() {
    return sourceFile != null;
  }
}
