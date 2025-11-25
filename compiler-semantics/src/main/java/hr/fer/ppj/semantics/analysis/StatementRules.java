package hr.fer.ppj.semantics.analysis;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.util.NodeUtils;

/**
 * Semantic rule implementations for all statement-related productions defined in
 * {@code config/semantics_definition.txt}.
 * 
 * <p>This class implements the semantic analysis for statements and control flow
 * constructs in PPJ-C, including:
 * <ul>
 *   <li>Compound statements (blocks) and statement lists</li>
 *   <li>Expression statements</li>
 *   <li>Conditional statements (if-else)</li>
 *   <li>Loop statements (while, for)</li>
 *   <li>Jump statements (break, continue, return)</li>
 * </ul>
 * 
 * <p>Each method in this class corresponds directly to one or more productions in the
 * semantic definition and implements the scope management, control flow validation,
 * and type checking rules specified there.
 * 
 * @see SemanticChecker for the main semantic analysis coordinator
 * @see ExpressionRules for expression-related semantic rules
 * @see DeclarationRules for declaration-related semantic rules
 */
final class StatementRules {

  private final SemanticChecker checker;

  StatementRules(SemanticChecker checker) {
    this.checker = checker;
    checker.registerRule("<slozena_naredba>", this::visitSlozenaNaredba);
    checker.registerRule("<lista_naredbi>", this::visitListaNaredbi);
    checker.registerRule("<naredba>", this::visitNaredba);
    checker.registerRule("<izraz_naredba>", this::visitIzrazNaredba);
    checker.registerRule("<naredba_grananja>", this::visitNaredbaGrananja);
    checker.registerRule("<naredba_petlje>", this::visitNaredbaPetlje);
    checker.registerRule("<naredba_skoka>", this::visitNaredbaSkoka);
  }

  /**
   * Implements semantic rules for {@code <slozena_naredba>} (compound statement).
   * 
   * <p>From {@code semantics_definition.txt}:
   * <pre>
   * &lt;slozena_naredba&gt; ::= L_VIT_ZAGRADA &lt;lista_naredbi&gt; D_VIT_ZAGRADA
   *                     | L_VIT_ZAGRADA &lt;lista_deklaracija&gt; &lt;lista_naredbi&gt; D_VIT_ZAGRADA
   * </pre>
   * 
   * <p>Semantic rules:
   * <ul>
   *   <li>Creates a new lexical scope for the block</li>
   *   <li>Processes declarations first (if present), then statements</li>
   *   <li>Empty blocks are allowed</li>
   * </ul>
   * 
   * @param node the {@code <slozena_naredba>} node to analyze
   */
  private void visitSlozenaNaredba(NonTerminalNode node) {
    checker.withNewScope(() -> processBlock(node));
  }

  /**
   * Processes the contents of a compound statement block.
   * 
   * <p>This method handles the different forms of compound statements:
   * <ul>
   *   <li>Empty block: {@code { }}</li>
   *   <li>Statements only: {@code { <lista_naredbi> }}</li>
   *   <li>Declarations and statements: {@code { <lista_deklaracija> <lista_naredbi> }}</li>
   * </ul>
   * 
   * <p>The method assumes it is called within the appropriate lexical scope.
   * 
   * @param node the compound statement node to process
   */
  void processBlock(NonTerminalNode node) {
    var children = node.children();
    if (children.size() < 2) {
      checker.fail(node);
    }
    if (children.size() == 2) {
      // Empty block: L_VIT_ZAGRADA D_VIT_ZAGRADA
      return;
    }
    if (children.size() == 3) {
      // Block with statements only: L_VIT_ZAGRADA <lista_naredbi> D_VIT_ZAGRADA
      checker.visitNonTerminal(NodeUtils.asNonTerminal(children.get(1)));
      return;
    }
    if (children.size() == 4) {
      // Block with declarations and statements: L_VIT_ZAGRADA <lista_deklaracija> <lista_naredbi> D_VIT_ZAGRADA
      checker.visitNonTerminal(NodeUtils.asNonTerminal(children.get(1))); // declarations
      checker.visitNonTerminal(NodeUtils.asNonTerminal(children.get(2))); // statements
      return;
    }
    checker.fail(node);
  }

