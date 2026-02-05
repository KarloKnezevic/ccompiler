package hr.fer.ppj.cli;

import hr.fer.ppj.cli.args.ArgumentParser;
import hr.fer.ppj.cli.args.CliOptions;
import hr.fer.ppj.cli.pipeline.PipelinePlan;
import hr.fer.ppj.cli.pipeline.PipelineRunner;
import hr.fer.ppj.cli.reporting.ConsoleReporter;
import hr.fer.ppj.cli.reporting.HelpPrinter;

/**
 * Main CLI entrypoint for the PPJ compiler.
 */
public final class CCompilerMain {

  private CCompilerMain() {
  }

  public static void main(String[] args) {
    ConsoleReporter reporter = new ConsoleReporter();
    ArgumentParser parser = new ArgumentParser();
    ArgumentParser.ParseResult parseResult = parser.parse(args);

    if (!parseResult.success()) {
      reporter.printArgumentError(parseResult.error());
      reporter.printHelp(new HelpPrinter().render());
      System.exit(1);
      return;
    }

    CliOptions options = parseResult.options();
    if (options.help()) {
      reporter.printHelp(new HelpPrinter().render());
      return;
    }

    PipelinePlan plan = PipelinePlan.from(options);
    PipelineRunner runner = new PipelineRunner(reporter);

    boolean success = runner.run(plan, options.sourceFile(), options.outputDir());
    if (!success) {
      System.exit(1);
    }
  }
}
