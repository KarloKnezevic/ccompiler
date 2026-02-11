package hr.fer.ppj.ir.lowering.decl;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.build.StructLayoutRegistry;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.lowering.ExpressionGenerator;
import hr.fer.ppj.ir.lowering.FunctionContext;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.util.VariableSlotManager;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for local variable declarations.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LocalDeclarationGenerator {

  private final ExpressionGenerator expressionGenerator;
  private final SymbolTable globalScope;
  private final LocalInitializerGenerator initializerGenerator;

  public LocalDeclarationGenerator(
      ExpressionGenerator expressionGenerator,
      SymbolTable globalScope,
      hr.fer.ppj.ir.build.StructNameRegistry structNameRegistry) {
    this.expressionGenerator =
        Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
    this.globalScope = Objects.requireNonNull(globalScope, "globalScope must not be null");
    this.initializerGenerator =
        new LocalInitializerGenerator(expressionGenerator, globalScope, structNameRegistry);
  }

  /**
   * Generates a local declaration.
   *
   * <p>Grammar: <deklaracija> ::= <ime_tipa> <lista_init_deklaratora> TOCKAZAREZ | <specifikatori_deklaracije> <lista_init_deklaratora> TOCKAZAREZ
   */
  public void generateDeclaration(
      NonTerminalNode node, FunctionContext functionContext) {
    List<ParseNode> children = node.children();
    if (children.isEmpty()) {
      return;
    }

    Type baseType = DeclaratorExtractor.extractBaseType(children, node);
    if (baseType == null) {
      return;
    }

    NonTerminalNode initDeclList = DeclaratorExtractor.findInitDeclaratorList(children);
    if (initDeclList != null) {
      generateInitDeclaratorList(initDeclList, baseType, functionContext);
    } else {
      NonTerminalNode initDecl = DeclaratorExtractor.findInitDeclarator(children);
      if (initDecl != null) {
        generateInitDeclarator(initDecl, baseType, functionContext);
      }
    }
  }

  /**
   * Generates an init declarator list.
   *
   * <p>Grammar: <lista_init_deklaratora> ::= <init_deklarator> | <lista_init_deklaratora> ZAREZ <init_deklarator>
   */
  private void generateInitDeclaratorList(
      NonTerminalNode node, Type baseType, FunctionContext functionContext) {
    List<ParseNode> children = node.children();

    if (children.size() == 1) {
      ParseNode firstChild = children.get(0);
      if (firstChild instanceof NonTerminalNode nt && nt.symbol().equals("<init_deklarator>")) {
        generateInitDeclarator(nt, baseType, functionContext);
      }
    } else if (children.size() == 3) {
      ParseNode firstChild = children.get(0);
      ParseNode thirdChild = children.get(2);
      if (firstChild instanceof NonTerminalNode listNt
          && listNt.symbol().equals("<lista_init_deklaratora>")) {
        generateInitDeclaratorList(listNt, baseType, functionContext);
      }
      if (thirdChild instanceof NonTerminalNode initDeclNt
          && initDeclNt.symbol().equals("<init_deklarator>")) {
        generateInitDeclarator(initDeclNt, baseType, functionContext);
      }
    }
  }

  /**
   * Generates an init declarator (variable with optional initializer).
   *
   * <p>Grammar: <init_deklarator> ::= <izravni_deklarator> | <izravni_deklarator> OP_PRIDRUZI <inicijalizator> | <deklarator> | <deklarator> OP_PRIDRUZI <inicijalizator>
   */
  private void generateInitDeclarator(
      NonTerminalNode node, Type baseType, FunctionContext functionContext) {
    List<ParseNode> children = node.children();
    if (children.isEmpty()) {
      return;
    }

    NonTerminalNode declarator = DeclaratorExtractor.findDeclarator(children);
    if (declarator == null) {
      return;
    }

    NonTerminalNode finalDeclarator = DeclaratorExtractor.extractFinalDeclarator(declarator);
    String varName = DeclaratorExtractor.extractVariableName(finalDeclarator, declarator);
    Type varType = DeclaratorExtractor.extractVariableType(finalDeclarator, declarator, baseType);

    if (varName == null || varType == null) {
      return;
    }

    // Block-scope function declarations (prototypes) do not allocate local storage.
    Type strippedVarType = TypeSystem.stripConst(varType);
    if (strippedVarType instanceof FunctionType) {
      return;
    }

    IrFunctionBuilder builder = functionContext.functionBuilder();
    if (builder == null) {
      return;
    }

    String actualVarName =
        VariableSlotManager.getUniqueVariableName(varName, builder);
    functionContext.variableNameManager().mapName(varName, actualVarName);

    VariableSlotManager.declareInScope(varName, varType, functionContext.functionScope());

    // Ensure struct types are registered and their definitions emitted
    StructLayoutRegistry structRegistry = functionContext.structLayoutRegistry();
    if (structRegistry != null) {
      ensureStructTypeReady(strippedVarType, structRegistry);
    }

    IrType irType;
    if (structRegistry != null) {
      irType = TypeMapper.toIrType(varType, structRegistry.getStructNameRegistry());
    } else {
      irType = TypeMapper.toIrType(varType);
    }
    int[] currentOffset = {functionContext.localOffset()};
    VariableSlotManager.createLocalSlot(
        actualVarName, irType, builder, currentOffset, structRegistry);
    functionContext.setLocalOffset(currentOffset[0]);

    if (children.size() == 3) {
      NonTerminalNode initializer =
          NodeUtils.asNonTerminal(children.get(2), "<inicijalizator>");
      initializerGenerator.generateInitializer(initializer, varName, varType, irType, functionContext);
    }
  }

  /**
   * Ensures that any struct type within a type (including array element types) is registered.
   */
  private void ensureStructTypeReady(Type type, StructLayoutRegistry structRegistry) {
    Type stripped = TypeSystem.stripConst(type);
    if (stripped instanceof StructType structType) {
      structRegistry.ensureStructReady(structType);
    } else if (stripped instanceof ArrayType arrayType) {
      ensureStructTypeReady(arrayType.elementType(), structRegistry);
    }
  }
}