  /**
   * Performs semantic analysis for the nonterminal &lt;lista_naredbi&gt;.
   *
   * <p>This method implements the semantic rules for:
   * <pre>
   * &lt;lista_naredbi&gt; ::= &lt;naredba&gt;
   * &lt;lista_naredbi&gt; ::= &lt;lista_naredbi&gt; &lt;naredba&gt;
   * </pre>
   *
   * <p>Semantic rules from semantics_definition.txt:
   * <ul>
   *   <li>provjeri(&lt;naredba&gt;) for each statement in the list</li>
   * </ul>
   *
   * @param node the &lt;lista_naredbi&gt; node to analyze
   */
  private void visitListaNaredbi(NonTerminalNode node) {
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt) {
        checker.visitNonTerminal(nt);
      }
    }
  }

  /**
   * Performs semantic analysis for the nonterminal &lt;naredba&gt;.
   *
   * <p>This method implements the semantic rules for:
   * <pre>
   * &lt;naredba&gt; ::= &lt;slozena_naredba&gt;
   * &lt;naredba&gt; ::= &lt;izraz_naredba&gt;
   * &lt;naredba&gt; ::= &lt;naredba_grananja&gt;
   * &lt;naredba&gt; ::= &lt;naredba_petlje&gt;
   * &lt;naredba&gt; ::= &lt;naredba_skoka&gt;
   * </pre>
   *
   * <p>Semantic rules from semantics_definition.txt:
   * <ul>
   *   <li>Delegates to the appropriate statement type checker</li>
   * </ul>
   *
   * @param node the &lt;naredba&gt; node to analyze
   */
  private void visitNaredba(NonTerminalNode node) {
    checker.visitNonTerminal(NodeUtils.asNonTerminal(node.children().get(0)));
  }

  /**
   * Performs semantic analysis for the nonterminal &lt;izraz_naredba&gt;.
   *
   * <p>This method implements the semantic rules for:
   * <pre>
   * &lt;izraz_naredba&gt; ::= TOCKAZAREZ
   * &lt;izraz_naredba&gt; ::= &lt;izraz&gt; TOCKAZAREZ
   * </pre>
   *
   * <p>Semantic rules from semantics_definition.txt:
   * <ul>
   *   <li>For TOCKAZAREZ: &lt;izraz_naredba&gt;.tip := int</li>
   *   <li>For &lt;izraz&gt;: provjeri(&lt;izraz&gt;), &lt;izraz_naredba&gt;.tip := &lt;izraz&gt;.tip</li>
   * </ul>
   *
   * @param node the &lt;izraz_naredba&gt; node to analyze
   */
  private void visitIzrazNaredba(NonTerminalNode node) {
    if (node.children().size() == 1) {
      // Rule: <izraz_naredba>.tip := int (for empty expression statement)
      node.attributes().type(PrimitiveType.INT);
      return;
    }
    NonTerminalNode expr = NodeUtils.asNonTerminal(node.children().get(0));
    checker.visitNonTerminal(expr);
    // Rule: <izraz_naredba>.tip := <izraz>.tip
    node.attributes().type(expr.attributes().type());
  }

  /**
   * Performs semantic analysis for the nonterminal &lt;naredba_grananja&gt;.
   *
   * <p>This method implements the semantic rules for:
   * <pre>
   * &lt;naredba_grananja&gt; ::= KR_IF L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
   * &lt;naredba_grananja&gt; ::= KR_IF L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt; KR_ELSE &lt;naredba&gt;
   * </pre>
   *
   * <p>Semantic rules from semantics_definition.txt:
   * <ul>
   *   <li>1. provjeri(&lt;izraz&gt;)</li>
   *   <li>2. &lt;izraz&gt;.tip ~ int</li>
   *   <li>3. provjeri(&lt;naredba&gt;) for both branches</li>
   * </ul>
   *
   * @param node the &lt;naredba_grananja&gt; node to analyze
   */
  private void visitNaredbaGrananja(NonTerminalNode node) {
    NonTerminalNode condition = NodeUtils.asNonTerminal(node.children().get(2));
    checker.visitNonTerminal(condition);
    // Rule 2: <izraz>.tip ~ int
    checker.ensureIntConvertible(condition.attributes().type(), node);
    // Rule 3: provjeri(<naredba>) for then branch
    checker.visitNonTerminal(NodeUtils.asNonTerminal(node.children().get(4)));
    if (node.children().size() == 7) {
      // Rule 3: provjeri(<naredba>) for else branch
      checker.visitNonTerminal(NodeUtils.asNonTerminal(node.children().get(6)));
    }
  }

  /**
   * Performs semantic analysis for the nonterminal &lt;naredba_petlje&gt;.
   *
   * <p>This method implements the semantic rules for:
   * <pre>
   * &lt;naredba_petlje&gt; ::= KR_WHILE L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
   * &lt;naredba_petlje&gt; ::= KR_FOR L_ZAGRADA &lt;izraz_naredba&gt; &lt;izraz_naredba&gt; D_ZAGRADA &lt;naredba&gt;
   * &lt;naredba_petlje&gt; ::= KR_FOR L_ZAGRADA &lt;izraz_naredba&gt; &lt;izraz_naredba&gt; &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
   * </pre>
   *
   * <p>Semantic rules from semantics_definition.txt:
   * <ul>
   *   <li>For WHILE: provjeri(&lt;izraz&gt;), &lt;izraz&gt;.tip ~ int, provjeri(&lt;naredba&gt;)</li>
   *   <li>For FOR: provjeri all expressions, condition.tip ~ int, provjeri(&lt;naredba&gt;)</li>
   *   <li>Loop body allows break/continue statements</li>
   * </ul>
   *
   * @param node the &lt;naredba_petlje&gt; node to analyze
   */
  private void visitNaredbaPetlje(NonTerminalNode node) {
    TerminalNode keyword = (TerminalNode) node.children().get(0);
    if (SemanticConstants.KR_WHILE.equals(keyword.symbol())) {
      NonTerminalNode condition = NodeUtils.asNonTerminal(node.children().get(2));
      checker.visitNonTerminal(condition);
      // Rule: <izraz>.tip ~ int
      checker.ensureIntConvertible(condition.attributes().type(), node);
      // Rule: provjeri(<naredba>) within loop context
      checker.withinLoop(() -> checker.visitNonTerminal(NodeUtils.asNonTerminal(node.children().get(4))));
      return;
    }
    if (SemanticConstants.KR_FOR.equals(keyword.symbol())) {
      visitForLoop(node);
      return;
    }
    checker.fail(node);
  }

  /**
   * Handles semantic analysis for FOR loop statements.
   *
   * <p>This method implements the semantic rules for:
   * <pre>
   * KR_FOR L_ZAGRADA &lt;izraz_naredba&gt; &lt;izraz_naredba&gt; D_ZAGRADA &lt;naredba&gt;
   * KR_FOR L_ZAGRADA &lt;izraz_naredba&gt; &lt;izraz_naredba&gt; &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
   * </pre>
   *
   * <p>Semantic rules from semantics_definition.txt:
   * <ul>
   *   <li>1. provjeri(&lt;izraz_naredba&gt;) for initialization</li>
   *   <li>2. provjeri(&lt;izraz_naredba&gt;) for condition</li>
   *   <li>3. condition.tip ~ int</li>
   *   <li>4. provjeri(&lt;izraz&gt;) for step expression (if present)</li>
   *   <li>5. provjeri(&lt;naredba&gt;) for loop body within loop context</li>
   * </ul>
   *
   * @param node the FOR loop node to analyze
   */
  private void visitForLoop(NonTerminalNode node) {
    // Rule 1: provjeri(<izraz_naredba>) for initialization
    NonTerminalNode init = NodeUtils.asNonTerminal(node.children().get(2));
    // Rule 2: provjeri(<izraz_naredba>) for condition
    NonTerminalNode condition = NodeUtils.asNonTerminal(node.children().get(3));
    checker.visitNonTerminal(init);
    checker.visitNonTerminal(condition);
    // Rule 3: condition.tip ~ int
    checker.ensureIntConvertible(condition.attributes().type(), node);
    int bodyIndex;
    if (node.children().size() == 7) {
      // Rule 4: provjeri(<izraz>) for step expression
      NonTerminalNode step = NodeUtils.asNonTerminal(node.children().get(4));
      checker.visitNonTerminal(step);
      bodyIndex = 6;
    } else {
      bodyIndex = 5;
    }
    int finalBodyIndex = bodyIndex;
    // Rule 5: provjeri(<naredba>) for loop body within loop context
    checker.withinLoop(() -> checker.visitNonTerminal(NodeUtils.asNonTerminal(node.children().get(finalBodyIndex))));
  }

  /**
   * Implements semantic rules for {@code <naredba_skoka>} (jump statements).
   * 
   * <p>From {@code semantics_definition.txt}:
   * <pre>
   * &lt;naredba_skoka&gt; ::= KR_CONTINUE TOCKAZAREZ
   *                   | KR_BREAK TOCKAZAREZ
   *                   | KR_RETURN TOCKAZAREZ
   *                   | KR_RETURN &lt;izraz&gt; TOCKAZAREZ
   * </pre>
   * 
   * <p>Semantic rules:
   * <ul>
   *   <li><strong>continue/break:</strong> Must be inside a loop (while or for)</li>
   *   <li><strong>return without expression:</strong> Only allowed in void functions</li>
   *   <li><strong>return with expression:</strong> Expression type must be assignable to function return type</li>
   * </ul>
   * 
   * @param node the {@code <naredba_skoka>} node to analyze
   */
  private void visitNaredbaSkoka(NonTerminalNode node) {
    TerminalNode keyword = (TerminalNode) node.children().get(0);
    switch (keyword.symbol()) {
      case SemanticConstants.KR_BREAK, SemanticConstants.KR_CONTINUE -> {
        // break and continue must be inside a loop
        if (checker.loopDepth() == 0) {
          checker.fail(node);
        }
      }
      case SemanticConstants.KR_RETURN -> handleReturn(node);
      default -> checker.fail(node);
    }
  }

  /**
   * Handles return statement semantic analysis.
   * 
   * <p>Validates return statements according to PPJ-C rules:
   * <ul>
   *   <li>Return statements must be inside a function</li>
   *   <li>{@code return;} is only valid in void functions</li>
   *   <li>{@code return expr;} requires expr type to be assignable to function return type</li>
   * </ul>
   * 
   * @param node the return statement node
   */
  private void handleReturn(NonTerminalNode node) {
    FunctionType current = checker.currentFunction();
    if (current == null) {
      checker.fail(node); // return outside function
    }
    
    if (node.children().size() == 2) {
      // return; (without expression)
      if (!current.isVoidReturn()) {
        checker.fail(node); // non-void function requires return value
      }
      return;
    }
    
    // return <expr>; (with expression)
    NonTerminalNode expr = NodeUtils.asNonTerminal(node.children().get(1));
    checker.visitNonTerminal(expr);
    checker.ensureAssignable(expr.attributes().type(), current.returnType(), node);
  }
}


