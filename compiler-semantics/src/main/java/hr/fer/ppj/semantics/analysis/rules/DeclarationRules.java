package hr.fer.ppj.semantics.analysis.rules;

import hr.fer.ppj.semantics.analysis.SemanticChecker;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.types.ConstType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;

/**
 * Semantic rule implementations for declaration-related productions.
 * 
 * <p>This class orchestrates declaration analysis and delegates to specialized
 * rule classes for:
 * <ul>
 *   <li>Type specifications ({@link TypeSpecificationRules})</li>
 *   <li>Declarators ({@link DeclaratorRules})</li>
 *   <li>Structs ({@link StructRules})</li>
 *   <li>Parameters ({@link ParameterRules})</li>
 *   <li>Initializers ({@link InitializerRules})</li>
 * </ul>
 * 
 * <p>This class handles:
 * <ul>
 *   <li>Translation unit structure</li>
 *   <li>External declarations</li>
 *   <li>Function definitions</li>
 *   <li>Variable declarations</li>
 * </ul>
 * 
 * @see hr.fer.ppj.semantics.analysis.SemanticChecker for the main semantic analysis coordinator
 * @see ExpressionRules for expression-related semantic rules
 * @see StatementRules for statement-related semantic rules
 */
public final class DeclarationRules {

  private final SemanticChecker checker;
  private final InitializerRules initializerRules;

  public DeclarationRules(SemanticChecker checker) {
    this.checker = checker;
    
    // Initialize specialized rule handlers
    new TypeSpecificationRules(checker);
    new DeclaratorRules(checker);
    new StructRules(checker);
    new ParameterRules(checker);
    this.initializerRules = new InitializerRules(checker);
    
    // Register declaration orchestration rules
    checker.registerRule("<prijevodna_jedinica>", this::visitPrijevodnaJedinica);
    checker.registerRule("<vanjska_deklaracija>", this::visitVanjskaDeklaracija);
    checker.registerRule("<definicija_funkcije>", this::visitDefinicijaFunkcije);
    checker.registerRule("<deklaracija>", this::visitDeklaracija);
    checker.registerRule("<lista_deklaracija>", this::visitListaDeklaracija);
    checker.registerRule("<lista_init_deklaratora>", this::visitListaInitDeklaratora);
    checker.registerRule("<init_deklarator>", this::visitInitDeklarator);
  }

