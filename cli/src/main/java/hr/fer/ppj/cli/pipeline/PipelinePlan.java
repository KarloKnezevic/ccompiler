package hr.fer.ppj.cli.pipeline;

import hr.fer.ppj.cli.args.CliOptions;
import hr.fer.ppj.opt.api.OptimizationLevel;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Resolved pipeline plan based on requested stages.
 */
public record PipelinePlan(
    List<PipelineStage> stages,
    OptimizationLevel optimizationLevel,
    boolean dumpIr,
    boolean verifyEach) {

  public PipelinePlan {
    Objects.requireNonNull(stages, "stages must not be null");
    Objects.requireNonNull(optimizationLevel, "optimizationLevel must not be null");
  }

  public static PipelinePlan from(CliOptions options) {
    Objects.requireNonNull(options, "options must not be null");

    EnumSet<PipelineStage> requested = EnumSet.copyOf(options.requestedStages());
    if (options.runAll()) {
      requested.addAll(PipelineStage.orderedCompileStages());
    }

    boolean includeRun = requested.contains(PipelineStage.RUN);
    List<PipelineStage> orderedCompile = PipelineStage.orderedCompileStages();

    int maxIndex = -1;
    for (int i = 0; i < orderedCompile.size(); i++) {
      if (requested.contains(orderedCompile.get(i))) {
        maxIndex = Math.max(maxIndex, i);
      }
    }

    if (includeRun && maxIndex < orderedCompile.size() - 1) {
      maxIndex = orderedCompile.size() - 1;
    }

    List<PipelineStage> planStages = new ArrayList<>();
    if (maxIndex >= 0) {
      for (int i = 0; i <= maxIndex; i++) {
        planStages.add(orderedCompile.get(i));
      }
    }

    if (includeRun) {
      planStages.add(PipelineStage.RUN);
    }

    return new PipelinePlan(
        List.copyOf(planStages), options.optimizationLevel(), options.dumpIr(), options.verifyEach());
  }
}
