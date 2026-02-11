package hr.fer.ppj.ir.lowering.decl;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.lowering.ExpressionGenerator;
import hr.fer.ppj.ir.lowering.FunctionContext;
import hr.fer.ppj.ir.lowering.expr.LValueGenerator;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.build.TypeSizeCalculator;
import hr.fer.ppj.ir.util.ArrayInitializerEvaluator;
import hr.fer.ppj.ir.util.ConstantEvaluator;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for local variable initializers.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LocalInitializerGenerator {

  private final ExpressionGenerator expressionGenerator;
  private final SymbolTable globalScope;
  private final hr.fer.ppj.ir.build.StructNameRegistry structNameRegistry;

  public LocalInitializerGenerator(
      ExpressionGenerator expressionGenerator,
      SymbolTable globalScope,
      hr.fer.ppj.ir.build.StructNameRegistry structNameRegistry) {
    this.expressionGenerator =
        Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
    this.globalScope = Objects.requireNonNull(globalScope, "globalScope must not be null");
    this.structNameRegistry = Objects.requireNonNull(structNameRegistry, "structNameRegistry must not be null");
  }

  /**
   * Generates initializer code for a local variable.
   *
   * @param initializer the initializer node
   * @param varName the variable name
   * @param varType the variable type
   * @param irType the IR type
   * @param functionContext the function context
   */
  public void generateInitializer(
      NonTerminalNode initializer,
      String varName,
      Type varType,
      IrType irType,
      FunctionContext functionContext) {
    Objects.requireNonNull(initializer, "initializer must not be null");
    Objects.requireNonNull(varName, "varName must not be null");
    Objects.requireNonNull(varType, "varType must not be null");
    Objects.requireNonNull(irType, "irType must not be null");
    Objects.requireNonNull(functionContext, "functionContext must not be null");

    IrFunctionBuilder builder = functionContext.functionBuilder();
    if (builder.getCurrentBlockLabel() == null) {
      builder.startNewBlock();
    }

    if (varType instanceof ArrayType arrayType && initializer.children().size() >= 3) {
      ParseNode initFirstChild = initializer.children().get(0);
      if (initFirstChild instanceof TerminalNode term
          && term.symbol().equals("L_VIT_ZAGRADA")) {
        generateArrayInitializer(initializer, varName, arrayType, functionContext);
        return;
      }
    }
    if (varType instanceof ArrayType arrayType && isStringLiteralInitializer(initializer, arrayType)) {
      generateStringLiteralInitializer(initializer, varName, arrayType, functionContext);
      return;
    }

    generateScalarInitializer(initializer, varName, varType, irType, functionContext);
  }

  private void generateArrayInitializer(
      NonTerminalNode initializer,
      String varName,
      ArrayType arrayType,
      FunctionContext functionContext) {
    IrFunctionBuilder builder = functionContext.functionBuilder();
    LValueGenerator lValueGenerator = createLValueGenerator(functionContext);
    IrTemp varAddr = lValueGenerator.emitLValueForVariable(varName, arrayType, functionContext);

    NonTerminalNode listNode =
        NodeUtils.asNonTerminal(initializer.children().get(1), "<lista_izraza_pridruzivanja>");
    List<NonTerminalNode> elementExprs =
        ArrayInitializerEvaluator.extractInitializerExpressions(listNode);

    Type elementType = arrayType.elementType();
    IrType irElementType = TypeMapper.toIrType(elementType);
    int elemSize = TypeSizeCalculator.getTypeSize(irElementType);

    for (int i = 0; i < elementExprs.size(); i++) {
      NonTerminalNode elemExpr = elementExprs.get(i);
      IrValue elemValue = expressionGenerator.emitRValue(elemExpr, functionContext);

      IrConst indexConst = new IrConst.IntConst(i, IrPrimitiveType.INT32);
      IrRhs.AddrIndex elemAddr =
          new IrRhs.AddrIndex(
              varAddr, indexConst, elemSize, new IrPointerType(irElementType));
      IrTemp elemAddrTemp = builder.tempFactory().newTemp(elemAddr.resultType());
      builder.addInstruction(new IrInstruction.IrAssignInstr(elemAddrTemp, elemAddr));

      builder.addInstruction(
          new IrInstruction.IrStoreInstr(elemAddrTemp, elemValue, irElementType));
    }
  }

  private void generateStringLiteralInitializer(
      NonTerminalNode initializer,
      String varName,
      ArrayType arrayType,
      FunctionContext functionContext) {
    IrFunctionBuilder builder = functionContext.functionBuilder();
    NonTerminalNode expr = NodeUtils.asNonTerminal(initializer.children().get(0));
    IrConst constant = ConstantEvaluator.extractConstantFromExpression(expr, arrayType);
    if (!(constant instanceof IrConst.ArrayConst arrayConst)) {
      throw new IllegalArgumentException("String literal initializer did not produce array constant");
    }

    LValueGenerator lValueGenerator = createLValueGenerator(functionContext);
    IrTemp varAddr = lValueGenerator.emitLValueForVariable(varName, arrayType, functionContext);
    Type elementType = arrayType.elementType();
    IrType irElementType = TypeMapper.toIrType(elementType);
    int elemSize = TypeSizeCalculator.getTypeSize(irElementType);

    List<IrConst> elements = arrayConst.elements();
    for (int i = 0; i < elements.size(); i++) {
      IrConst indexConst = new IrConst.IntConst(i, IrPrimitiveType.INT32);
      IrRhs.AddrIndex elemAddr =
          new IrRhs.AddrIndex(varAddr, indexConst, elemSize, new IrPointerType(irElementType));
      IrTemp elemAddrTemp = builder.tempFactory().newTemp(elemAddr.resultType());
      builder.addInstruction(new IrInstruction.IrAssignInstr(elemAddrTemp, elemAddr));
      builder.addInstruction(new IrInstruction.IrStoreInstr(elemAddrTemp, elements.get(i), irElementType));
    }
  }

  private void generateScalarInitializer(
      NonTerminalNode initializer,
      String varName,
      Type varType,
      IrType irType,
      FunctionContext functionContext) {
    List<ParseNode> children = initializer.children();
    if (children.size() == 1) {
      ParseNode child = children.get(0);
      if (child instanceof NonTerminalNode expr) {
        IrValue initValue = expressionGenerator.emitRValue(expr, functionContext);
        LValueGenerator lValueGenerator = createLValueGenerator(functionContext);
        IrTemp varAddr = lValueGenerator.emitLValueForVariable(varName, varType, functionContext);
        IrFunctionBuilder builder = functionContext.functionBuilder();
        builder.addInstruction(new IrInstruction.IrStoreInstr(varAddr, initValue, irType));
      }
    }
  }

  private LValueGenerator createLValueGenerator(FunctionContext functionContext) {
    return new LValueGenerator(
        expressionGenerator,
        globalScope,
        functionContext.variableNameManager(),
        functionContext.addressReuseContext(),
        structNameRegistry);
  }

  private boolean isStringLiteralInitializer(NonTerminalNode initializer, ArrayType arrayType) {
    if (initializer.children().size() != 1) {
      return false;
    }
    if (!(TypeSystem.stripConst(arrayType.elementType()) == PrimitiveType.CHAR)) {
      return false;
    }
    ParseNode first = initializer.children().get(0);
    if (!(first instanceof NonTerminalNode expr)) {
      return false;
    }
    return expr.attributes().isStringLiteral();
  }
}
