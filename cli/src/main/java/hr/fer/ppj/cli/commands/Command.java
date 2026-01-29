package hr.fer.ppj.cli.commands;

import hr.fer.ppj.cli.args.CliOptions;

/**
 * Interface for CLI commands.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public interface Command {

  /**
   * Executes the command with the given options.
   *
   * @param options the parsed CLI options
   * @return the exit code (0 for success, non-zero for failure)
   */
  int execute(CliOptions options);
}
