package hr.fer.ppj.cli.io;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Writes canonical IR text with a small metadata header.
 */
public final class IrOutputWriter {

  private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public void write(Path outputFile, String irText, Path sourceFile) throws Exception {
    Objects.requireNonNull(outputFile, "outputFile must not be null");
    Objects.requireNonNull(irText, "irText must not be null");
    Objects.requireNonNull(sourceFile, "sourceFile must not be null");

    StringBuilder sb = new StringBuilder(irText.length() + 256);
    sb.append("; Intermediate Representation (typed canonical IR)\n");
    sb.append("; Source: ").append(sourceFile.getFileName()).append("\n");
    sb.append("; Generated: ").append(LocalDateTime.now().format(TIMESTAMP)).append("\n");
    sb.append("\n");
    sb.append(irText);
    if (!irText.endsWith(System.lineSeparator())) {
      sb.append(System.lineSeparator());
    }

    Files.writeString(outputFile, sb.toString(), StandardCharsets.UTF_8);
  }
}
