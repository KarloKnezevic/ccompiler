package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.build.StructNameRegistry;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.util.AddressabilityChecker;
import hr.fer.ppj.ir.util.BlockLocalSymbolAddressCache;
import hr.fer.ppj.ir.util.ExpressionNameExtractor;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.SemanticAttributes;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PointerType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.List;
import java.util.Objects;

/**
 * Handles l-value generation for postfix expressions.
 *
 * <p>Supports array indexing and struct field access as defined in the grammar:
 * <pre>
 * AddrIndex ::= "addr_index" Value "," Value "," Int ;
 * AddrField ::= "addr_field" Value "," Ident "." Ident ;
 * </pre>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class LValuePostfixHandler {

  private final ExpressionEmitter emitter;
  private final LValueGenerator lValueGenerator;
  private final StructNameRegistry structNameRegistry;

  LValuePostfixHandler(
      ExpressionEmitter emitter,
      LValueGenerator lValueGenerator,
      StructNameRegistry structNameRegistry) {
    this.emitter = Objects.requireNonNull(emitter);
    this.lValueGenerator = Objects.requireNonNull(lValueGenerator);
    this.structNameRegistry = Objects.requireNonNull(structNameRegistry);
  }

  IrTemp emitLValuePostfix(
      NonTerminalNode node,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    List<ParseNode> children = node.children();
    if (children.isEmpty()) {
      throw new IllegalArgumentException("Empty postfix expression");
    }

    // Array indexing: <postfiks_izraz> [ <izraz> ]
    if (isArrayIndexing(children)) {
      return emitArrayIndexing(children, functionContext);
    }

    // Struct field access: <postfiks_izraz> . IDN
    if (isFieldAccess(children)) {
      return emitFieldAccess(children, functionContext);
    }

    // Parentheses: L_ZAGRADA <izraz> D_ZAGRADA
    if (isParenthesized(children)) {
      return lValueGenerator.emitLValue((NonTerminalNode) children.get(1), functionContext);
    }

    // Postfix increment/decrement
    if (isPostfixIncDec(children)) {
      NonTerminalNode baseNode = NodeUtils.asNonTerminal(children.get(0), "<postfiks_izraz>");
      return lValueGenerator.emitLValue(baseNode, functionContext);
    }

    // Fall back to primary expression
    if (children.get(0) instanceof NonTerminalNode nt
        && nt.symbol().equals("<primarni_izraz>")) {
      return lValueGenerator.emitLValuePrimary(nt, functionContext);
    }

    throw new IllegalArgumentException("Cannot emit l-value for postfix expression");
  }

  private boolean isArrayIndexing(List<ParseNode> children) {
    return children.size() >= 3
        && children.get(1) instanceof TerminalNode t
        && t.symbol().equals("L_UGL_ZAGRADA");
  }

  private boolean isFieldAccess(List<ParseNode> children) {
    return children.size() >= 3
        && children.get(1) instanceof TerminalNode t
        && t.symbol().equals("TOCKA");
  }

  private boolean isParenthesized(List<ParseNode> children) {
    return children.size() == 3
        && children.get(0) instanceof TerminalNode t1 && t1.symbol().equals("L_ZAGRADA")
        && children.get(1) instanceof NonTerminalNode
        && children.get(2) instanceof TerminalNode t3 && t3.symbol().equals("D_ZAGRADA");
  }

  private boolean isPostfixIncDec(List<ParseNode> children) {
    return children.size() == 2
        && children.get(1) instanceof TerminalNode t
        && (t.symbol().equals("OP_INC") || t.symbol().equals("OP_DEC"));
  }

  private IrTemp emitArrayIndexing(
      List<ParseNode> children,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    IrFunctionBuilder builder = functionContext.functionBuilder();
    NonTerminalNode baseNode = NodeUtils.asNonTerminal(children.get(0), "<postfiks_izraz>");
    NonTerminalNode indexNode = NodeUtils.asNonTerminal(children.get(2), "<izraz>");

    SemanticAttributes baseAttrs = baseNode.attributes();
    Type baseType = baseAttrs.type();

    IrTemp baseAddr = resolveArrayBase(baseNode, baseType, functionContext, builder);
    IrValue index = emitter.emitRValue(indexNode, functionContext);

    Type elementType = extractElementType(baseType);
    IrType irElementType = TypeMapper.toIrType(elementType);
    int elemSize = computeElementSize(irElementType, functionContext);

    return emitAddrIndex(baseAddr, index, irElementType, elemSize, functionContext, builder);
  }

  private IrTemp resolveArrayBase(
      NonTerminalNode baseNode, Type baseType,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext,
      IrFunctionBuilder builder) {

    String baseVarName = ExpressionNameExtractor.extractArrayBaseVarName(baseNode);
    boolean isPointerParam = isPointerParameter(baseVarName, baseType, builder);

    if (isPointerParam) {
      IrTemp paramAddr = lValueGenerator.emitLValue(baseNode, functionContext);
      IrType ptrType = TypeMapper.toIrType(baseType);
      IrRhs.Load loadPtr = new IrRhs.Load(paramAddr, ptrType);
      IrTemp baseAddr = builder.tempFactory().newTemp(ptrType);
      builder.addInstruction(new IrInstruction.IrAssignInstr(baseAddr, loadPtr));
      return baseAddr;
    }

    if (AddressabilityChecker.isAddressableExpressionForm(baseNode)) {
      return lValueGenerator.emitLValue(baseNode, functionContext);
    }

    IrValue ptrValue = emitter.emitRValue(baseNode, functionContext);
    if (ptrValue instanceof IrTemp temp) {
      return temp;
    }
    throw new IllegalArgumentException("Cannot index into constant pointer value");
  }

  private boolean isPointerParameter(String varName, Type type, IrFunctionBuilder builder) {
    if (varName == null || !builder.isParameter(varName)) {
      return false;
    }
    Type stripped = TypeSystem.stripConst(type);
    return stripped instanceof PointerType;
  }

  private Type extractElementType(Type baseType) {
    Type stripped = TypeSystem.stripConst(baseType);
    if (stripped instanceof ArrayType at) return at.elementType();
    if (stripped instanceof PointerType pt) return pt.baseType();
    throw new IllegalArgumentException("Base type is not array or pointer: " + baseType);
  }

  private int computeElementSize(IrType type, hr.fer.ppj.ir.lowering.FunctionContext ctx) {
    if (ctx.structLayoutRegistry() != null) {
      return ctx.structLayoutRegistry().getTypeSize(type);
    }
    return hr.fer.ppj.ir.build.TypeSizeCalculator.getTypeSize(type);
  }

  private IrTemp emitAddrIndex(
      IrTemp baseAddr, IrValue index, IrType elemType, int elemSize,
      hr.fer.ppj.ir.lowering.FunctionContext ctx, IrFunctionBuilder builder) {

    String cacheKey = createIndexCacheKey(baseAddr, index);
    if (cacheKey != null) {
      IrTemp cached = ctx.blockLocalAddressCache().get(cacheKey);
      if (cached != null) return cached;
    }

    IrRhs.AddrIndex addrIndex = new IrRhs.AddrIndex(
        baseAddr, index, elemSize, new IrPointerType(elemType));
    IrTemp result = builder.tempFactory().newTemp(addrIndex.resultType());
    builder.addInstruction(new IrInstruction.IrAssignInstr(result, addrIndex));

    if (cacheKey != null) {
      ctx.blockLocalAddressCache().put(cacheKey, result);
    }
    return result;
  }

  private String createIndexCacheKey(IrTemp base, IrValue index) {
    if (index instanceof hr.fer.ppj.ir.model.IrConst c) {
      return "index:" + base.index() + "[" + c.toIrString() + "]";
    }
    return null;
  }

  private IrTemp emitFieldAccess(
      List<ParseNode> children,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    IrFunctionBuilder builder = functionContext.functionBuilder();
    NonTerminalNode baseNode = NodeUtils.asNonTerminal(children.get(0), "<postfiks_izraz>");
    TerminalNode fieldNode = NodeUtils.asTerminal(children.get(2), "IDN");

    IrTemp baseAddr = lValueGenerator.emitLValue(baseNode, functionContext);
    String fieldName = fieldNode.lexeme();
    Type baseType = baseNode.attributes().type();
    StructType structType = extractStructType(baseType);

    String structName = structNameRegistry.getStructName(structType.tag(), structType);
    IrType fieldType = TypeMapper.toIrType(structType.getFieldType(fieldName), structNameRegistry);

    String cacheKey = "field:" + baseAddr.index() + "." + fieldName;
    BlockLocalSymbolAddressCache cache = functionContext.blockLocalAddressCache();
    IrTemp cached = cache.get(cacheKey);
    if (cached != null) return cached;

    IrRhs.AddrField addrField = new IrRhs.AddrField(
        baseAddr, structName, fieldName, new IrPointerType(fieldType));
    IrTemp result = builder.tempFactory().newTemp(addrField.resultType());
    builder.addInstruction(new IrInstruction.IrAssignInstr(result, addrField));
    cache.put(cacheKey, result);
    return result;
  }

  private StructType extractStructType(Type type) {
    if (type instanceof StructType st) return st;
    if (type instanceof PointerType pt && pt.baseType() instanceof StructType st) return st;
    throw new IllegalArgumentException("Base type is not a struct: " + type);
  }
}
