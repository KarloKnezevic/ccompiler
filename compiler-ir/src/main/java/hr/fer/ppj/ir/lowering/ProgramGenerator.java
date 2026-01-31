package hr.fer.ppj.ir.lowering;

import hr.fer.ppj.ir.build.StructLayoutRegistry;
import hr.fer.ppj.ir.build.StructNameRegistry;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for program-level constructs (translation units, globals, structs).
 *
 * <p>This generator handles:
 * <ul>
 *   <li>Translation unit generation</li>
 *   <li>External declaration routing</li>
 *   <li>Delegation to specialized generators</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ProgramGenerator {

  private final SymbolTable globalScope;
  private final IrProgram.Builder programBuilder;
  private final StructNameRegistry structNameRegistry;
  private final StructLayoutRegistry structLayoutRegistry;
  private final GlobalGenerator globalGenerator;
  private final StructGenerator structGenerator;
  private final FunctionGenerator functionGenerator;

  public ProgramGenerator(SymbolTable globalScope) {
    this.globalScope = Objects.requireNonNull(globalScope, "globalScope must not be null");
    this.programBuilder = IrProgram.builder();
    this.structNameRegistry = new StructNameRegistry();
    this.structLayoutRegistry = new StructLayoutRegistry(structNameRegistry);
    this.structLayoutRegistry.setProgramBuilder(programBuilder);
    this.globalGenerator = new GlobalGenerator(globalScope, programBuilder, structLayoutRegistry);
    this.structGenerator = new StructGenerator(
        programBuilder, structNameRegistry, structLayoutRegistry);

    // Create expression generator first (used by statement generator)
    ExpressionGenerator expressionGenerator = new ExpressionGenerator(
        globalScope, structNameRegistry, structLayoutRegistry);

    // Create statement generator (used by function generator)
    StatementGenerator statementGenerator = new StatementGenerator(
        expressionGenerator, globalScope, structNameRegistry);

    // Create function generator with statement generator
    this.functionGenerator = new FunctionGenerator(
        globalScope, programBuilder, statementGenerator, structLayoutRegistry);
  }

  /**
   * Generates IR from a translation unit node.
   *
   * @param translationUnit the translation unit node
   * @return the generated IR program
   */
  public IrProgram generate(NonTerminalNode translationUnit) {
    Objects.requireNonNull(translationUnit, "translationUnit must not be null");
    generateTranslationUnit(translationUnit);
    return programBuilder.build();
  }

  private void generateTranslationUnit(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    
    if (children.size() == 1) {
      ParseNode child = children.get(0);
      if (child instanceof NonTerminalNode nt && nt.symbol().equals("<vanjska_deklaracija>")) {
        generateExternalDeclaration(nt);
      }
    } else if (children.size() == 2) {
      ParseNode firstChild = children.get(0);
      ParseNode secondChild = children.get(1);
      
      if (firstChild instanceof NonTerminalNode nt1 && nt1.symbol().equals("<prijevodna_jedinica>")) {
        generateTranslationUnit(nt1);
      }
      
      if (secondChild instanceof NonTerminalNode nt2 && nt2.symbol().equals("<vanjska_deklaracija>")) {
        generateExternalDeclaration(nt2);
      }
    }
  }

  private void generateExternalDeclaration(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.isEmpty()) {
      return;
    }

    ParseNode firstChild = children.get(0);
    if (firstChild instanceof NonTerminalNode nt) {
      String symbol = nt.symbol();
      if (symbol.equals("<definicija_funkcije>")) {
        functionGenerator.generateFunctionDefinition(nt);
      } else if (symbol.equals("<deklaracija>")) {
        globalGenerator.generateDeclaration(nt);
      }
    }
  }
}
