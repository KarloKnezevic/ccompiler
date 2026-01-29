package hr.fer.ppj.cli.commands;

import hr.fer.ppj.cli.args.CliOptions;

/**
 * Command that prints usage help.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class HelpCommand implements Command {

  @Override
  public int execute(CliOptions options) {
    printUsage();
    return 0;
  }

  public static void printUsage() {
    System.out.println("PPJ Compiler - Command Line Interface");
    System.out.println();
    System.out.println("USAGE:");
    System.out.println("  java -jar cli.jar <command> [options]");
    System.out.println();
    System.out.println("COMMANDS:");
    System.out.println("  lexer <file>              Lexical analysis only (output to stdout)");
    System.out.println("  syntax <file>             Lexical + syntax analysis (output to compiler-bin/)");
    System.out.println("  semantic <file>           Full compilation pipeline");
    System.out.println("  ir --in <file> [options]  Generate IR for a single file");
    System.out.println("  ir-test --golden <dir>    Run golden IR tests");
    System.out.println("  run <frisc-file>          Execute FRISC assembly");
    System.out.println("  <file>                    Full compilation (same as semantic)");
    System.out.println();
    System.out.println("IR COMMAND OPTIONS:");
    System.out.println("  --in, -i <file>           Input source file (required)");
    System.out.println("  --out, -o <dir>           Output directory (default: compiler-bin)");
    System.out.println();
    System.out.println("IR-TEST COMMAND OPTIONS:");
    System.out.println("  --golden, -g <dir>        Golden test directory (required)");
    System.out.println("  --out, -o <dir>           Output directory (default: compiler-bin)");
    System.out.println("  --recursive, -r           Recursively search for test files");
    System.out.println();
    System.out.println("EXAMPLES:");
    System.out.println("  java -jar cli.jar ir --in examples/valid/program1.c");
    System.out.println("  java -jar cli.jar ir --in examples/valid/program1.c --out build/ir");
    System.out.println("  java -jar cli.jar ir-test --golden examples/valid");
    System.out.println("  java -jar cli.jar ir-test --golden examples --recursive");
    System.out.println("  java -jar cli.jar semantic examples/valid/program1.c");
    System.out.println();
    System.out.println("OUTPUT FILES:");
    System.out.println("  compiler-bin/leksicke_jedinke.txt   - Lexical tokens");
    System.out.println("  compiler-bin/generativno_stablo.txt - Generative tree");
    System.out.println("  compiler-bin/sintaksno_stablo.txt   - Syntax tree");
    System.out.println("  compiler-bin/tablica_simbola.txt    - Symbol table");
    System.out.println("  compiler-bin/semanticko_stablo.txt  - Semantic tree");
    System.out.println("  compiler-bin/medukod.ir             - Generated IR code");
  }
}
