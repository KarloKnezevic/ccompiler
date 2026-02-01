package hr.fer.ppj.cli.test;

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
import hr.fer.ppj.semantics.errors.SemanticException;
import hr.fer.ppj.semantics.tree.NonTerminalNode;

import java.io.FileReader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TestPipeline {

    private static LexerGeneratorResult lexerResult;

    // Initialize lexer once as it might be expensive
    static {
        try {
            Path specPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
            LexerGenerator generator = new LexerGenerator();
            try (FileReader reader = new FileReader(specPath.toFile())) {
                lexerResult = generator.generate(reader);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize lexer", e);
        }
    }

    public static CompilationResult run(String sourceCode) {
        try {
            // Lexer
            Lexer lexer = new Lexer(lexerResult);
            List<Token> tokens;
            try (StringReader reader = new StringReader(sourceCode)) {
                tokens = lexer.tokenize(reader);
            }

            // Parser
            List<TokenReader.Token> parserTokens = new ArrayList<>(tokens.size());
            for (Token token : tokens) {
                parserTokens.add(new TokenReader.Token(token.type(), token.line(), token.value()));
            }
            Parser parser = new Parser();
            ParseTree parseTree = parser.parseTokens(parserTokens);

            // Semantics
            SemanticAnalyzer semanticAnalyzer = new SemanticAnalyzer();
            SemanticAnalyzer.SemanticAnalysisResult semanticResult = semanticAnalyzer.analyzeWithResults(parseTree,
                    System.err, null);
            NonTerminalNode semanticTree = semanticResult.parseTree();

            // IR Generation
            IrProgram irProgram = IrPipeline.generate(semanticResult.globalScope(), semanticTree);
            String irString = IrPipeline.print(irProgram);

            return new CompilationResult(true, irString, null);

        } catch (SemanticException e) {
            return new CompilationResult(false, null, "ERROR: Semantic error - " + e.getMessage());
        } catch (Exception e) {
            return new CompilationResult(false, null, "ERROR: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    public static class CompilationResult {
        public final boolean success;
        public final String ir;
        public final String error;

        public CompilationResult(boolean success, String ir, String error) {
            this.success = success;
            this.ir = ir;
            this.error = error;
        }
    }
}
