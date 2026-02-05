package hr.fer.ppj.cli.io;

import hr.fer.ppj.cli.pipeline.PipelineStage;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Standard output layout for compiler-bin artifacts.
 */
public record CompilerBinLayout(Path outputDir) {

  public CompilerBinLayout {
    Objects.requireNonNull(outputDir, "outputDir must not be null");
  }

  public Path lexerOutput() {
    return outputDir.resolve("leksicke_jedinke.txt");
  }

  public Path generativeTree() {
    return outputDir.resolve("generativno_stablo.txt");
  }

  public Path syntaxTree() {
    return outputDir.resolve("sintaksno_stablo.txt");
  }

  public Path symbolTable() {
    return outputDir.resolve("tablica_simbola.txt");
  }

  public Path semanticTree() {
    return outputDir.resolve("semanticko_stablo.txt");
  }

  public Path irFile() {
    return outputDir.resolve("medukod.ir");
  }

  public Path friscFile() {
    return outputDir.resolve("a.frisc");
  }

  public List<Path> artifactsForStage(PipelineStage stage) {
    return switch (stage) {
      case LEX -> List.of(lexerOutput());
      case PARSE -> List.of(generativeTree(), syntaxTree());
      case SEMANTIC -> List.of(symbolTable(), semanticTree());
      case IR -> List.of(irFile());
      case FRISC -> List.of(friscFile());
      case RUN -> List.of();
    };
  }
}
