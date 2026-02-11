package hr.fer.ppj.cli.io;

import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Token;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Writes lexical analysis output in a stable, human-readable format.
 */
public final class LexerOutputWriter {

  public void write(Path outputFile, List<Lexer.SymbolTableEntry> symbolTable, List<Token> tokens) throws Exception {
    Objects.requireNonNull(outputFile, "outputFile must not be null");
    Objects.requireNonNull(symbolTable, "symbolTable must not be null");
    Objects.requireNonNull(tokens, "tokens must not be null");

    StringBuilder sb = new StringBuilder(4096);
    sb.append("LEXER OUTPUT\n");
    sb.append("============\n\n");

    sb.append("Token Table\n");
    sb.append("-----------\n");
    sb.append(String.format("%-8s %-24s %s%n", "Index", "Token Name", "Token Value"));
    for (int i = 0; i < symbolTable.size(); i++) {
      Lexer.SymbolTableEntry entry = symbolTable.get(i);
      sb.append(String.format("%-8d %-24s %s%n", i, entry.token(), entry.text()));
    }

    sb.append("\nUniform Token Stream\n");
    sb.append("--------------------\n");
    sb.append(String.format("%-24s %-12s %s%n", "Token Name", "Source Row", "Token Table Index"));
    for (Token token : tokens) {
      sb.append(String.format("%-24s %-12d %d%n",
          token.type(),
          token.line(),
          token.symbolTableIndex()));
    }

    Files.writeString(outputFile, sb.toString(), StandardCharsets.UTF_8);
  }
}
