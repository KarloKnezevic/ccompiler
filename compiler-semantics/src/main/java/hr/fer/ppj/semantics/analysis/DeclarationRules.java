package hr.fer.ppj.semantics.analysis;

import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.ConstType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates all grammar productions related to declarations, definitions, and initialization.
 */
final class DeclarationRules {

  private final SemanticChecker checker;

  DeclarationRules(SemanticChecker checker) {
    this.checker = checker;
    checker.registerRule("<prijevodna_jedinica>", this::visitPrijevodnaJedinica);
    checker.registerRule("<vanjska_deklaracija>", this::visitVanjskaDeklaracija);
    checker.registerRule("<definicija_funkcije>", this::visitDefinicijaFunkcije);
    checker.registerRule("<deklaracija>", this::visitDeklaracija);
    checker.registerRule("<lista_deklaracija>", this::visitListaDeklaracija);
    checker.registerRule("<lista_init_deklaratora>", this::visitListaInitDeklaratora);
    checker.registerRule("<init_deklarator>", this::visitInitDeklarator);
    checker.registerRule("<izravni_deklarator>", this::visitIzravniDeklarator);
    checker.registerRule("<deklarator>", this::visitDeklarator);
    checker.registerRule("<ime_tipa>", this::visitImeTipa);
    checker.registerRule("<specifikatori_deklaracije>", this::visitSpecifikatoriDeklaracije);
    checker.registerRule("<specifikator_tipa>", this::visitSpecifikatorTipa);
    checker.registerRule("<lista_parametara>", this::visitListaParametara);
    checker.registerRule("<deklaracija_parametra>", this::visitDeklaracijaParametra);
    checker.registerRule("<inicijalizator>", this::visitInicijalizator);
    checker.registerRule("<lista_izraza_pridruzivanja>", this::visitListaIzrazaPridruzivanja);
  }

