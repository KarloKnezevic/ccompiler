package hr.fer.ppj.codegen.stmt;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates FRISC assembly code generation for all statement types.
 *
 * <p>This class serves as the main dispatcher for statement code generation, delegating to
 * specialized generators based on the statement type. It implements a hierarchical visitor pattern
 * that matches the C statement grammar structure.
 *
 * <p><b>Design Pattern: Dispatcher/Visitor</b>
 *
 * <p>This class implements the <b>dispatcher pattern</b> (also known as the visitor pattern for
 * statements), where:
 *
 * <ul>
 *   <li>The main method ({@code generateStatement}) dispatches to specialized generators based on
 *       the statement type
 *   <li>Each statement type has its own generator class that handles the specific code generation
 *       logic
 *   <li>The dispatcher maintains the overall control flow and context
 * </ul>
 *
 * <p><b>Statement Types Handled:</b>
 *
 * <p>This class handles the generation of code for all types of statements in ppjC:
 *
 * <ul>
 *   <li><b>Expression Statements:</b> {@code expression;} - evaluates expression, discards result
 *   <li><b>Compound Statements:</b> {@code { statements } } - blocks with local scope
 *   <li><b>Conditional Statements:</b> {@code if (condition) statement [else statement]} -
 *       delegated to {@link BranchingStatementGenerator}
 *   <li><b>Loop Statements:</b> {@code while (condition) statement} and {@code for (init;
 *       condition; increment) statement} - delegated to {@link LoopStatementGenerator}
 *   <li><b>Jump Statements:</b> {@code return [expression];}, {@code break;}, {@code continue;} -
 *       delegated to {@link JumpStatementGenerator}
 *   <li><b>Local Declarations:</b> Variable declarations within compound statements - delegated to
 *       {@link LocalDeclarationGenerator}
 * </ul>
 *
 * <p><b>Grammar Rules Handled:</b>
 *
 * <pre>
 * &lt;naredba&gt; ::= &lt;slozena_naredba&gt;
 *            | &lt;izraz_naredba&gt;
 *            | &lt;naredba_grananja&gt;
 *            | &lt;naredba_petlje&gt;
 *            | &lt;naredba_skoka&gt;
 *
 * &lt;slozena_naredba&gt; ::= L_VIT_ZAGRADA &lt;lista_deklaracija&gt; &lt;lista_naredbi&gt; D_VIT_ZAGRADA
 *
 * &lt;izraz_naredba&gt; ::= &lt;izraz&gt; TOCKAZAREZ
 *                   | TOCKAZAREZ
 *
 * &lt;naredba_grananja&gt; ::= KR_IF L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
 *                        | KR_IF L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt; KR_ELSE &lt;naredba&gt;
 *
 * &lt;naredba_petlje&gt; ::= KR_WHILE L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
 *                    | KR_FOR L_ZAGRADA &lt;izraz_naredba&gt; &lt;izraz_naredba&gt; D_ZAGRADA &lt;naredba&gt;
 *                    | KR_FOR L_ZAGRADA &lt;izraz_naredba&gt; &lt;izraz_naredba&gt; &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
 *
 * &lt;naredba_skoka&gt; ::= KR_RETURN &lt;izraz&gt; TOCKAZAREZ
 *                    | KR_RETURN TOCKAZAREZ
 *                    | KR_BREAK TOCKAZAREZ
 *                    | KR_CONTINUE TOCKAZAREZ
 * </pre>
 *
 * <p><b>Algorithm: Statement Code Generation</b>
 *
 * <p>The statement generation algorithm works as follows:
 *
 * <ol>
 *   <li><b>Statement Type Identification:</b> Identify the statement type from the nonterminal
 *       symbol
 *   <li><b>Delegation:</b> Delegate to the appropriate specialized generator
 *   <li><b>Context Propagation:</b> Pass the code generation context (with loop labels, function
 *       exit labels, etc.) to specialized generators
 *   <li><b>Control Flow Management:</b> Specialized generators handle labels and jumps for their
 *       specific control flow patterns
 * </ol>
 *
 * <p><b>Compound Statement Algorithm:</b>
 *
 * <p>Compound statements (blocks) are handled as follows:
 *
 * <ol>
 *   <li><b>Process Local Declarations:</b> Generate code for local variable declarations (allocates
 *       stack space, handles initializers)
 *   <li><b>Process Statement List:</b> Generate code for each statement in the block sequentially
 *   <li><b>Scope Management:</b> Local variables are automatically scoped to the block (handled by
 *       activation record)
 * </ol>
 *
 * <p><b>Expression Statement Algorithm:</b>
 *
 * <p>Expression statements are handled as follows:
 *
 * <ol>
 *   <li><b>Expression Evaluation:</b> Generate code to evaluate the expression
 *   <li><b>Result Discard:</b> The result is left in R0 but not used (side effects of the
 *       expression are preserved)
 * </ol>
 *
 * <p><b>Context Management:</b>
 *
 * <p>This class manages the code generation context for statements:
 *
 * <ul>
 *   <li><b>Loop Context:</b> When entering a loop, a new context with loop labels is created and
 *       passed to the loop generator
 *   <li><b>Function Context:</b> When in a function, the context includes the activation record and
 *       function exit label
 *   <li><b>Nested Contexts:</b> Nested loops and conditionals create nested contexts with their own
 *       labels
 * </ul>
 *
 * <p><b>FRISC Code Pattern (Expression Statement):</b>
 *
 * <pre>
 * ; Expression statement: x = y + z;
 * ... (evaluate y + z, result in R0) ...
 * ... (store result to x) ...
 * ; Result is discarded (side effects preserved)
 * </pre>
 *
 * <p><b>FRISC Code Pattern (Compound Statement):</b>
 *
 * <pre>
 * ; Compound statement: { int x = 5; x++; }
 * ; Local declaration: allocate x on stack, initialize to 5
 * SUB R7, %D 4, R7          ; allocate space for x
 * MOVE %D 5, R0
 * STORE R0, (R5-04)         ; x = 5
 * ; Expression statement: x++
 * LOAD R0, (R5-04)          ; load x
 * ADD R0, %D 1, R0          ; increment
 * STORE R0, (R5-04)         ; store back
 * </pre>
 *
 * <p><b>Complexity Analysis:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(n) where n is the number of statements (each statement is
 *       processed once)
 *   <li><b>Space Complexity:</b> O(1) - uses only a few generator objects and context
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class StatementCodeGenerator {

  private final CodeGenContext context;
  private final ExpressionCodeGenerator exprGen;
  private final BranchingStatementGenerator branchingGen;
  private final LoopStatementGenerator loopGen;
  private final JumpStatementGenerator jumpGen;
  private final LocalDeclarationGenerator localDeclGen;

  /**
   * Creates a new statement code generator.
   *
   * <p>Initializes specialized generators for different statement types:
   *
   * <ul>
   *   <li>Branching statements (if-else)
   *   <li>Loop statements (while, for)
   *   <li>Jump statements (return, break, continue)
   *   <li>Local declarations
   * </ul>
   *
   * @param context the code generation context
   */
  public StatementCodeGenerator(CodeGenContext context) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.exprGen = new ExpressionCodeGenerator(context);
    this.branchingGen = new BranchingStatementGenerator(context, exprGen, this);
    this.loopGen = new LoopStatementGenerator(context, exprGen, this);
    this.jumpGen = new JumpStatementGenerator(context, exprGen);
    this.localDeclGen = new LocalDeclarationGenerator(context, exprGen);
  }

  /**
   * Sets the parse tree for extracting struct array sizes.
   *
   * <p>This propagates the parse tree to ExpressionCodeGenerator and other generators that need it
   * for handling nested struct field access with arrays.
   *
   * @param parseTree the parse tree from semantic analysis
   */
  public void setParseTree(NonTerminalNode parseTree) {
    if (exprGen != null) {
      exprGen.setParseTree(parseTree);
    }
    // JumpStatementGenerator uses ReturnStatementGenerator which has its own LValueAddressGenerator
    // ReturnStatementGenerator.setParseTree is called via ExpressionCodeGenerator
  }

  /**
   * Generates code for a statement.
   *
   * @param statement the statement node to generate code for
   */
  public void generateStatement(NonTerminalNode statement) {
    Objects.requireNonNull(statement, "statement must not be null");

    String symbol = statement.symbol();

    switch (symbol) {
      case "<naredba>" -> {
        // Delegate to the specific statement type
        NonTerminalNode child = (NonTerminalNode) statement.children().get(0);
        generateStatement(child);
      }
      case "<slozena_naredba>" -> generateCompoundStatement(statement);
      case "<izraz_naredba>" -> generateExpressionStatement(statement);
      case "<naredba_grananja>" -> branchingGen.generateBranchingStatement(statement);
      case "<naredba_petlje>" -> loopGen.generateLoopStatement(statement);
      case "<naredba_skoka>" -> jumpGen.generateJumpStatement(statement);
      default -> throw new IllegalArgumentException("Unknown statement type: " + symbol);
    }
  }

  /** Generates code for compound statements (blocks). */
  private void generateCompoundStatement(NonTerminalNode node) {
    List<ParseNode> children = node.children();

    context.emitter().emitComment("Compound statement");

    // Process all statements in the block
    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nonTerminal) {
        String symbol = nonTerminal.symbol();

        if ("<lista_naredbi>".equals(symbol)) {
          generateStatementList(nonTerminal);
        } else if ("<lista_deklaracija>".equals(symbol)) {
          // Handle local variable declarations
          localDeclGen.generateLocalDeclarations(nonTerminal);
        }
      }
    }
  }

  /** Generates code for a list of statements. */
  private void generateStatementList(NonTerminalNode node) {
    String symbol = node.symbol();

    if ("<lista_naredbi>".equals(symbol)) {
      List<ParseNode> children = node.children();

      for (ParseNode child : children) {
        if (child instanceof NonTerminalNode nonTerminal) {
          String childSymbol = nonTerminal.symbol();
          if ("<lista_naredbi>".equals(childSymbol)) {
            // Recursive case for nested statement lists
            generateStatementList(nonTerminal);
          } else if ("<naredba>".equals(childSymbol)) {
            // Use this instance to maintain context
            generateStatement(nonTerminal);
          }
        }
      }
    }
  }

  /** Generates code for expression statements. */
  private void generateExpressionStatement(NonTerminalNode node) {
    List<ParseNode> children = node.children();

    if (children.size() == 2) {
      // <izraz> TOCKAZAREZ
      NonTerminalNode expression = (NonTerminalNode) children.get(0);
      exprGen.generateExpression(expression);
      // Result is discarded (expression statement)
    }
    // If only TOCKAZAREZ, it's an empty statement - no code needed
  }
}
