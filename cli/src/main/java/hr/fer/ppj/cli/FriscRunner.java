package hr.fer.ppj.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Utility for executing FRISC assembly programs via the FRISCjs simulator.
 * 
 * <p>
 * The FRISC simulator outputs the decimal value of register R6 to stdout.
 */
public final class FriscRunner {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
  private static final Path SIMULATOR_PATH = Paths.get("node_modules", "friscjs", "consoleapp", "frisc-console.js");

  private final Path workingDirectory;

  public FriscRunner() {
    this(Paths.get("").toAbsolutePath());
  }

  public FriscRunner(Path workingDirectory) {
    this.workingDirectory = workingDirectory;
  }

  public Result run(Path friscFile) throws IOException, InterruptedException {
    return run(friscFile, DEFAULT_TIMEOUT);
  }

  public Result run(Path friscFile, Duration timeout) throws IOException, InterruptedException {
    Path absoluteFriscFile = workingDirectory.resolve(friscFile).normalize();
    Path simulator = workingDirectory.resolve(SIMULATOR_PATH).normalize();

    if (!Files.exists(absoluteFriscFile)) {
      return Result.failure("FRISC file not found: " + absoluteFriscFile);
    }
    if (!Files.exists(simulator)) {
      return Result.failure("FRISC simulator not found at " + simulator);
    }

    ProcessBuilder pb = new ProcessBuilder("node", simulator.toString(), absoluteFriscFile.toString());
    pb.directory(workingDirectory.toFile());
    pb.redirectErrorStream(true);

    Process process = pb.start();
    StringBuilder outputBuilder = new StringBuilder();
    Thread collector = new Thread(() -> {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          outputBuilder.append(line).append(System.lineSeparator());
        }
      } catch (IOException ignored) {
        // Swallow stream errors
      }
    });
    collector.start();

    boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    if (!finished) {
      process.destroyForcibly();
      collector.join();
      return Result.failure("Execution timed out after " + timeout.getSeconds() + " seconds",
          outputBuilder.toString());
    }

    collector.join();
    String simulatorOutput = outputBuilder.toString();

    if (process.exitValue() != 0) {
      return Result.failure("Simulator exited with code " + process.exitValue(), simulatorOutput);
    }

    // FRISC simulator outputs decimal R6 value to stdout
    // Look for a line with only a decimal number (the R6 value)
    String[] lines = simulatorOutput.split(System.lineSeparator());
    for (int i = lines.length - 1; i >= 0; i--) {
      String line = lines[i].trim();
      // Match decimal number (optionally negative)
      if (line.matches("^-?\\d+$")) {
        return Result.success(line, simulatorOutput);
      }
    }

    return Result.failure("Simulator did not report R6 register value", simulatorOutput);
  }

  public static final class Result {
    private final boolean success;
    private final String r6Value;
    private final String output;
    private final String errorMessage;

    private Result(boolean success, String r6Value, String output, String errorMessage) {
      this.success = success;
      this.r6Value = r6Value;
      this.output = output;
      this.errorMessage = errorMessage;
    }

    public static Result success(String r6Value, String output) {
      return new Result(true, r6Value, output, "");
    }

    public static Result failure(String errorMessage) {
      return new Result(false, "", "", errorMessage);
    }

    public static Result failure(String errorMessage, String output) {
      return new Result(false, "", output, errorMessage);
    }

    public boolean success() {
      return success;
    }

    public String r6Value() {
      return r6Value;
    }

    /**
     * Returns the R6 register value as an integer.
     * 
     * @return the integer value of R6, or 0 if parsing fails
     */
    public int r6ValueAsInt() {
      try {
        return Integer.parseInt(r6Value);
      } catch (NumberFormatException e) {
        return 0;
      }
    }

    /**
     * Converts the R6 register value from Q16.16 fixed-point format to float.
     * 
     * <p>
     * If the R6 value represents a Q16.16 fixed-point number (used for float return
     * values),
     * this method converts it to the actual float value by dividing by 65536.0.
     * 
     * <p>
     * Example: If R6 = 65536 (Q16.16), this returns 1.0f
     * 
     * @return the float value represented by the Q16.16 integer in R6
     */
    public float r6ValueAsFloat() {
      try {
        int q16_16 = Integer.parseInt(r6Value);
        return (float) q16_16 / 65536.0f; // Simple Q16.16 conversion
      } catch (NumberFormatException e) {
        return 0.0f;
      }
    }

    /**
     * Returns the R6 register value formatted as a float string.
     * 
     * <p>
     * This converts the Q16.16 integer value to float and formats it as a string.
     * Useful for displaying float results in test output.
     * 
     * @return the float value as a string (e.g., "3.0", "1.5", "-2.25")
     */
    public String r6ValueAsFloatString() {
      float floatValue = r6ValueAsFloat();
      // Format to remove unnecessary trailing zeros, but keep at least one decimal
      // place for floats
      if (floatValue == (int) floatValue) {
        return String.format("%.1f", floatValue);
      } else {
        return String.valueOf(floatValue);
      }
    }

    public String output() {
      return output;
    }

    public String errorMessage() {
      return errorMessage;
    }
  }
}
