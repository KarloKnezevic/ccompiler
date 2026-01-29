package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrSymbolRef;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.util.AddressabilityChecker;
import hr.fer.ppj.ir.util.AddressReuseContext;
import hr.fer.ppj.ir.util.BlockLocalSymbolAddressCache;
import hr.fer.ppj.ir.util.SymbolResolver;
import hr.fer.ppj.ir.util.VariableNameManager;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PointerType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.tree.SemanticAttributes;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for l-values (addresses).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LValueGenerator {

  private final ExpressionEmitter emitter;
  private final SymbolTable globalScope;
  private final VariableNameManager variableNameManager;
  private final AddressReuseContext addressReuseContext;
  private final hr.fer.ppj.ir.build.StructNameRegistry structNameRegistry;

  public LValueGenerator(
      ExpressionEmitter emitter,
      SymbolTable globalScope,
      VariableNameManager variableNameManager,
      AddressReuseContext addressReuseContext,
      hr.fer.ppj.ir.build.StructNameRegistry structNameRegistry) {
    this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
    this.globalScope = Objects.requireNonNull(globalScope, "globalScope must not be null");
    this.variableNameManager = Objects.requireNonNull(variableNameManager, "variableNameManager must not be null");
    this.addressReuseContext = Objects.requireNonNull(addressReuseContext, "addressReuseContext must not be null");
    this.structNameRegistry = Objects.requireNonNull(structNameRegistry, "structNameRegistry must not be null");
  }

  /**
   * Emits an l-value (address) for an expression.
   */
  public IrTemp emitLValue(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    Objects.requireNonNull(node, "node must not be null");
    Objects.requireNonNull(functionContext, "functionContext must not be null");

    if (!AddressabilityChecker.isAddressableExpressionForm(node)) {
      throw new IllegalArgumentException("Expression is not addressable (l-value): " + node.symbol());
    }

    String symbol = node.symbol();
    IrFunctionBuilder builder = functionContext.functionBuilder();

    return switch (symbol) {
      case "<primarni_izraz>" -> emitLValuePrimary(node, functionContext);
      case "<postfiks_izraz>" -> emitLValuePostfix(node, functionContext);
      case "<unarni_izraz>" -> {
        List<ParseNode> children = node.children();
        if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
          yield emitLValue(child, functionContext);
        } else {
          yield emitLValueUnary(node, functionContext);
        }
      }
      case "<cast_izraz>" -> {
        List<ParseNode> children = node.children();
        if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
          yield emitLValue(child, functionContext);
        } else {
          throw new IllegalArgumentException("Cannot emit l-value for cast expression");
        }
      }
      default -> throw new IllegalArgumentException("Cannot emit l-value for: " + symbol);
    };
  }

  /**
   * Emits l-value for a primary expression (identifier).
   */
  public IrTemp emitLValuePrimary(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    List<ParseNode> children = node.children();
    if (children.isEmpty()) {
      throw new IllegalArgumentException("Empty primary expression");
    }

    ParseNode firstChild = children.get(0);
    if (firstChild instanceof TerminalNode term && term.symbol().equals("IDN")) {
      String varName = term.lexeme();
      SemanticAttributes attrs = node.attributes();
      Type varType = attrs.type();

      // Check for address reuse from assignment context
      // Only reuse if we have both the variable name AND a valid address
      if (addressReuseContext.canReuse(varName)) {
        IrTemp reuseAddr = addressReuseContext.getReuseAddress(varName);
        if (reuseAddr != null) {
          return reuseAddr;
        }
        // If canReuse is true but address is null, fall through to get a new address
      }

      // Check for last load address (for reusing address used in right side evaluation)
      IrTemp lastLoadAddr = addressReuseContext.getLastLoadAddress(varName);
      if (lastLoadAddr != null) {
        return lastLoadAddr;
      }

      // Note: Array base address reuse is disabled to match expected IR output format
      // which recomputes addr_of_symbol for each array access
      // Type strippedVarType = TypeSystem.stripConst(varType);
      // boolean isArrayType = strippedVarType instanceof ArrayType;
      // if (isArrayType
      //     && addressReuseContext.canReuseArrayBase(varName)) {
      //   return addressReuseContext.getArrayBaseAddress(varName);
      // }

      return emitLValueForVariable(varName, varType, functionContext);
    }

    throw new IllegalArgumentException("Cannot emit l-value for primary expression");
  }

  /**
   * Emits l-value for a unary expression (dereference).
   */
  public IrTemp emitLValueUnary(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    List<ParseNode> children = node.children();
    if (children.size() >= 2) {
      ParseNode first = children.get(0);
      if (first instanceof TerminalNode term && term.symbol().equals("OP_PUTA")) {
        NonTerminalNode operand = NodeUtils.asNonTerminal(children.get(1), "<cast_izraz>");
        IrValue ptrValue = emitter.emitRValue(operand, functionContext);

        if (ptrValue instanceof IrTemp temp) {
          return temp;
        }

        throw new IllegalArgumentException(
            "Cannot get address of constant pointer value");
      }
    }
    throw new IllegalArgumentException("Cannot emit l-value for unary expression");
  }

  /**
   * Emits l-value for a postfix expression (array indexing, field access).
   */
  public IrTemp emitLValuePostfix(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    List<ParseNode> children = node.children();
    if (children.isEmpty()) {
      throw new IllegalArgumentException("Empty postfix expression");
    }

    IrFunctionBuilder builder = functionContext.functionBuilder();

    // Array indexing: <postfiks_izraz> [ <izraz> ]
    if (children.size() >= 3) {
      ParseNode secondChild = children.get(1);
      if (secondChild instanceof TerminalNode bracketTerm
          && bracketTerm.symbol().equals("L_UGL_ZAGRADA")) {
        NonTerminalNode baseNode =
            NodeUtils.asNonTerminal(children.get(0), "<postfiks_izraz>");
        NonTerminalNode indexNode = NodeUtils.asNonTerminal(children.get(2), "<izraz>");

        SemanticAttributes baseAttrs = baseNode.attributes();
        Type baseType = baseAttrs.type();

        boolean isPointerParam = false;
        String baseVarName = hr.fer.ppj.ir.util.ExpressionNameExtractor.extractArrayBaseVarName(baseNode);
        if (baseVarName != null
            && builder.isParameter(baseVarName)) {
          Type strippedBaseType = TypeSystem.stripConst(baseType);
          if (strippedBaseType instanceof PointerType) {
            isPointerParam = true;
          }
        }

        IrTemp baseAddr;
        if (baseVarName != null && !isPointerParam) {
          // Note: Array base address reuse is disabled to match expected IR output format
          // if (addressReuseContext.canReuseArrayBase(baseVarName)) {
          //   baseAddr = addressReuseContext.getArrayBaseAddress(baseVarName);
          // } else {
            if (AddressabilityChecker.isAddressableExpressionForm(baseNode)) {
              baseAddr = emitLValue(baseNode, functionContext);
              // addressReuseContext.setArrayBaseAddress(baseVarName, baseAddr);
            } else {
              IrValue ptrValue = emitter.emitRValue(baseNode, functionContext);
              if (ptrValue instanceof IrTemp temp) {
                baseAddr = temp;
                // addressReuseContext.setArrayBaseAddress(baseVarName, baseAddr);
              } else {
                throw new IllegalArgumentException(
                    "Cannot index into constant pointer value");
              }
            }
          // }
        } else {
          if (isPointerParam) {
            IrTemp paramAddr = emitLValue(baseNode, functionContext);
            IrType ptrType = TypeMapper.toIrType(baseType);
            IrRhs.Load loadPtr = new IrRhs.Load(paramAddr, ptrType);
            baseAddr = builder.tempFactory().newTemp(ptrType);
            builder.addInstruction(new IrInstruction.IrAssignInstr(baseAddr, loadPtr));
          } else if (AddressabilityChecker.isAddressableExpressionForm(baseNode)) {
            baseAddr = emitLValue(baseNode, functionContext);
          } else {
            IrValue ptrValue = emitter.emitRValue(baseNode, functionContext);
            if (ptrValue instanceof IrTemp temp) {
              baseAddr = temp;
            } else {
              throw new IllegalArgumentException(
                  "Cannot index into constant pointer value");
            }
          }
        }

        IrValue index = emitter.emitRValue(indexNode, functionContext);

        Type elementType;
        Type strippedBaseType = TypeSystem.stripConst(baseType);
        if (strippedBaseType instanceof ArrayType arrayType) {
          elementType = arrayType.elementType();
        } else if (strippedBaseType instanceof PointerType pointerType) {
          elementType = pointerType.baseType();
        } else {
          throw new IllegalArgumentException("Base type is not array or pointer: " + baseType);
        }
        IrType irElementType = TypeMapper.toIrType(elementType);
        int elemSize = hr.fer.ppj.ir.build.TypeSizeCalculator.getTypeSize(irElementType);

        IrRhs.AddrIndex addrIndex =
            new IrRhs.AddrIndex(baseAddr, index, elemSize, new IrPointerType(irElementType));
        IrTemp result = builder.tempFactory().newTemp(addrIndex.resultType());
        builder.addInstruction(new IrInstruction.IrAssignInstr(result, addrIndex));
        return result;
      }
    }

    // Struct field access: <postfiks_izraz> . IDN
    if (children.size() >= 3) {
      ParseNode secondChild = children.get(1);
      if (secondChild instanceof TerminalNode term && term.symbol().equals("TOCKA")) {
        NonTerminalNode baseNode =
            NodeUtils.asNonTerminal(children.get(0), "<postfiks_izraz>");
        TerminalNode fieldNode = NodeUtils.asTerminal(children.get(2), "IDN");

        IrTemp baseAddr = emitLValue(baseNode, functionContext);
        String fieldName = fieldNode.lexeme();
        SemanticAttributes baseAttrs = baseNode.attributes();
        Type baseType = baseAttrs.type();

        StructType structType;
        if (baseType instanceof StructType st) {
          structType = st;
        } else if (baseType instanceof PointerType ptr
            && ptr.baseType() instanceof StructType st) {
          structType = st;
        } else {
          throw new IllegalArgumentException("Base type is not a struct: " + baseType);
        }

        String structName = structNameRegistry.getStructName(structType.tag(), structType);
        IrType fieldType = TypeMapper.toIrType(structType.getFieldType(fieldName), structNameRegistry);

        IrRhs.AddrField addrField =
            new IrRhs.AddrField(baseAddr, structName, fieldName, new IrPointerType(fieldType));
        IrTemp result = builder.tempFactory().newTemp(addrField.resultType());
        builder.addInstruction(new IrInstruction.IrAssignInstr(result, addrField));
        return result;
      }
    }

    // Parentheses: L_ZAGRADA <izraz> D_ZAGRADA
    if (children.size() == 3) {
      ParseNode first = children.get(0);
      ParseNode second = children.get(1);
      ParseNode third = children.get(2);
      if (first instanceof TerminalNode term1
          && term1.symbol().equals("L_ZAGRADA")
          && second instanceof NonTerminalNode inner
          && third instanceof TerminalNode term3
          && term3.symbol().equals("D_ZAGRADA")) {
        return emitLValue(inner, functionContext);
      }
    }

    // Postfix increment/decrement: <postfiks_izraz> OP_INC or OP_DEC
    if (children.size() == 2) {
      ParseNode second = children.get(1);
      if (second instanceof TerminalNode term) {
        if (term.symbol().equals("OP_INC") || term.symbol().equals("OP_DEC")) {
          NonTerminalNode baseNode =
              NodeUtils.asNonTerminal(children.get(0), "<postfiks_izraz>");
          return emitLValue(baseNode, functionContext);
        }
      }
    }

    // Fall back to primary expression
    if (children.get(0) instanceof NonTerminalNode nt
        && nt.symbol().equals("<primarni_izraz>")) {
      return emitLValuePrimary(nt, functionContext);
    }

    throw new IllegalArgumentException("Cannot emit l-value for postfix expression");
  }

  /**
   * Emits l-value for a variable by name.
   */
  public IrTemp emitLValueForVariable(
      String varName, Type varType, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    IrFunctionBuilder builder = functionContext.functionBuilder();
    if (builder.getCurrentBlockLabel() == null) {
      builder.startNewBlock();
    }

    String actualVarName = variableNameManager.getActualName(varName);
    IrSymbolRef.Kind kind =
        SymbolResolver.determineSymbolKind(
            varName, actualVarName, builder, functionContext.functionScope(), globalScope);

    // Check block-local address cache first (for reusing addresses within the same block)
    String symbolRefKey = BlockLocalSymbolAddressCache.createSymbolRefKey(kind, actualVarName);
    hr.fer.ppj.ir.util.BlockLocalSymbolAddressCache blockCache =
        functionContext.blockLocalAddressCache();
    IrTemp cachedAddr = blockCache.get(symbolRefKey);
    if (cachedAddr != null) {
      return cachedAddr;
    }

    // Check for last load address (for reusing address used in right side evaluation)
    // This is checked here because emitLValueForVariable is called when no reuse context
    // is set up, but we still want to reuse addresses from recent loads
    IrTemp lastLoadAddr = addressReuseContext.getLastLoadAddress(varName);
    if (lastLoadAddr != null) {
      // Also cache it in block-local cache for future reuse in this block
      blockCache.put(symbolRefKey, lastLoadAddr);
      return lastLoadAddr;
    }

    // Create new address temp
    IrSymbolRef symbolRef = new IrSymbolRef(kind, actualVarName);
    IrType irType = TypeMapper.toIrType(varType);
    IrRhs.AddrOfSymbol addrOf = new IrRhs.AddrOfSymbol(symbolRef, new IrPointerType(irType));
    IrTemp addrTemp = builder.tempFactory().newTemp(addrOf.resultType());
    builder.addInstruction(new IrInstruction.IrAssignInstr(addrTemp, addrOf));

    // Cache it in block-local cache for future reuse in this block
    blockCache.put(symbolRefKey, addrTemp);

    return addrTemp;
  }
}
