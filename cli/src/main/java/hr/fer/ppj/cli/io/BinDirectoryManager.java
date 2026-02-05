package hr.fer.ppj.cli.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages compiler-bin cleanup and creation.
 */
public final class BinDirectoryManager {

  public void prepare(Path outputDir) throws IOException {
    Objects.requireNonNull(outputDir, "outputDir must not be null");
    clearDirectory(outputDir);
    Files.createDirectories(outputDir);
  }

  private void clearDirectory(Path outputDir) throws IOException {
    if (!Files.exists(outputDir)) {
      return;
    }

    Path normalized = outputDir.toAbsolutePath().normalize();
    if (normalized.getParent() == null) {
      throw new IOException("Refusing to clear root directory: " + normalized);
    }

    try (Stream<Path> walk = Files.walk(outputDir)) {
      List<Path> paths = walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
      for (Path path : paths) {
        if (Files.isDirectory(path)) {
          if (!path.equals(outputDir)) {
            Files.deleteIfExists(path);
          }
        } else {
          Files.deleteIfExists(path);
        }
      }
    }
  }
}
