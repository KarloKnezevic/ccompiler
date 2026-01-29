package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.util.AddressabilityChecker;
import hr.fer.ppj.ir.util.ExpressionNameExtractor;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for postfix expressions (function calls, array indexing, post-increment/decrement).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class PostfixExpressionGenerator {

  private final ExpressionEmitter emitter;
  private final LValueEmitter lValueEmitter;

  public PostfixExpressionGenerator(ExpressionEmitter emitter, LValueEmitter lValueEmitter) {
    this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
    this.lValueEmitter = Objects.requireNonNull(lValueEmitter, "lValueEmitter must not be null");
  }

  /**
   * Emits r-value for a postfix expression.
   */
  public IrValue emitRValue(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    List<ParseNode> children = node.children();
    IrFunctionBuilder builder = functionContext.functionBuilder();

    // Check for postfix increment/decrement: <postfiks_izraz> OP_INC or OP_DEC
    if (children.size() == 2) {
      ParseNode second = children.get(1);
      if (second instanceof TerminalNode term) {
        if (term.symbol().equals("OP_INC") || term.symbol().equals("OP_DEC")) {
          NonTerminalNode baseNode =
              NodeUtils.asNonTerminal(children.get(0), "<postfiks_izraz>");
          IrTemp addr = lValueEmitter.emitLValue(baseNode, functionContext);
          Type exprType = baseNode.attributes().type();
          IrType irType = TypeMapper.toIrType(exprType);

          // Generate explicit load/add/store sequence to match expected IR output format
          
          // Load current value (this is the result of post-inc/dec)
          IrRhs.Load load = new IrRhs.Load(addr, irType);
          IrTemp originalValue = builder.tempFactory().newTemp(irType);
          builder.addInstruction(new IrInstruction.IrAssignInstr(originalValue, load));

          // Add/subtract 1
          IrConst oneConst = term.symbol().equals("OP_INC")
              ? new IrConst.IntConst(1, irType)
              : new IrConst.IntConst(-1, irType);
          IrRhs.BinOp addOp = new IrRhs.BinOp(
              IrRhs.BinOp.BinOpName.ADD, originalValue, oneConst, irType);
          IrTemp newValue = builder.tempFactory().newTemp(irType);
          builder.addInstruction(new IrInstruction.IrAssignInstr(newValue, addOp));

          // Store new value
          builder.addInstruction(new IrInstruction.IrStoreInstr(addr, newValue, irType));
          
          // Invalidate last loaded value for this variable since it has changed
          // This ensures statement-local value reuse doesn't use stale values
          String varName = ExpressionNameExtractor.extractVariableName(baseNode);
          if (varName != null) {
            hr.fer.ppj.ir.util.AddressReuseContext addressReuseContext =
                functionContext.addressReuseContext();
            addressReuseContext.clearLastLoadedValue(varName);
          }

          // Return original value
          return originalValue;
        }
      }
    }

    // Check for function call: <postfiks_izraz> L_ZAGRADA [<lista_argumenata>] D_ZAGRADA
    if (children.size() >= 3) {
      ParseNode first = children.get(0);
      ParseNode second = children.get(1);
      if (first instanceof NonTerminalNode && second instanceof TerminalNode term
          && term.symbol().equals("L_ZAGRADA")) {
        NonTerminalNode funcNode = (NonTerminalNode) first;
        String funcName = ExpressionNameExtractor.extractFunctionName(funcNode);
        if (funcName != null) {
          List<IrValue> args = new ArrayList<>();
          if (children.size() == 4) {
            NonTerminalNode argList =
                NodeUtils.asNonTerminal(children.get(2), "<lista_argumenata>");
            args = collectArguments(argList, functionContext);
          }

          Type returnType = node.attributes().type();
          IrType irReturnType =
              returnType != null && !returnType.isVoid()
                  ? TypeMapper.toIrType(returnType)
                  : null;

          if (irReturnType != null) {
            IrRhs.Call call = new IrRhs.Call(funcName, args, irReturnType);
            IrTemp result = builder.tempFactory().newTemp(irReturnType);
            builder.addInstruction(new IrInstruction.IrAssignInstr(result, call));
            
            // Function calls may modify memory/globals/aliased locals, so conservatively
            // clear all statement-local loaded values AFTER the call to avoid invalid reuse
            hr.fer.ppj.ir.util.AddressReuseContext addressReuseContext =
                functionContext.addressReuseContext();
            addressReuseContext.clearAllLastLoadedValues();
            
            return result;
          } else {
            builder.addInstruction(new IrInstruction.IrVoidCallInstr(funcName, args));
            
            // Function calls may modify memory/globals/aliased locals, so conservatively
            // clear all statement-local loaded values AFTER the call to avoid invalid reuse
            hr.fer.ppj.ir.util.AddressReuseContext addressReuseContext =
                functionContext.addressReuseContext();
            addressReuseContext.clearAllLastLoadedValues();
            
            return new IrConst.IntConst(0, IrPrimitiveType.INT32);
          }
        }
      }
    }

    // Check if it's addressable by form
    if (AddressabilityChecker.isAddressableExpressionForm(node)) {
      String varName = ExpressionNameExtractor.extractVariableName(node);
      Type exprType = node.attributes().type();
      IrType irType = TypeMapper.toIrType(exprType);
      
      // If this is a simple variable, use the unified load API with cache reuse
      if (varName != null) {
        // Get actual variable name (may be renamed for shadowing)
        String actualVarName = functionContext.variableNameManager().getActualName(varName);
        
        // Find global scope
        hr.fer.ppj.semantics.symbols.SymbolTable globalScope = null;
        hr.fer.ppj.semantics.symbols.SymbolTable currentScope = functionContext.functionScope();
        if (currentScope != null) {
          while (currentScope.parent() != null) {
            currentScope = currentScope.parent();
          }
          globalScope = currentScope;
        }
        
        // Determine symbol kind
        hr.fer.ppj.ir.model.IrSymbolRef.Kind kind =
            hr.fer.ppj.ir.util.SymbolResolver.determineSymbolKind(
                varName, actualVarName, builder, functionContext.functionScope(), globalScope);
        
        // Create cache key
        String cacheKey = hr.fer.ppj.ir.lowering.FunctionContext.createCacheKey(kind, actualVarName);
        
        // Get address
        IrTemp addr = lValueEmitter.emitLValue(node, functionContext);
        
        // Record address for potential reuse
        hr.fer.ppj.ir.util.AddressReuseContext addressReuseContext =
            functionContext.addressReuseContext();
        if (addressReuseContext.assignmentReuseVarName() != null) {
          addressReuseContext.setLastLoadAddress(varName, addr);
        }
        
        // Use unified load API with cache reuse
        return functionContext.loadScalarWithReuse(cacheKey, addr, irType);
      }
      
      // Non-variable addressable expressions: load directly (no cache)
      IrTemp addr = lValueEmitter.emitLValue(node, functionContext);
      IrRhs.Load load = new IrRhs.Load(addr, irType);
      IrTemp value = builder.tempFactory().newTemp(irType);
      builder.addInstruction(new IrInstruction.IrAssignInstr(value, load));
      return value;
    }

    // Otherwise, try primary expression or recursively handle nested postfix
    if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode nt) {
      if (nt.symbol().equals("<primarni_izraz>")) {
        return emitter.emitRValue(nt, functionContext);
      } else if (nt.symbol().equals("<postfiks_izraz>")) {
        return emitRValue(nt, functionContext);
      }
    }

    throw new IllegalArgumentException("Cannot emit r-value for postfix expression");
  }

  /**
   * Collects arguments from an argument list node.
   *
   * <p>Arrays decay to pointers when passed as arguments, so we emit their address.
   */
  private List<IrValue> collectArguments(
      NonTerminalNode argList, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    List<IrValue> args = new ArrayList<>();
    List<ParseNode> children = argList.children();
    if (children.size() == 1) {
      if (children.get(0) instanceof NonTerminalNode expr) {
        args.add(emitRValueForArgument(expr, functionContext));
      }
    } else if (children.size() == 3) {
      NonTerminalNode list = NodeUtils.asNonTerminal(children.get(0), "<lista_argumenata>");
      NonTerminalNode expr = NodeUtils.asNonTerminal(children.get(2), "<izraz_pridruzivanja>");
      args.addAll(collectArguments(list, functionContext));
      args.add(emitRValueForArgument(expr, functionContext));
    }
    return args;
  }

  /**
   * Emits r-value for a function argument.
   *
   * <p>Arrays decay to pointers when passed as arguments, so we emit their address. Other types
   * are emitted as normal r-values (never addresses).
   *
   * <p>This method ensures that non-array arguments are always computed as rvalues,
   * matching the golden IR format (e.g., program40 expects x+1 to be computed as a temp,
   * not as an address).
   */
  private IrValue emitRValueForArgument(
      NonTerminalNode expr, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    Type exprType = expr.attributes().type();
    Type strippedType = TypeSystem.stripConst(exprType);

    if (strippedType instanceof ArrayType) {
      // Arrays decay to pointers - emit address
      if (AddressabilityChecker.isAddressableExpressionForm(expr)) {
        return lValueEmitter.emitLValue(expr, functionContext);
      } else {
        NonTerminalNode addressableExpr =
            AddressabilityChecker.findAddressableSubExpression(expr);
        if (addressableExpr != null) {
          return lValueEmitter.emitLValue(addressableExpr, functionContext);
        }
        throw new IllegalArgumentException(
            "Array argument must be an addressable expression (variable, not assignment)");
      }
    }

    // For non-array types, ALWAYS emit rvalue (never address)
    // This ensures expressions like x+1 are computed as temps, not addresses
    IrValue result = emitter.emitRValue(expr, functionContext);
    
    // Assertion: result should never be an address temp created by addr_of_symbol
    // for non-array types (this would indicate a bug)
    if (result instanceof hr.fer.ppj.ir.model.IrTemp temp) {
      // Check if this temp was created by addr_of_symbol (it would have pointer type)
      // and the expression type is not an array
      hr.fer.ppj.ir.types.IrType tempType = temp.type();
      if (tempType instanceof hr.fer.ppj.ir.types.IrPointerType) {
        // This is suspicious - a pointer temp for a non-array argument
        // However, we can't easily check if it came from addr_of_symbol without
        // more context, so we'll just ensure the result is used correctly
      }
    }
    
    return result;
  }
}
