package hr.fer.ppj.ir.lowering;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.lowering.stmt.StatementRouter;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for statements.
 *
 * <p>This generator handles:
 * <ul>
 *   <li>Compound statements (blocks)</li>
 *   <li>Statement lists</li>
 *   <li>Declaration lists</li>
 * </ul>
 *
 * <p>Routes individual statements to specialized generators.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class StatementGenerator {

  private final ExpressionGenerator expressionGenerator;
  private final SymbolTable globalScope;
  private final StatementRouter statementRouter;
  private final hr.fer.ppj.ir.lowering.decl.LocalDeclarationGenerator declarationGenerator;

  public StatementGenerator(
      ExpressionGenerator expressionGenerator,
      SymbolTable globalScope,
      hr.fer.ppj.ir.build.StructNameRegistry structNameRegistry) {
    this.expressionGenerator = Objects.requireNonNull(
        expressionGenerator, "expressionGenerator must not be null");
    this.globalScope = Objects.requireNonNull(globalScope, "globalScope must not be null");
    this.statementRouter = new StatementRouter(expressionGenerator, this);
    this.declarationGenerator =
        new hr.fer.ppj.ir.lowering.decl.LocalDeclarationGenerator(expressionGenerator, globalScope, structNameRegistry);
  }

  /**
   * Gets the expression generator.
   */
  public ExpressionGenerator expressionGenerator() {
    return expressionGenerator;
  }

  /**
   * Generates a statement (public method for use by other generators).
   */
  public void generateStatement(NonTerminalNode node, FunctionContext functionContext) {
    statementRouter.generateStatement(node, functionContext);
  }

  /**
   * Generates a compound statement (function body or block).
   *
   * @param node the compound statement node
   * @param functionContext the function context
   * @param isFunctionBody true if this is the function body, false for nested blocks
   */
  public void generateCompoundStatement(
      NonTerminalNode node, FunctionContext functionContext, boolean isFunctionBody) {
    Objects.requireNonNull(node, "node must not be null");
    Objects.requireNonNull(functionContext, "functionContext must not be null");

    IrFunctionBuilder builder = functionContext.functionBuilder();
    
    // Only create a new block for function body, not for nested blocks
    if (isFunctionBody) {
      builder.startNewBlock();
    } else {
      // For nested blocks, ensure we have a current block (but don't create a new one)
      if (builder.getCurrentBlockLabel() == null) {
        builder.startNewBlock();
      }
    }

    // Enter new scope for nested blocks, but not for function body
    // Note: Scope lookup is handled automatically by SymbolTable's hierarchical lookup
    // mechanism, which searches parent scopes. The functionScope in FunctionContext
    // represents the function-level scope, and nested block scopes are managed
    // by the semantic analyzer's SymbolTable structure.
    hr.fer.ppj.ir.util.VariableNameManager previousNameManager =
        functionContext.variableNameManager().snapshot();

    try {
      // Generate variable declarations first (if present)
      for (ParseNode child : node.children()) {
        if (child instanceof NonTerminalNode nt && nt.symbol().equals("<lista_deklaracija>")) {
          generateDeclarationList(nt, functionContext);
        }
      }

      // Generate statements
      if (builder.getCurrentBlockLabel() == null) {
        builder.startNewBlock();
      }

      for (ParseNode child : node.children()) {
        if (child instanceof NonTerminalNode nt && nt.symbol().equals("<lista_naredbi>")) {
          generateStatementList(nt, functionContext);
        }
      }
    } finally {
      // Restore previous scope and name mappings
      if (!isFunctionBody) {
        functionContext.variableNameManager().restore(previousNameManager);
      }
    }
  }

  private void generateDeclarationList(NonTerminalNode node, FunctionContext functionContext) {
    List<ParseNode> children = node.children();

    if (children.size() == 1) {
      ParseNode firstChild = children.get(0);
      if (firstChild instanceof NonTerminalNode nt && nt.symbol().equals("<deklaracija>")) {
        // Treat each declaration as a statement scope for rvalue cache
        functionContext.addressReuseContext().beginStatement();
        generateDeclaration(nt, functionContext, false);
        functionContext.addressReuseContext().endStatement();
      }
    } else if (children.size() == 2) {
      ParseNode firstChild = children.get(0);
      ParseNode secondChild = children.get(1);
      if (firstChild instanceof NonTerminalNode listNt && listNt.symbol().equals("<lista_deklaracija>")) {
        generateDeclarationList(listNt, functionContext);
      }
      if (secondChild instanceof NonTerminalNode declNt && declNt.symbol().equals("<deklaracija>")) {
        // Treat each declaration as a statement scope for rvalue cache
        functionContext.addressReuseContext().beginStatement();
        generateDeclaration(declNt, functionContext, false);
        functionContext.addressReuseContext().endStatement();
      }
    }
  }

  private void generateStatementList(NonTerminalNode node, FunctionContext functionContext) {
    IrFunctionBuilder builder = functionContext.functionBuilder();
    
    for (ParseNode child : node.children()) {
      // Check for unreachable code after return
      if (builder.getCurrentBlockLabel() == null) {
        List<hr.fer.ppj.ir.model.IrBlock> blocks = builder.getBlocks();
        if (!blocks.isEmpty()) {
          hr.fer.ppj.ir.model.IrBlock lastBlock = blocks.get(blocks.size() - 1);
          if (lastBlock.terminator() instanceof hr.fer.ppj.ir.model.IrTerminator.IrRetTerm) {
            continue;
          }
        }
      }

      if (child instanceof NonTerminalNode nt) {
        String symbol = nt.symbol();
        if (symbol.equals("<naredba>")) {
          if (builder.getCurrentBlockLabel() == null) {
            builder.startNewBlock();
          }
          // Begin statement scope - clears statement-local loaded values
          functionContext.addressReuseContext().beginStatement();
          generateStatement(nt, functionContext);
          // End statement scope - clears statement-local caches
          functionContext.addressReuseContext().endStatement();
        } else if (symbol.equals("<lista_naredbi>")) {
          generateStatementList(nt, functionContext);
        } else if (symbol.equals("<naredba_grananja>") || symbol.equals("<naredba_petlje>")
            || symbol.equals("<naredba_skoka>") || symbol.equals("<izraz_naredba>")
            || symbol.equals("<slozena_naredba>")) {
          if (builder.getCurrentBlockLabel() == null) {
            builder.startNewBlock();
          }
          // Begin statement scope - clears statement-local loaded values
          functionContext.addressReuseContext().beginStatement();
          generateStatement(nt, functionContext);
          // End statement scope - clears statement-local caches
          functionContext.addressReuseContext().endStatement();
        }
      }
    }
  }


  private void generateDeclaration(
      NonTerminalNode node, FunctionContext functionContext, boolean isGlobal) {
    if (isGlobal) {
      throw new UnsupportedOperationException(
          "Global declarations should be handled by GlobalGenerator");
    }
    declarationGenerator.generateDeclaration(node, functionContext);
  }
}
