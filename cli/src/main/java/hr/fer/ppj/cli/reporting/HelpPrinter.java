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
        "  --frisc       Generate FRISC (implies --lex --parse --sem --ir)",
        "  --run         Execute FRISC (implies --frisc and prior stages)",
        "  --all         Run all compile stages (lex → frisc)",
        "  --trace-ir    (run-ir) Print instruction trace",
        "  --ir-step-limit <N>  (run-ir) Max interpreter steps",
        "  --bin <dir>   Output directory (default: compiler-bin)",
        "  -h, --help    Show this help message",
        "",
        "Examples:",
        "  java -jar ccompiler --lex program.c",
        "  java -jar ccompiler --frisc program.c",
        "  java -jar ccompiler --all --run program.c",
        "  java -jar ccompiler run-ir examples/valid/basics/0006_basics_program6/program.ir",
        "",
        "Artifacts (written to compiler-bin):",
        "  leksicke_jedinke.txt",
        "  generativno_stablo.txt",
        "  sintaksno_stablo.txt",
        "  tablica_simbola.txt",
        "  semanticko_stablo.txt",
        "  medukod.ir",
        "  a.frisc",
        "",
        "Notes:",
        "  - Stages run in order and stop on first failure.",
        "  - Later stages automatically include required earlier stages."
    );
  }
}
