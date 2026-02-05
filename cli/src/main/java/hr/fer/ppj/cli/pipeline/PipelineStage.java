package hr.fer.ppj.cli.pipeline;

import java.util.List;

/**
 * Ordered compilation stages for the CLI pipeline.
 */
public enum PipelineStage {
  LEX("Lexical Analysis"),
  PARSE("Syntax Analysis"),
  SEMANTIC("Semantic Analysis"),
  IR("IR Generation"),
  FRISC("FRISC Code Generation"),
  RUN("FRISC Execution");

  private final String displayName;

  PipelineStage(String displayName) {
    this.displayName = displayName;
  }

  public String displayName() {
    return displayName;
  }

  public static List<PipelineStage> orderedCompileStages() {
    return List.of(LEX, PARSE, SEMANTIC, IR, FRISC);
  }
}
