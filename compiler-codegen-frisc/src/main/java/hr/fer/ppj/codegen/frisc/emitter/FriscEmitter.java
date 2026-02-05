package hr.fer.ppj.codegen.frisc.emitter;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class FriscEmitter {

  private static final int INDENT_WIDTH = 8;
  private static final int COMMENT_COLUMN = 32;

  private final List<String> lines = new ArrayList<>();
  private boolean inDataSection = false;
  private int dataOffset = 0;

  private boolean needsMul;
  private boolean needsDiv;
  private boolean needsMod;
  private boolean needsFmul;
  private boolean needsFdiv;
  private boolean needsF2i;
  private boolean needsI2f;
  private boolean needsBoundsCheck;

  public void beginDataSection() {
    inDataSection = true;
    dataOffset = 0;
  }

  public void endDataSection() {
    inDataSection = false;
  }

  public void emitComment(String comment) {
    Objects.requireNonNull(comment, "comment must not be null");
    lines.add("; " + comment);
  }

  public void emitSectionHeader(String title) {
    emitComment("===========================================================================");
    emitComment(title);
    emitComment("===========================================================================");
  }

  public void emitLabel(String label, String comment) {
    Objects.requireNonNull(label, "label must not be null");
    String base = label;
    lines.add(formatWithComment(base, comment));
  }

  public void emitInstruction(String mnemonic, List<String> operands, String comment) {
    Objects.requireNonNull(mnemonic, "mnemonic must not be null");
    Objects.requireNonNull(operands, "operands must not be null");

    StringBuilder sb = new StringBuilder();
    sb.append(" ".repeat(INDENT_WIDTH)).append(mnemonic);
    if (!operands.isEmpty()) {
      sb.append(' ');
      for (int i = 0; i < operands.size(); i++) {
        if (i > 0) {
          sb.append(", ");
        }
        sb.append(operands.get(i));
      }
    }
    lines.add(formatWithComment(sb.toString(), comment));
  }

  public void emitRaw(String line) {
    lines.add(line);
  }

  public void emitData(String label, String directive, String value, String comment, int sizeBytes, int alignment) {
    Objects.requireNonNull(directive, "directive must not be null");
    Objects.requireNonNull(value, "value must not be null");

    if (inDataSection) {
      alignData(alignment);
    }

    String base;
    if (label != null && !label.isBlank()) {
      base = label + " " + directive + " " + value;
    } else {
      base = " ".repeat(INDENT_WIDTH) + directive + " " + value;
    }
    lines.add(formatWithComment(base, comment));

    if (inDataSection && sizeBytes > 0) {
      dataOffset += sizeBytes;
    }
  }

  public void emitPadding(int sizeBytes, String comment) {
    if (sizeBytes <= 0) {
      return;
    }
    String base = " ".repeat(INDENT_WIDTH) + "`DS " + sizeBytes;
    lines.add(formatWithComment(base, comment));
    if (inDataSection) {
      dataOffset += sizeBytes;
    }
  }

  private void alignData(int alignment) {
    if (alignment <= 1) {
      return;
    }
    int mod = dataOffset % alignment;
    if (mod == 0) {
      return;
    }
    int padding = alignment - mod;
    emitPadding(padding, "align " + alignment);
  }

  private String formatWithComment(String base, String comment) {
    if (comment == null || comment.isBlank()) {
      return base;
    }
    StringBuilder sb = new StringBuilder(base);
    if (sb.length() < COMMENT_COLUMN) {
      sb.append(" ".repeat(COMMENT_COLUMN - sb.length()));
    } else {
      sb.append(' ');
    }
    sb.append("; ").append(comment);
    return sb.toString();
  }

  public void markMulNeeded() {
    needsMul = true;
  }

  public void markDivNeeded() {
    needsDiv = true;
  }

  public void markModNeeded() {
    needsMod = true;
  }

  public void markFmulNeeded() {
    needsFmul = true;
  }

  public void markFdivNeeded() {
    needsFdiv = true;
  }

  public void markF2iNeeded() {
    needsF2i = true;
  }

  public void markI2fNeeded() {
    needsI2f = true;
  }

  public void markBoundsCheckNeeded() {
    needsBoundsCheck = true;
  }

  public boolean needsMul() {
    return needsMul;
  }

  public boolean needsDiv() {
    return needsDiv;
  }

  public boolean needsMod() {
    return needsMod;
  }

  public boolean needsFmul() {
    return needsFmul;
  }

  public boolean needsFdiv() {
    return needsFdiv;
  }

  public boolean needsF2i() {
    return needsF2i;
  }

  public boolean needsI2f() {
    return needsI2f;
  }

  public boolean needsBoundsCheck() {
    return needsBoundsCheck;
  }

  public void writeToFile(Path path) {
    Objects.requireNonNull(path, "path must not be null");
    try {
      Files.createDirectories(path.getParent());
      Files.write(path, lines);
    } catch (IOException e) {
      throw new CodeGenerationException("Failed to write FRISC output to " + path, e);
    }
  }

  public List<String> lines() {
    return List.copyOf(lines);
  }
}
