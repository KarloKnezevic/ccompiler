package hr.fer.ppj.cli;

import hr.fer.ppj.ir.IrPipeline;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Token;
import hr.fer.ppj.parser.Parser;
import hr.fer.ppj.parser.io.TokenReader;
import hr.fer.ppj.parser.tree.ParseTree;
import hr.fer.ppj.semantics.analysis.SemanticAnalyzer;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Command-line tool to generate IR from a C source file.
 * 
 * <p>Usage: java IrGenerator <input.c> [output.ir]
 * If output.ir is not specified, writes to stdout.
 */
public final class IrGenerator {

  public static void main(String[] args) {
    if (args.length < 1) {
      System.err.println("Usage: IrGenerator <input.c> [output.ir]");
      System.exit(1);
    }

    String inputFile = args[0];
    String outputFile = args.length > 1 ? args[1] : null;

    try {
      Path inputPath = Paths.get(inputFile);
      if (!Files.exists(inputPath)) {
        System.err.println("Error: File not found: " + inputFile);
        System.exit(1);
      }

      // Run lexical analysis
      Path specPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
      LexerGenerator generator = new LexerGenerator();
      LexerGeneratorResult lexerResult;
      try (FileReader reader = new FileReader(specPath.toFile())) {
        lexerResult = generator.generate(reader);
      }
      
      Lexer lexer = new Lexer(lexerResult);
      List<Token> tokens;
      try (Reader reader = new FileReader(inputPath.toFile())) {
        tokens = lexer.tokenize(reader);
      }

      // Run parser
      List<TokenReader.Token> parserTokens = new ArrayList<>(tokens.size());
      for (Token token : tokens) {
        parserTokens.add(new TokenReader.Token(token.type(), token.line(), token.value()));
      }
      Parser parser = new Parser();
      ParseTree parseTree = parser.parseTokens(parserTokens);

      // Run semantic analysis
      SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
      SemanticAnalyzer.SemanticAnalysisResult semanticResult;
      try {
        semanticResult = semanticAnalyzer.analyzeWithResults(parseTree, System.err, null);
        NonTerminalNode semanticTree = semanticResult.parseTree();
        
        // Generate IR
        IrProgram irProgram = IrPipeline.generate(semanticResult.globalScope(), semanticTree);
        String irString = IrPipeline.print(irProgram);
        
        // Write output
        if (outputFile != null) {
          Files.writeString(Paths.get(outputFile), irString);
        } else {
          System.out.print(irString);
        }
      } catch (hr.fer.ppj.semantics.errors.SemanticException e) {
        String errorMsg = "ERROR: Semantic error - " + e.getMessage();
        if (outputFile != null) {
          Files.writeString(Paths.get(outputFile), errorMsg + "\n");
        } else {
          System.err.println(errorMsg);
        }
        System.exit(1);
      }
    } catch (Exception e) {
      String errorMsg = "ERROR: " + e.getMessage();
      if (outputFile != null) {
        try {
          Files.writeString(Paths.get(outputFile), errorMsg + "\n");
        } catch (IOException ioException) {
          System.err.println("Failed to write error to file: " + ioException.getMessage());
        }
      } else {
        System.err.println(errorMsg);
      }
      e.printStackTrace(System.err);
      System.exit(1);
    }
  }
}

