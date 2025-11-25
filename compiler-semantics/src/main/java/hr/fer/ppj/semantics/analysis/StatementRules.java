package hr.fer.ppj.semantics.analysis;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.util.NodeUtils;

/**
 * Semantic rules covering statements, blocks, and control-flow constructs.
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

  private void visitSlozenaNaredba(NonTerminalNode node) {
    checker.withNewScope(() -> processBlock(node));
  }

  void processBlock(NonTerminalNode node) {
    var children = node.children();
    if (children.size() < 2) {
      checker.fail(node);
    }
    if (children.size() == 2) {
      // empty block { }
      return;
    }
    if (children.size() == 3) {
      checker.visitNonTerminal(NodeUtils.asNonTerminal(children.get(1)));
      return;
    }
    if (children.size() == 4) {
      checker.visitNonTerminal(NodeUtils.asNonTerminal(children.get(1)));
      checker.visitNonTerminal(NodeUtils.asNonTerminal(children.get(2)));
      return;
    }
    checker.fail(node);
  }

  private void visitListaNaredbi(NonTerminalNode node) {
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt) {
        checker.visitNonTerminal(nt);
      }
    }
  }

  private void visitNaredba(NonTerminalNode node) {
    checker.visitNonTerminal(NodeUtils.asNonTerminal(node.children().get(0)));
  }

  private void visitIzrazNaredba(NonTerminalNode node) {
    if (node.children().size() == 1) {
      node.attributes().type(PrimitiveType.INT);
      return;
    }
    NonTerminalNode expr = NodeUtils.asNonTerminal(node.children().get(0));
    checker.visitNonTerminal(expr);
    node.attributes().type(expr.attributes().type());
  }

  private void visitNaredbaGrananja(NonTerminalNode node) {
    NonTerminalNode condition = NodeUtils.asNonTerminal(node.children().get(2));
    checker.visitNonTerminal(condition);
    checker.ensureIntConvertible(condition.attributes().type(), node);
    checker.visitNonTerminal(NodeUtils.asNonTerminal(node.children().get(4)));
    if (node.children().size() == 7) {
      checker.visitNonTerminal(NodeUtils.asNonTerminal(node.children().get(6)));
    }
  }

  private void visitNaredbaPetlje(NonTerminalNode node) {
    TerminalNode keyword = (TerminalNode) node.children().get(0);
    if ("KR_WHILE".equals(keyword.symbol())) {
      NonTerminalNode condition = NodeUtils.asNonTerminal(node.children().get(2));
      checker.visitNonTerminal(condition);
      checker.ensureIntConvertible(condition.attributes().type(), node);
      checker.withinLoop(() -> checker.visitNonTerminal(NodeUtils.asNonTerminal(node.children().get(4))));
      return;
    }
    if ("KR_FOR".equals(keyword.symbol())) {
      visitForLoop(node);
      return;
    }
    checker.fail(node);
  }

  private void visitForLoop(NonTerminalNode node) {
    NonTerminalNode init = NodeUtils.asNonTerminal(node.children().get(2));
    NonTerminalNode condition = NodeUtils.asNonTerminal(node.children().get(3));
    checker.visitNonTerminal(init);
    checker.visitNonTerminal(condition);
    checker.ensureIntConvertible(condition.attributes().type(), node);
    int bodyIndex;
    if (node.children().size() == 7) {
      NonTerminalNode step = NodeUtils.asNonTerminal(node.children().get(4));
      checker.visitNonTerminal(step);
      bodyIndex = 6;
    } else {
      bodyIndex = 5;
    }
    int finalBodyIndex = bodyIndex;
    checker.withinLoop(() -> checker.visitNonTerminal(NodeUtils.asNonTerminal(node.children().get(finalBodyIndex))));
  }

  private void visitNaredbaSkoka(NonTerminalNode node) {
    TerminalNode keyword = (TerminalNode) node.children().get(0);
    switch (keyword.symbol()) {
      case "KR_BREAK", "KR_CONTINUE" -> {
        if (checker.loopDepth() == 0) {
          checker.fail(node);
        }
      }
      case "KR_RETURN" -> handleReturn(node);
      default -> checker.fail(node);
    }
  }

  private void handleReturn(NonTerminalNode node) {
    FunctionType current = checker.currentFunction();
    if (current == null) {
      checker.fail(node);
    }
    if (node.children().size() == 2) {
      if (!current.isVoidReturn()) {
        checker.fail(node);
      }
      return;
    }
    NonTerminalNode expr = NodeUtils.asNonTerminal(node.children().get(1));
    checker.visitNonTerminal(expr);
    checker.ensureAssignable(expr.attributes().type(), current.returnType(), node);
  }
}


