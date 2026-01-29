package hr.fer.ppj.cli.commands;

import hr.fer.ppj.cli.args.CliOptions;
import hr.fer.ppj.cli.pipeline.CompilationPipeline;
import hr.fer.ppj.cli.pipeline.CompilationPipeline.CompilationResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Command for generating IR from a single source file.
 *
 * <p>Usage: {@code ir --in <source.c> [--out <outDir>]}
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class IrCommand implements Command {

  private static final String IR_OUTPUT_FILENAME = "medukod.ir";

  private final CompilationPipeline pipeline;

  public IrCommand() {
    this.pipeline = new CompilationPipeline();
  }

  public IrCommand(CompilationPipeline pipeline) {
    this.pipeline = pipeline;
  }

  @Override
  public int execute(CliOptions options) {
    Path inputFile = options.inputFile()
        .orElseThrow(() -> new IllegalArgumentException("Input file is required"));
    Path outputDir = options.outputDir();

    if (!Files.exists(inputFile)) {
      System.err.println("Error: Input file not found: " + inputFile);
      return 1;
    }

    try {
      ensureOutputDirectory(outputDir);

      System.err.println("Compiling: " + inputFile);
      CompilationResult result = pipeline.compile(inputFile);

      Path irOutputPath = outputDir.resolve(IR_OUTPUT_FILENAME);
      Files.writeString(irOutputPath, result.irString());

      System.err.println("IR generation completed: " + irOutputPath);
      return 0;

    } catch (hr.fer.ppj.semantics.errors.SemanticException e) {
      System.err.println("Error: semantic error");
      return 1;
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace(System.err);
      return 1;
    }
  }

  /**
   * Compiles a source file and returns the IR string.
   *
   * <p>This method is exposed for use by other commands (e.g., golden test runner).
   *
   * @param sourceFile the source file to compile
   * @return the generated IR string
   * @throws Exception if compilation fails
   */
  public String compileToIrString(Path sourceFile) throws Exception {
    CompilationResult result = pipeline.compile(sourceFile);
    return result.irString();
  }

  private void ensureOutputDirectory(Path outputDir) throws IOException {
    if (!Files.exists(outputDir)) {
      Files.createDirectories(outputDir);
    }
  }
}
