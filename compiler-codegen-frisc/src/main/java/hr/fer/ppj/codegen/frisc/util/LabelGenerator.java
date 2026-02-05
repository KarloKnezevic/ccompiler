package hr.fer.ppj.codegen.frisc.util;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public final class LabelGenerator {

  private final AtomicInteger counter = new AtomicInteger();

  public String functionLabel(String name) {
    return "F_" + toUpper(name);
  }

  public String globalLabel(String name) {
    return "G_" + toUpper(name);
  }

  public String blockLabel(String functionName, String blockLabel) {
    return "L_" + toUpper(functionName) + "_" + blockLabel;
  }

  public String newLabel(String prefix) {
    return prefix + "_" + counter.incrementAndGet();
  }

  private String toUpper(String name) {
    return name.toUpperCase(Locale.ROOT);
  }
}
