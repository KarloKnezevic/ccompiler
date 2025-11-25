package hr.fer.ppj.semantics.io;

import hr.fer.ppj.parser.io.TokenReader;
import hr.fer.ppj.parser.io.TokenReader.Token;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility for reading lexer output in PPJ format and converting it to {@link SemanticToken}s. The
 * format matches {@code leksicke_jedinke.txt} where each token is already enriched with line and
 * symbol-table indices. The semantic analyzer only needs the {@code type}, {@code line}, and lexeme
 * so the conversion keeps just those fields.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LexicalTokenReader {

  /**
   * Reads lexer output from the provided path.
   *
   * @param path path to {@code leksicke_jedinke.txt}
   * @return ordered list of tokens
   * @throws IOException if the file cannot be read
   */
  public List<SemanticToken> read(Path path) throws IOException {
    try (Reader reader = Files.newBufferedReader(path)) {
      return read(reader);
    }
  }

  /**
   * Reads lexer output from the provided reader.
   */
  public List<SemanticToken> read(Reader reader) throws IOException {
    TokenReader delegate = new TokenReader();
    List<Token> tokens = delegate.readTokens(reader);
    List<SemanticToken> result = new ArrayList<>(tokens.size());
    for (Token token : tokens) {
      result.add(new SemanticToken(token.type(), token.line(), token.lexicalUnit()));
    }
    return result;
  }
}

