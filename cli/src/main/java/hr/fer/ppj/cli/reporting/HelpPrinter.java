package hr.fer.ppj.cli.reporting;

/**
 * Provides help text for the CLI.
 */
public final class HelpPrinter {

  public String render() {
    return String.join(System.lineSeparator(),
        "PPJ C Compiler CLI",
        "",
        "Usage:",
        "  java -jar ccompiler [options] <source_file.c>",
        "  java -jar ccompiler run-ir [--trace-ir] [--ir-step-limit N] <program.ir>",
        "",
        "Options:",
        "  --lex         Run lexical analysis",
        "  --parse       Run syntax analysis (implies --lex)",
        "  --sem         Run semantic analysis (implies --lex --parse)",
        "  --ir          Generate IR (implies --lex --parse --sem)",
        "  --frisc       Generate FRISC (implies --lex --parse --sem --ir --opt)",
        "  --run         Execute FRISC (implies --frisc and prior stages)",
        "  --all         Run all compile stages (lex -> frisc)",
        "  --O0          Disable IR optimization (default)",
        "  --O1          Enable O1 peephole IR optimization",
        "  --dump-ir     Dump IR before/after optimization to compiler-bin/ir-dumps/<program>/",
        "  --trace-ir    (run-ir) Print instruction trace",
        "  --ir-step-limit <N>  (run-ir) Max interpreter steps",
        "  --bin <dir>   Output directory (default: compiler-bin)",
        "  -h, --help    Show this help message",
        "",
        "Examples:",
        "  java -jar ccompiler --lex program.c",
        "  java -jar ccompiler --frisc program.c",
        "  java -jar ccompiler --frisc --O1 --dump-ir program.c",
        "  java -jar ccompiler --all --run program.c",
        "  java -jar ccompiler run-ir examples/valid/basics/0006_basics_program6/program.ir",
        "",
        "Artifacts (written to compiler-bin):",
        "  tokens.txt",
        "  ast.txt",
        "  semantic_tree.txt",
        "  intermediate.ir",
        "  a.out",
        "  errors.txt (written only on failure, as the only file in output dir)",
        "",
        "Notes:",
        "  - Stages run in order and stop on first failure.",
        "  - Later stages automatically include required earlier stages.",
        "  - O1 optimization runs between IR generation and FRISC codegen."
    );
  }
}