  private void visitPrijevodnaJedinica(NonTerminalNode node) {
    var children = node.children();
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

  /**
   * Performs semantic analysis for function definitions.
   * 
   * <p>Grammar:
   *   <definicija_funkcije> ::= <ime_tipa> <izravni_deklarator> <slozena_naredba>
   * 
   * <p>This method:
   * <ol>
   *   <li>Processes the return type (must not be const-qualified)</li>
   *   <li>Processes the function declarator to extract function signature</li>
   *   <li>Registers the function definition in the symbol table</li>
   *   <li>Creates a new scope for function parameters and body</li>
   *   <li>Declares function parameters in the new scope</li>
   *   <li>Processes the function body</li>
   * </ol>
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Return type cannot be const-qualified (const applies to return value, not type)</li>
   *   <li>Declarator must represent a function type</li>
   *   <li>Function name must be valid identifier</li>
   * </ul>
   * 
   * @param node the function definition node
   */
  private void visitDefinicijaFunkcije(NonTerminalNode node) {
    var children = node.children();
    
    // Process return type: <ime_tipa>
    NonTerminalNode typeNode = NodeUtils.asNonTerminal(children.get(0));
    checker.visitNonTerminal(typeNode);
    Type returnType = typeNode.attributes().type();
    // Return type cannot be const-qualified (const applies to the return value, not the type itself)
    if (returnType == null || returnType instanceof ConstType) {
      checker.fail(node);
    }

    // Process function declarator: <izravni_deklarator>
    // The declarator will build the function type from the return type and parameter list
    NonTerminalNode declarator = NodeUtils.asNonTerminal(children.get(1));
    declarator.attributes().inheritedType(returnType);
    checker.visitNonTerminal(declarator);
    Type declaratorType = declarator.attributes().type();
    // Declarator must represent a function type
    if (!(declaratorType instanceof FunctionType functionType)) {
      checker.fail(node);
      return;
    }
    String name = declarator.attributes().identifier();
    if (name == null || name.isBlank()) {
      checker.fail(node);
    }

    // Register function definition in symbol table
    checker.registerFunctionDefinition(name, functionType, node);
    
    // Save current function context and set new function context
    // This is needed for return statement validation
    FunctionType previousFunction = checker.currentFunction();
    checker.setCurrentFunction(functionType);

    // Process function body in a new scope
    NonTerminalNode body = NodeUtils.asNonTerminal(children.get(children.size() - 1));
    SymbolTable previousScope = checker.currentScope();
    checker.setCurrentScope(checker.currentScope().enterChildScope());
    try {
      // Declare function parameters in the new scope
      // Parameters are accessible within the function body
      checker.declareFunctionParameters(
          declarator.attributes().parameterNames(), functionType.parameterTypes(), node);
      // Process function body (compound statement)
      checker.processBlock(body);
    } finally {
      // Restore previous scope and function context
      checker.setCurrentScope(previousScope);
      checker.setCurrentFunction(previousFunction);
    }
  }

  /**
   * Performs semantic analysis for variable declarations.
   * 
   * <p>Grammar:
   *   <deklaracija> ::= <ime_tipa> <lista_init_deklaratora> TOCKAZAREZ
   * 
   * <p>This method:
   * <ol>
   *   <li>Processes the type specification</li>
   *   <li>Validates that the type is not void</li>
   *   <li>Processes the declarator list with the inherited type</li>
   * </ol>
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Variables cannot have void type</li>
   *   <li>Type must be valid (non-null)</li>
   * </ul>
   * 
   * @param node the declaration node
   */
  private void visitDeklaracija(NonTerminalNode node) {
    var children = node.children();
    // Process type specification: <ime_tipa>
    NonTerminalNode typeNode = NodeUtils.asNonTerminal(children.get(0));
    checker.visitNonTerminal(typeNode);
    Type baseType = typeNode.attributes().type();
    if (baseType == null) {
      checker.fail(node);
    }
    // A bare declaration with void type is invalid (e.g., "void;").
    // For declarations with declarators, we defer validation to declarator processing,
    // where function prototypes are handled and non-function void declarations are rejected.
    if (children.size() == 2 && TypeSystem.stripConst(baseType) == PrimitiveType.VOID) {
      checker.fail(node);
    }
    // Process declarator list: <lista_init_deklaratora>
    // The type is inherited to the declarator list, which will distribute it to individual declarators
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
    var children = node.children();
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

  /**
   * Performs semantic analysis for initialized declarators.
   * 
   * <p>Grammar:
   *   <init_deklarator> ::= <izravni_deklarator>
   *                       | <izravni_deklarator> OP_PRIDRUZI <inicijalizator>
   * 
   * <p>This method handles both variable declarations and function prototypes:
   * <ul>
   *   <li>If declarator type is FunctionType: registers function prototype (no initializer allowed)</li>
   *   <li>Otherwise: declares variable and validates initializer if present</li>
   * </ul>
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Function prototypes cannot have initializers</li>
   *   <li>Variables requiring initialization (e.g., const) must have initializers</li>
   *   <li>Initializer type must be assignable to declared type</li>
   * </ul>
   * 
   * @param node the initialized declarator node
   */
  private void visitInitDeklarator(NonTerminalNode node) {
    // Process declarator with inherited type
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

    // Handle function prototypes (function declarations without body)
    if (declaredType instanceof FunctionType functionType) {
      // Function prototypes cannot have initializers
      if (node.children().size() == 3) {
        checker.fail(node);
      }
      checker.registerFunctionPrototype(identifier, functionType, node);
      return;
    }

    // Handle variable declarations
    checker.declareVariable(identifier, declaredType, node);

    // Process initializer if present
    if (node.children().size() == 3) {
      NonTerminalNode initializer = NodeUtils.asNonTerminal(node.children().get(2));
      initializer.attributes().inheritedType(declaredType);
      checker.visitNonTerminal(initializer);
      // Validate that initializer type is assignable to declared type
      initializerRules.validateInitializer(initializer, declarator, declaredType, node);
    } else if (checker.requiresInitialization(declaredType)) {
      // Some types (e.g., const variables) require initialization
      checker.fail(node);
    }
  }
}
