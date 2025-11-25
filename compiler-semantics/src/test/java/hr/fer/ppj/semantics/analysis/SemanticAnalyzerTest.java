package hr.fer.ppj.semantics.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;

import hr.fer.ppj.semantics.io.GenerativeTreeParser;
import hr.fer.ppj.semantics.io.LexicalTokenReader;
import hr.fer.ppj.semantics.io.SemanticToken;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Golden tests that replay the semantic analyser on top of parser outputs.
 */
final class SemanticAnalyzerTest {

  private static Stream<Arguments> provideCases() throws IOException {
    Path root = Paths.get("src/test/resources");
    try (Stream<Path> stream = Files.list(root)) {
      return stream
          .filter(Files::isDirectory)
          .filter(
              path ->
                  path.getFileName().toString().startsWith("ppjc_case_")
                      && Files.exists(path.resolve("generativno_stablo.txt"))
                      && Files.exists(path.resolve("leksicke_jedinke.txt")))
          .sorted(Comparator.comparing(path -> path.getFileName().toString()))
          .map(path -> Arguments.of(path.getFileName().toString(), path))
          .toList()
          .stream();
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("provideCases")
  void semanticGoldenCases(String name, Path caseRoot) throws Exception {
    Path tokensPath = caseRoot.resolve("leksicke_jedinke.txt");
    Path treePath = caseRoot.resolve("generativno_stablo.txt");
    Path expectedPath = caseRoot.resolve("semantic.txt");

    List<SemanticToken> tokens;
    try (var reader = Files.newBufferedReader(tokensPath, StandardCharsets.UTF_8)) {
      tokens = new LexicalTokenReader().read(reader);
    }

    NonTerminalNode root;
    try (var reader = Files.newBufferedReader(treePath, StandardCharsets.UTF_8)) {
      root = GenerativeTreeParser.parse(reader, tokens);
    }

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    SemanticAnalyzer analyzer = new SemanticAnalyzer();
    try {
      analyzer.analyze(root, new PrintStream(buffer, true, StandardCharsets.UTF_8));
    } catch (SemanticException ignored) {
      // Expected for negative golden cases; output is still captured in the buffer.
    }

    String actual = buffer.toString(StandardCharsets.UTF_8);
    String expected = Files.readString(expectedPath, StandardCharsets.UTF_8);
    assertEquals(expected, actual, () -> "Mismatch for case " + name);
  }
}