  private void visitPrijevodnaJedinica(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.size() == 1) {
      checker.visitNonTerminal(NodeUtils.asNonTerminal(children.get(0)));
    } else if (children.size() == 2) {
      checker.visitNonTerminal(NodeUtils.asNonTerminal(children.get(0)));
      checker.visitNonTerminal(NodeUtils.asNonTerminal(children.get(1)));
    } else {
      checker.fail(node);
    }
  }

  private void visitVanjskaDeklaracija(NonTerminalNode node) {
    checker.visitNonTerminal(NodeUtils.asNonTerminal(node.children().get(0)));
  }

  private void visitDefinicijaFunkcije(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    NonTerminalNode typeNode = NodeUtils.asNonTerminal(children.get(0));
    checker.visitNonTerminal(typeNode);
    Type returnType = typeNode.attributes().type();
    if (returnType == null || returnType instanceof ConstType) {
      checker.fail(node);
    }

    NonTerminalNode declarator = NodeUtils.asNonTerminal(children.get(1));
    declarator.attributes().inheritedType(returnType);
    checker.visitNonTerminal(declarator);
    Type declaratorType = declarator.attributes().type();
    if (!(declaratorType instanceof FunctionType functionType)) {
      checker.fail(node);
      return;
    }
    String name = declarator.attributes().identifier();
    if (name == null || name.isBlank()) {
      checker.fail(node);
    }

    checker.registerFunctionDefinition(name, functionType, node);
    FunctionType previousFunction = checker.currentFunction();
    checker.setCurrentFunction(functionType);

    NonTerminalNode body = NodeUtils.asNonTerminal(children.get(children.size() - 1));
    SymbolTable previousScope = checker.currentScope();
    checker.setCurrentScope(checker.currentScope().enterChildScope());
    try {
      checker.declareFunctionParameters(
          declarator.attributes().parameterNames(), functionType.parameterTypes(), node);
      checker.processBlock(body);
    } finally {
      checker.setCurrentScope(previousScope);
      checker.setCurrentFunction(previousFunction);
    }
  }

  private void visitDeklaracija(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    NonTerminalNode typeNode = NodeUtils.asNonTerminal(children.get(0));
    checker.visitNonTerminal(typeNode);
    Type baseType = typeNode.attributes().type();
    if (baseType == null) {
      checker.fail(node);
    }
    if (TypeSystem.stripConst(baseType) == PrimitiveType.VOID) {
      checker.fail(node);
    }
    if (children.size() == 3) {
      NonTerminalNode list = NodeUtils.asNonTerminal(children.get(1));
      list.attributes().inheritedType(baseType);
      checker.visitNonTerminal(list);
    }
  }

  private void visitListaDeklaracija(NonTerminalNode node) {
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt) {
        checker.visitNonTerminal(nt);
      }
    }
  }

  private void visitListaInitDeklaratora(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.size() == 1) {
      NonTerminalNode init = NodeUtils.asNonTerminal(children.get(0));
      init.attributes().inheritedType(node.attributes().inheritedType());
      checker.visitNonTerminal(init);
      return;
    }
    if (children.size() == 3) {
      NonTerminalNode left = NodeUtils.asNonTerminal(children.get(0));
      NonTerminalNode right = NodeUtils.asNonTerminal(children.get(2));
      left.attributes().inheritedType(node.attributes().inheritedType());
      right.attributes().inheritedType(node.attributes().inheritedType());
      checker.visitNonTerminal(left);
      checker.visitNonTerminal(right);
      return;
    }
    checker.fail(node);
  }

  private void visitInitDeklarator(NonTerminalNode node) {
    NonTerminalNode declarator = NodeUtils.asNonTerminal(node.children().get(0));
    declarator.attributes().inheritedType(node.attributes().inheritedType());
    checker.visitNonTerminal(declarator);

    Type declaredType = declarator.attributes().type();
    if (declaredType == null) {
      checker.fail(node);
    }
    String identifier = declarator.attributes().identifier();
    if (identifier == null || identifier.isBlank()) {
      checker.fail(node);
    }

    if (declaredType instanceof FunctionType functionType) {
      if (node.children().size() == 3) {
        checker.fail(node);
      }
      checker.registerFunctionPrototype(identifier, functionType, node);
      return;
    }

    checker.declareVariable(identifier, declaredType, node);

    if (node.children().size() == 3) {
      NonTerminalNode initializer = NodeUtils.asNonTerminal(node.children().get(2));
      initializer.attributes().inheritedType(declaredType);
      checker.visitNonTerminal(initializer);
      validateInitializer(initializer, declarator, declaredType, node);
    } else if (checker.requiresInitialization(declaredType)) {
      checker.fail(node);
    }
  }

  private void validateInitializer(
      NonTerminalNode initializer, NonTerminalNode declarator, Type targetType, NonTerminalNode ctx) {
    if (targetType instanceof ArrayType arrayType) {
      int limit = declarator.attributes().elementCount();
      int provided = initializer.attributes().initializerElementCount();
      if (limit > 0 && provided > limit) {
        checker.fail(ctx);
      }
      for (Type value : initializer.attributes().initializerElementTypes()) {
        checker.ensureAssignable(value, arrayType.elementType(), ctx);
      }
    } else {
      List<Type> values = initializer.attributes().initializerElementTypes();
      if (values.isEmpty() || values.size() > 1) {
        checker.fail(ctx);
      }
      checker.ensureAssignable(values.get(0), targetType, ctx);
    }
  }

  private void visitIzravniDeklarator(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.isEmpty()) {
      checker.fail(node);
    }
    if (children.size() == 1 && children.get(0) instanceof TerminalNode id) {
      Type inherited = node.attributes().inheritedType();
      if (inherited == null) {
        checker.fail(node);
      }
      node.attributes().identifier(id.lexeme());
      node.attributes().type(inherited);
      node.attributes().lValue(true);
      return;
    }
    ParseNode first = children.get(0);
    if (first instanceof NonTerminalNode inner && children.size() > 1) {
      inner.attributes().inheritedType(node.attributes().inheritedType());
      checker.visitNonTerminal(inner);
      if (children.get(1) instanceof TerminalNode lParen
          && "L_ZAGRADA".equals(lParen.symbol())) {
        handleNestedFunctionDeclarator(node, inner, children);
        return;
      }
      if (isUnsizedArrayDeclarator(children)) {
        Type baseType = inner.attributes().type();
        if (baseType == null) {
          checker.fail(node);
        }
        node.attributes().identifier(inner.attributes().identifier());
        node.attributes().type(new ArrayType(baseType));
        node.attributes().lValue(false);
        return;
      }
      if (isSizedArrayDeclarator(children)) {
        Type baseType = inner.attributes().type();
        if (baseType == null) {
          checker.fail(node);
        }
        String literal = extractArrayLengthLiteral(children.get(2), node);
        int length = checker.parseArrayLength(literal, node);
        node.attributes().identifier(inner.attributes().identifier());
        node.attributes().type(new ArrayType(baseType));
        node.attributes().elementCount(length);
        node.attributes().lValue(false);
        return;
      }
      node.attributes().identifier(inner.attributes().identifier());
      node.attributes().type(inner.attributes().type());
      node.attributes().parameterTypes(inner.attributes().parameterTypes());
      node.attributes().parameterNames(inner.attributes().parameterNames());
      node.attributes().elementCount(inner.attributes().elementCount());
      node.attributes().lValue(inner.attributes().isLValue());
      return;
    }
    if (first instanceof TerminalNode idToken) {
      if (children.size() == 4 && "L_UGL_ZAGRADA".equals(((TerminalNode) children.get(1)).symbol())) {
        handleArrayDeclarator(node, idToken, children);
        return;
      }
      handleFunctionDeclarator(node, idToken, children);
      return;
    }
    if (first instanceof NonTerminalNode inner) {
      inner.attributes().inheritedType(node.attributes().inheritedType());
      checker.visitNonTerminal(inner);
      node.attributes().identifier(inner.attributes().identifier());
      node.attributes().type(inner.attributes().type());
      node.attributes().parameterTypes(inner.attributes().parameterTypes());
      node.attributes().parameterNames(inner.attributes().parameterNames());
      node.attributes().elementCount(inner.attributes().elementCount());
      node.attributes().lValue(inner.attributes().isLValue());
      return;
    }
    checker.fail(node);
  }

  private void handleArrayDeclarator(
      NonTerminalNode node, TerminalNode idToken, List<ParseNode> children) {
    Type inherited = node.attributes().inheritedType();
    if (inherited == null) {
      checker.fail(node);
    }
    if (TypeSystem.stripConst(inherited).isVoid()) {
      checker.fail(node);
    }
    String literal = extractArrayLengthLiteral(children.get(2), node);
    int length = checker.parseArrayLength(literal, node);
    node.attributes().identifier(idToken.lexeme());
    node.attributes().type(new ArrayType(inherited));
    node.attributes().elementCount(length);
    node.attributes().lValue(false);
  }

  private void handleFunctionDeclarator(
      NonTerminalNode node, TerminalNode idToken, List<ParseNode> children) {
    Type inherited = node.attributes().inheritedType();
    if (inherited == null) {
      checker.fail(node);
    }
    if (children.size() < 4) {
      checker.fail(node);
    }
    if (!(children.get(1) instanceof TerminalNode lParen)
        || !"L_ZAGRADA".equals(lParen.symbol())
        || !(children.get(children.size() - 1) instanceof TerminalNode rParen)
        || !"D_ZAGRADA".equals(rParen.symbol())) {
      checker.fail(node);
    }

    List<Type> parameterTypes = List.of();
    List<String> parameterNames = List.of();
    if (children.size() == 4 && children.get(2) instanceof TerminalNode voidToken) {
      if (!"KR_VOID".equals(voidToken.symbol())) {
        checker.fail(node);
      }
    } else {
      NonTerminalNode params = NodeUtils.asNonTerminal(children.get(2));
      checker.visitNonTerminal(params);
      parameterTypes = params.attributes().parameterTypes();
      parameterNames = params.attributes().parameterNames();
    }

    node.attributes().identifier(idToken.lexeme());
    node.attributes().parameterTypes(parameterTypes);
    node.attributes().parameterNames(parameterNames);
    node.attributes().type(new FunctionType(inherited, parameterTypes));
    node.attributes().lValue(false);
  }

  private void handleNestedFunctionDeclarator(
      NonTerminalNode node, NonTerminalNode inner, List<ParseNode> children) {
    if (children.size() != 4) {
      checker.fail(node);
    }
    List<Type> parameterTypes = List.of();
    List<String> parameterNames = List.of();
    ParseNode paramsNode = children.get(2);
    if (paramsNode instanceof TerminalNode voidToken) {
      if (!"KR_VOID".equals(voidToken.symbol())) {
        checker.fail(node);
      }
    } else {
      NonTerminalNode params = NodeUtils.asNonTerminal(paramsNode);
      checker.visitNonTerminal(params);
      parameterTypes = params.attributes().parameterTypes();
      parameterNames = params.attributes().parameterNames();
    }
    node.attributes().identifier(inner.attributes().identifier());
    node.attributes().parameterTypes(parameterTypes);
    node.attributes().parameterNames(parameterNames);
    node.attributes().type(new FunctionType(inner.attributes().type(), parameterTypes));
    node.attributes().lValue(false);
  }

  private void visitDeklarator(NonTerminalNode node) {
    NonTerminalNode inner = NodeUtils.asNonTerminal(node.children().get(0));
    inner.attributes().inheritedType(node.attributes().inheritedType());
    checker.visitNonTerminal(inner);
    node.attributes().identifier(inner.attributes().identifier());
    node.attributes().type(inner.attributes().type());
    node.attributes().parameterTypes(inner.attributes().parameterTypes());
    node.attributes().parameterNames(inner.attributes().parameterNames());
    node.attributes().elementCount(inner.attributes().elementCount());
    node.attributes().lValue(inner.attributes().isLValue());
  }

  private void visitImeTipa(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.size() == 1) {
      NonTerminalNode spec = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(spec);
      node.attributes().type(spec.attributes().type());
      return;
    }
    if (children.size() == 2 && children.get(0) instanceof TerminalNode constToken) {
      if (!"KR_CONST".equals(constToken.symbol())) {
        checker.fail(node);
      }
      NonTerminalNode spec = NodeUtils.asNonTerminal(children.get(1));
      checker.visitNonTerminal(spec);
      Type base = spec.attributes().type();
      if (base == null || base.isVoid()) {
        checker.fail(node);
      }
      node.attributes().type(new ConstType(base));
      return;
    }
    checker.fail(node);
  }

  private boolean isUnsizedArrayDeclarator(List<ParseNode> children) {
    return children.size() == 3
        && isTerminal(children.get(1), "L_UGL_ZAGRADA")
        && isTerminal(children.get(2), "D_UGL_ZAGRADA");
  }

  private boolean isSizedArrayDeclarator(List<ParseNode> children) {
    return children.size() == 4
        && isTerminal(children.get(1), "L_UGL_ZAGRADA")
        && isTerminal(children.get(3), "D_UGL_ZAGRADA");
  }

  private boolean isTerminal(ParseNode node, String symbol) {
    return node instanceof TerminalNode terminal && symbol.equals(terminal.symbol());
  }

  private String extractArrayLengthLiteral(ParseNode node, NonTerminalNode ctx) {
    if (node instanceof TerminalNode terminal) {
      if (!"BROJ".equals(terminal.symbol())) {
        checker.fail(ctx);
      }
      return terminal.lexeme();
    }
    if (node instanceof NonTerminalNode nonTerminal) {
      List<ParseNode> children = nonTerminal.children();
      if (children.size() == 1) {
        return extractArrayLengthLiteral(children.get(0), ctx);
      }
    }
    checker.fail(ctx);
    return "";
  }

  private void visitSpecifikatoriDeklaracije(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.size() == 1 && children.get(0) instanceof NonTerminalNode spec) {
      checker.visitNonTerminal(spec);
      node.attributes().type(spec.attributes().type());
      return;
    }
    if (children.size() == 2) {
      if (children.get(0) instanceof TerminalNode constToken
          && "KR_CONST".equals(constToken.symbol())
          && children.get(1) instanceof NonTerminalNode spec) {
        checker.visitNonTerminal(spec);
        Type base = spec.attributes().type();
        if (base == null || base.isVoid()) {
          checker.fail(node);
        }
        node.attributes().type(new ConstType(base));
        return;
      }
    }
    checker.fail(node);
  }

  private void visitSpecifikatorTipa(NonTerminalNode node) {
    TerminalNode token = (TerminalNode) node.children().get(0);
    switch (token.symbol()) {
      case "KR_VOID" -> node.attributes().type(PrimitiveType.VOID);
      case "KR_CHAR" -> node.attributes().type(PrimitiveType.CHAR);
      case "KR_INT" -> node.attributes().type(PrimitiveType.INT);
      default -> checker.fail(node);
    }
  }

  private void visitListaParametara(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.size() == 1) {
      NonTerminalNode param = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(param);
      if (param.attributes().identifier() == null
          && TypeSystem.stripConst(param.attributes().type()) == PrimitiveType.VOID) {
        node.attributes().parameterTypes(List.of());
        node.attributes().parameterNames(List.of());
        return;
      }
      ensureValidParameter(param, node);
      node.attributes().parameterTypes(List.of(param.attributes().type()));
      node.attributes().parameterNames(List.of(param.attributes().identifier()));
      return;
    }
    if (children.size() == 3) {
      NonTerminalNode list = NodeUtils.asNonTerminal(children.get(0));
      NonTerminalNode param = NodeUtils.asNonTerminal(children.get(2));
      checker.visitNonTerminal(list);
      checker.visitNonTerminal(param);
      ensureValidParameter(param, node);
      List<Type> types = new ArrayList<>(list.attributes().parameterTypes());
      List<String> names = new ArrayList<>(list.attributes().parameterNames());
      if (names.contains(param.attributes().identifier())) {
        checker.fail(node);
      }
      types.add(param.attributes().type());
      names.add(param.attributes().identifier());
      node.attributes().parameterTypes(types);
      node.attributes().parameterNames(names);
      return;
    }
    checker.fail(node);
  }

  private void ensureValidParameter(NonTerminalNode param, NonTerminalNode ctx) {
    if (param.attributes().identifier() == null
        || param.attributes().type() == null
        || TypeSystem.stripConst(param.attributes().type()) == PrimitiveType.VOID) {
      checker.fail(ctx);
    }
  }

  private void visitDeklaracijaParametra(NonTerminalNode node) {
    NonTerminalNode typeNode = NodeUtils.asNonTerminal(node.children().get(0));
    checker.visitNonTerminal(typeNode);
    Type baseType = typeNode.attributes().type();
    if (baseType == null) {
      checker.fail(node);
    }
    if (node.children().size() == 1) {
      node.attributes().identifier(null);
      node.attributes().type(baseType);
      return;
    }
    if (TypeSystem.stripConst(baseType) == PrimitiveType.VOID) {
      checker.fail(node);
    }

    ParseNode descriptor = node.children().get(1);
    if (descriptor instanceof TerminalNode idToken) {
      if (node.children().size() == 2) {
        node.attributes().identifier(idToken.lexeme());
        node.attributes().type(baseType);
        node.attributes().lValue(true);
        return;
      }
      if (node.children().size() == 4) {
        node.attributes().identifier(idToken.lexeme());
        node.attributes().type(new ArrayType(baseType));
        node.attributes().lValue(false);
        return;
      }
      checker.fail(node);
    }

    if (descriptor instanceof NonTerminalNode declarator) {
      declarator.attributes().inheritedType(baseType);
      checker.visitNonTerminal(declarator);
      String identifier = declarator.attributes().identifier();
      Type effectiveType = declarator.attributes().type();
      if (identifier == null || effectiveType == null) {
        checker.fail(node);
      }
      node.attributes().identifier(identifier);
      node.attributes().type(effectiveType);
      node.attributes().elementCount(declarator.attributes().elementCount());
      node.attributes().lValue(declarator.attributes().isLValue());
      return;
    }
    checker.fail(node);
  }

  private void visitInicijalizator(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.size() == 1) {
      NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(expr);
      node.attributes().initializerElementTypes(List.of(expr.attributes().type()));
      node.attributes().initializerElementCount(1);
      return;
    }
    NonTerminalNode list = NodeUtils.asNonTerminal(children.get(1));
    checker.visitNonTerminal(list);
    node.attributes().initializerElementTypes(list.attributes().parameterTypes());
    node.attributes().initializerElementCount(list.attributes().parameterTypes().size());
  }

  private void visitListaIzrazaPridruzivanja(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    if (children.size() == 1) {
      NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(expr);
      node.attributes().parameterTypes(List.of(expr.attributes().type()));
      return;
    }
    if (children.size() == 3) {
      NonTerminalNode list = NodeUtils.asNonTerminal(children.get(0));
      NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(2));
      checker.visitNonTerminal(list);
      checker.visitNonTerminal(expr);
      List<Type> types = new ArrayList<>(list.attributes().parameterTypes());
      types.add(expr.attributes().type());
      node.attributes().parameterTypes(types);
      return;
    }
    checker.fail(node);
  }
}


