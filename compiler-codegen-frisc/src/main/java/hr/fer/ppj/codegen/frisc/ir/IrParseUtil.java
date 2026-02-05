package hr.fer.ppj.codegen.frisc.ir;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared parsing utilities for the IR text parser.
 */
final class IrParseUtil {
  private IrParseUtil() {
  }

  static boolean startsWithAny(String text, String... prefixes) {
    for (String prefix : prefixes) {
      if (text.startsWith(prefix + " ") || text.equals(prefix)) {
        return true;
      }
    }
    return false;
  }

  static String[] splitByLastTypeSuffix(String text) {
    int index = lastIndexOfTypeSuffix(text);
    if (index < 0) {
      throw new CodeGenerationException("Missing type suffix in: " + text);
    }
    String expr = text.substring(0, index).trim();
    String type = text.substring(index + 3).trim();
    return new String[] { expr, type };
  }

  static List<String> splitTopLevel(String text, char delimiter) {
    List<String> parts = new ArrayList<>();
    int parenDepth = 0;
    int angleDepth = 0;
    int braceDepth = 0;
    boolean inChar = false;
    boolean escape = false;
    int start = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (inChar) {
        if (escape) {
          escape = false;
        } else if (c == '\\') {
          escape = true;
        } else if (c == '\'') {
          inChar = false;
        }
        continue;
      }
      if (c == '\'') {
        inChar = true;
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> parenDepth--;
        case '<' -> angleDepth++;
        case '>' -> angleDepth--;
        case '{' -> braceDepth++;
        case '}' -> braceDepth--;
        default -> {
        }
      }
      if (c == delimiter && parenDepth == 0 && angleDepth == 0 && braceDepth == 0) {
        parts.add(text.substring(start, i).trim());
        start = i + 1;
      }
    }
    parts.add(text.substring(start).trim());
    return parts;
  }

  static int indexOfTopLevel(String text, char target) {
    int parenDepth = 0;
    int angleDepth = 0;
    int braceDepth = 0;
    boolean inChar = false;
    boolean escape = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (inChar) {
        if (escape) {
          escape = false;
        } else if (c == '\\') {
          escape = true;
        } else if (c == '\'') {
          inChar = false;
        }
        continue;
      }
      if (c == '\'') {
        inChar = true;
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> parenDepth--;
        case '<' -> angleDepth++;
        case '>' -> angleDepth--;
        case '{' -> braceDepth++;
        case '}' -> braceDepth--;
        default -> {
        }
      }
      if (c == target && parenDepth == 0 && angleDepth == 0 && braceDepth == 0) {
        return i;
      }
    }
    return -1;
  }

  static int parseInt(String value, String context) {
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      throw new CodeGenerationException("Invalid integer for " + context + ": " + value);
    }
  }

  private static int lastIndexOfTypeSuffix(String text) {
    for (int i = text.length() - 3; i >= 0; i--) {
      if (text.charAt(i) == ' ' && text.charAt(i + 1) == ':' && text.charAt(i + 2) == ' ') {
        return i;
      }
    }
    return -1;
  }
}
