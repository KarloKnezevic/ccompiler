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
 * <p>The FRISC simulator outputs the decimal value of register R6 to stdout.
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

    public String output() {
      return output;
    }

    public String errorMessage() {
      return errorMessage;
    }
  }
}

