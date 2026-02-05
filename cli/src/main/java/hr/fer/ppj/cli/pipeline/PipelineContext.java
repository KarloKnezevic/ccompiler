package hr.fer.ppj.cli.pipeline;

import hr.fer.ppj.cli.io.CompilerBinLayout;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Token;
import hr.fer.ppj.parser.tree.ParseTree;
import hr.fer.ppj.semantics.analysis.SemanticAnalyzer;
import java.nio.file.Path;
import java.util.List;

/**
 * Shared context for pipeline stages.
 */
final class PipelineContext {

  private final Path sourceFile;
  private final CompilerBinLayout layout;

  private List<Token> tokens;
  private List<Lexer.SymbolTableEntry> symbolTable;
  private ParseTree parseTree;
  private SemanticAnalyzer.SemanticAnalysisResult semanticResult;
  private IrProgram irProgram;
  private String irText;
  private Path friscFile;

  PipelineContext(Path sourceFile, CompilerBinLayout layout) {
    this.sourceFile = sourceFile;
    this.layout = layout;
  }

  Path sourceFile() {
    return sourceFile;
  }

  CompilerBinLayout layout() {
    return layout;
  }

  List<Token> tokens() {
    return tokens;
  }

  void tokens(List<Token> tokens) {
    this.tokens = tokens;
  }

  List<Lexer.SymbolTableEntry> symbolTable() {
    return symbolTable;
  }

  void symbolTable(List<Lexer.SymbolTableEntry> symbolTable) {
    this.symbolTable = symbolTable;
  }

  ParseTree parseTree() {
    return parseTree;
  }

  void parseTree(ParseTree parseTree) {
    this.parseTree = parseTree;
  }

  SemanticAnalyzer.SemanticAnalysisResult semanticResult() {
    return semanticResult;
  }

  void semanticResult(SemanticAnalyzer.SemanticAnalysisResult semanticResult) {
    this.semanticResult = semanticResult;
  }

  IrProgram irProgram() {
    return irProgram;
  }

  void irProgram(IrProgram irProgram) {
    this.irProgram = irProgram;
  }

  String irText() {
    return irText;
  }

  void irText(String irText) {
    this.irText = irText;
  }

  Path friscFile() {
    return friscFile;
  }

  void friscFile(Path friscFile) {
    this.friscFile = friscFile;
  }
}
