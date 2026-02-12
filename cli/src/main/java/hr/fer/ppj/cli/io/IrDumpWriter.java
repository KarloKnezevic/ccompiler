package hr.fer.ppj.cli.io;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Writes pre/post optimization IR snapshots.
 */
public final class IrDumpWriter {

  public List<Path> write(Path outputDir, Path sourceFile, String preOptimization, String postOptimization)
      throws Exception {
    Objects.requireNonNull(outputDir, "outputDir must not be null");
    Objects.requireNonNull(sourceFile, "sourceFile must not be null");
    Objects.requireNonNull(preOptimization, "preOptimization must not be null");
    Objects.requireNonNull(postOptimization, "postOptimization must not be null");

    String sourceName = sourceFile.getFileName().toString();
    int dot = sourceName.lastIndexOf('.');
    String programName = dot > 0 ? sourceName.substring(0, dot) : sourceName;

    Path dumpDir = outputDir.resolve("ir-dumps").resolve(programName);
    Files.createDirectories(dumpDir);

    Path preFile = dumpDir.resolve("before_optimization.ir");
    Path postFile = dumpDir.resolve("after_optimization.ir");

    Files.writeString(preFile, preOptimization, StandardCharsets.UTF_8);
    Files.writeString(postFile, postOptimization, StandardCharsets.UTF_8);

    List<Path> dumps = new ArrayList<>(2);
    dumps.add(preFile);
    dumps.add(postFile);
    return dumps;
  }
}
