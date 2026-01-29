package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.util.ConstantEvaluator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.SemanticAttributes;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for primary expressions (literals, identifiers, parentheses).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class PrimaryExpressionGenerator {

  private final ExpressionEmitter emitter;
  private final LValueEmitter lValueEmitter;

  public PrimaryExpressionGenerator(ExpressionEmitter emitter) {
    this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
    if (emitter instanceof LValueEmitter lve) {
      this.lValueEmitter = lve;
    } else {
      throw new IllegalArgumentException("emitter must implement LValueEmitter");
    }
  }

  /**
   * Emits r-value for a primary expression.
   *
   * <p>Handles:
   * <ul>
   *   <li>Identifiers (variables)</li>
   *   <li>Literals (integers, floats, characters)</li>
   *   <li>Parenthesized expressions</li>
   * </ul>
   */
  public IrValue emitRValue(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    List<ParseNode> children = node.children();
    if (children.isEmpty()) {
      throw new IllegalArgumentException("Empty primary expression");
    }

    ParseNode firstChild = children.get(0);

    // Check for parentheses: L_ZAGRADA <izraz> D_ZAGRADA
    if (children.size() == 3) {
      ParseNode first = children.get(0);
      ParseNode second = children.get(1);
      ParseNode third = children.get(2);
      if (first instanceof TerminalNode term1 && term1.symbol().equals("L_ZAGRADA")
          && second instanceof NonTerminalNode inner
          && third instanceof TerminalNode term3 && term3.symbol().equals("D_ZAGRADA")) {
        // Parentheses: forward to inner expression
        return emitter.emitRValue(inner, functionContext);
      }
    }

    SemanticAttributes attrs = node.attributes();

    // Check for identifier
    if (firstChild instanceof TerminalNode term && term.symbol().equals("IDN")) {
      String varName = term.lexeme();
      Type varType = attrs.type();
      IrType irType = TypeMapper.toIrType(varType);

      hr.fer.ppj.ir.util.AddressReuseContext addressReuseContext =
          functionContext.addressReuseContext();
      IrFunctionBuilder builder = functionContext.functionBuilder();
      
      // Get actual variable name (may be renamed for shadowing)
      String actualVarName = functionContext.variableNameManager().getActualName(varName);
      
      // Find global scope by traversing up the parent chain
      hr.fer.ppj.semantics.symbols.SymbolTable globalScope = null;
      hr.fer.ppj.semantics.symbols.SymbolTable currentScope = functionContext.functionScope();
      if (currentScope != null) {
        while (currentScope.parent() != null) {
          currentScope = currentScope.parent();
        }
        globalScope = currentScope;
      }
      
      // Determine symbol kind for fully-qualified cache key
      hr.fer.ppj.ir.model.IrSymbolRef.Kind kind =
          hr.fer.ppj.ir.util.SymbolResolver.determineSymbolKind(
              varName,
              actualVarName,
              builder,
              functionContext.functionScope(),
              globalScope);
      
      // Create fully-qualified cache key (e.g., "param:m", "local:x", "global:g")
      String cacheKey = hr.fer.ppj.ir.lowering.FunctionContext.createCacheKey(kind, actualVarName);

      // Get address of variable (emitLValuePrimary will check for reuse)
      IrTemp addrTemp = lValueEmitter.emitLValue(node, functionContext);

      // Record this address for potential reuse in assignments
      addressReuseContext.setLastLoadAddress(varName, addrTemp);

      // Use unified load API with cache reuse
      return functionContext.loadScalarWithReuse(cacheKey, addrTemp, irType);
    }

    // Check for constant (BROJ, ZNAK, NIZ_ZNAKOVA)
    if (firstChild instanceof TerminalNode term) {
      String termSymbol = term.symbol();
      String lexeme = term.lexeme();

      // Check for number literal (BROJ)
      if (termSymbol.equals("BROJ")) {
        Type constType = attrs.type();
        if (constType == null) {
          constType = PrimitiveType.INT;
        }
        IrType irType = TypeMapper.toIrType(constType);

        if (constType == PrimitiveType.INT) {
          try {
            int value = Integer.parseInt(lexeme);
            return new IrConst.IntConst(value, irType);
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer literal: " + lexeme);
          }
        } else if (constType == PrimitiveType.FLOAT) {
          try {
            float value = Float.parseFloat(lexeme);
            return new IrConst.FloatConst(value);
          } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid float literal: " + lexeme);
          }
        }
      }

      // Check for character literal (ZNAK)
      if (termSymbol.equals("ZNAK")) {
        char value = ConstantEvaluator.parseCharLiteral(lexeme);
        return new IrConst.CharConst(value);
      }

      // Check for string literal (NIZ_ZNAKOVA) - not supported in IR yet
      if (termSymbol.equals("NIZ_ZNAKOVA")) {
        throw new UnsupportedOperationException("String literals not yet supported in IR");
      }

      // Fallback: check if attributes say it's a const value
      if (attrs.isConstValue()) {
        Type constType = attrs.type();
        IrType irType = TypeMapper.toIrType(constType);

        if (constType == PrimitiveType.INT) {
          try {
            int value = Integer.parseInt(lexeme);
            return new IrConst.IntConst(value, irType);
          } catch (NumberFormatException e) {
            // Invalid integer literal
          }
        } else if (constType == PrimitiveType.CHAR) {
          char value = ConstantEvaluator.parseCharLiteral(lexeme);
          return new IrConst.CharConst(value);
        } else if (constType == PrimitiveType.FLOAT) {
          try {
            float value = Float.parseFloat(lexeme);
            return new IrConst.FloatConst(value);
          } catch (NumberFormatException e) {
            // Invalid float literal
          }
        }
      }
    }

    throw new IllegalArgumentException("Cannot emit r-value for primary expression");
  }
}
