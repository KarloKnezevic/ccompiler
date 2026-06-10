package hr.fer.ppj.cli;

import hr.fer.ppj.cli.args.ArgumentParser;
import hr.fer.ppj.cli.args.CliOptions;
import hr.fer.ppj.cli.ir.IrCommandRunner;
import hr.fer.ppj.cli.ir.IrExecutionResult;
import hr.fer.ppj.cli.ir.IrInterpreterOptions;
import hr.fer.ppj.cli.vm.VmCommandRunner;
import hr.fer.ppj.cli.vm.VmExecutionOptions;
import hr.fer.ppj.cli.vm.VmExecutionResult;
import hr.fer.ppj.cli.pipeline.PipelinePlan;
import hr.fer.ppj.cli.pipeline.PipelineRunner;
import hr.fer.ppj.cli.reporting.CliLoggingConfigurer;
import hr.fer.ppj.cli.reporting.ConsoleReporter;
import hr.fer.ppj.cli.reporting.HelpPrinter;

/**
 * Main CLI entrypoint for the PPJ compiler.
 */
public final class CCompilerMain {

  private CCompilerMain() {
  }

  public static void main(String[] args) {
    CliLoggingConfigurer.configure();
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

    if (options.runIrCommand()) {
      boolean success = runIrCommand(options, reporter);
      if (!success) {
        System.exit(1);
      }
      return;
    }

    if (options.runVmCommand()) {
      boolean success = runVmCommand(options, reporter);
      if (!success) {
        System.exit(1);
      }
      return;
    }

    PipelinePlan plan = PipelinePlan.from(options);
    PipelineRunner runner = new PipelineRunner(reporter);

    boolean success = runner.run(plan, options.sourceFile(), options.outputDir());
    if (!success) {
      System.exit(1);
    }
  }

  private static boolean runIrCommand(CliOptions options, ConsoleReporter reporter) {
    try {
      IrInterpreterOptions interpreterOptions =
          new IrInterpreterOptions(options.irStepLimit(), options.irTrace());
      IrExecutionResult result = new IrCommandRunner().run(options.irFile(), interpreterOptions);
      reporter.printIrExecutionResult(options.irFile(), result.returnValue(), result.steps(), result.trace());
      return true;
    } catch (Exception ex) {
      reporter.printIrExecutionFailure(options.irFile(), ex.getMessage());
      return false;
    }
  }

  private static boolean runVmCommand(CliOptions options, ConsoleReporter reporter) {
    VmCommandRunner runner = new VmCommandRunner();
    try {
      if (options.vmDisassemble()) {
        reporter.printBytecodeDisassembly(options.vmFile(), runner.disassemble(options.vmFile()));
        return true;
      }
      VmExecutionOptions vmOptions =
          new VmExecutionOptions(options.vmDispatchLimit(), options.vmTrace());
      VmExecutionResult result = runner.run(options.vmFile(), vmOptions);
      reporter.printVmExecutionResult(
          options.vmFile(), result.returnValue(), result.dispatched(), result.trace());
      return true;
    } catch (Exception ex) {
      reporter.printVmExecutionFailure(options.vmFile(), ex.getMessage());
      return false;
    }
  }
}
