package hr.fer.ppj.codegen.frisc.ir;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Iterates over non-empty lines in the IR text.
 */
final class IrLineCursor {
  private final List<String> lines;
  private int index;

  IrLineCursor(String text) {
    this.lines = readLines(text);
    this.index = 0;
  }

  String peekNonEmptyLine() {
    int i = index;
    while (i < lines.size()) {
      String line = lines.get(i).trim();
      if (!line.isEmpty() && !isComment(line)) {
        return line;
      }
      i++;
    }
    return null;
  }

  String nextNonEmptyLine() {
    while (index < lines.size()) {
      String line = lines.get(index++).trim();
      if (!line.isEmpty() && !isComment(line)) {
        return line;
      }
    }
    return null;
  }

  private static boolean isComment(String line) {
    return line.startsWith(";");
  }

  private static List<String> readLines(String text) {
    List<String> list = new ArrayList<>();
    try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
      String line;
      while ((line = reader.readLine()) != null) {
        list.add(line);
      }
    } catch (IOException e) {
      throw new CodeGenerationException("Failed to read IR text", e);
    }
    return list;
  }
}
