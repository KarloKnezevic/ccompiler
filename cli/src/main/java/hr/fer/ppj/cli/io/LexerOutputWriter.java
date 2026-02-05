package hr.fer.ppj.cli.io;

import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Token;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Writes lexer output in the expected compiler-bin format.
 */
public final class LexerOutputWriter {

  public void write(Path outputFile, List<Lexer.SymbolTableEntry> symbolTable, List<Token> tokens) throws Exception {
    Objects.requireNonNull(outputFile, "outputFile must not be null");
    Objects.requireNonNull(symbolTable, "symbolTable must not be null");
    Objects.requireNonNull(tokens, "tokens must not be null");

    StringBuilder sb = new StringBuilder();
    sb.append("tablica znakova:\n");
    sb.append("indeks   uniformni znak   izvorni tekst\n");
    for (int i = 0; i < symbolTable.size(); i++) {
      Lexer.SymbolTableEntry entry = symbolTable.get(i);
      sb.append(String.format("     %d   %-18s %s%n", i, entry.token(), entry.text()));
    }

    sb.append("\nniz uniformnih znakova:\n");
    sb.append("uniformni znak    redak    indeks u tablicu znakova\n");
    for (Token token : tokens) {
      sb.append(String.format("%-18s %5d       %d%n",
          token.type(),
          token.line(),
          token.symbolTableIndex()));
    }

    Files.writeString(outputFile, sb.toString(), StandardCharsets.UTF_8);
  }
}
