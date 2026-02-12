package hr.fer.ppj.cli.io;

import hr.fer.ppj.cli.pipeline.PipelineStage;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Standard output layout for compiler artifacts.
 */
public record CompilerBinLayout(Path outputDir) {

  public CompilerBinLayout {
    Objects.requireNonNull(outputDir, "outputDir must not be null");
  }

  public Path tokensFile() {
    return outputDir.resolve("tokens.txt");
  }

  public Path astFile() {
    return outputDir.resolve("ast.txt");
  }

  public Path semanticFile() {
    return outputDir.resolve("semantic_tree.txt");
  }

  public Path irFile() {
    return outputDir.resolve("intermediate.ir");
  }

  public Path friscFile() {
    return outputDir.resolve("a.out");
  }

  public Path errorsFile() {
    return outputDir.resolve("errors.txt");
  }

  public List<Path> artifactsForStage(PipelineStage stage) {
    return switch (stage) {
      case LEX -> List.of(tokensFile());
      case PARSE -> List.of(astFile());
      case SEMANTIC -> List.of(semanticFile());
      case IR -> List.of(irFile());
      case OPT -> List.of(irFile());
      case FRISC -> List.of(friscFile());
      case RUN -> List.of();
    };
  }
}
