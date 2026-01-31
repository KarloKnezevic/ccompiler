package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrValue;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.build.TypeMapper;
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
 * Generates IR for assignment expressions.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class AssignmentExpressionGenerator {

  private final ExpressionEmitter emitter;
  private final LValueEmitter lValueEmitter;

  public AssignmentExpressionGenerator(ExpressionEmitter emitter, LValueEmitter lValueEmitter) {
    this.emitter = Objects.requireNonNull(emitter, "emitter must not be null");
    this.lValueEmitter = Objects.requireNonNull(lValueEmitter, "lValueEmitter must not be null");
  }

  /**
   * Emits r-value for an assignment expression.
   */
  public IrValue emitRValue(
      NonTerminalNode node, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    List<ParseNode> children = node.children();
    IrFunctionBuilder builder = functionContext.functionBuilder();

    if (children.size() >= 3) {
      ParseNode firstChild = children.get(0);
      ParseNode secondChild = children.get(1);
      if (secondChild instanceof TerminalNode term && term.symbol().equals("OP_PRIDRUZI")) {
        NonTerminalNode leftNode =
            NodeUtils.asNonTerminal(firstChild, "<unarni_izraz>");
        NonTerminalNode rightNode =
            NodeUtils.asNonTerminal(children.get(2), "<izraz_pridruzivanja>");

        String leftVarName = ExpressionNameExtractor.extractVariableName(leftNode);
        String rightFirstVar = ExpressionNameExtractor.extractFirstVariableName(rightNode);
        boolean shouldReuse =
            (leftVarName != null
                && rightFirstVar != null
                && leftVarName.equals(rightFirstVar));

        hr.fer.ppj.ir.util.AddressReuseContext addressReuseContext =
            functionContext.addressReuseContext();
        IrTemp savedReuseAddr = addressReuseContext.assignmentReuseAddr();
        String savedReuseVarName = addressReuseContext.assignmentReuseVarName();

        // Check if right side contains a cast and left side is array indexing
        // If so, evaluate in special order: base address, cast, indexed address
        boolean rightHasCast = containsCast(rightNode);
        boolean leftIsArrayIndex = isArrayIndexing(leftNode);
        boolean rightHasArrayIndex = containsArrayIndexing(rightNode);
        boolean rightHasPreInc = containsPreIncrement(rightNode);
        boolean leftIsSimpleVar = ExpressionNameExtractor.extractVariableName(leftNode) != null;
        
        // Set up assignment reuse context BEFORE evaluating right side (for the special case)
        // This allows PostfixExpressionGenerator to record addresses for variables loaded in the right side
        if (shouldReuse && rightHasArrayIndex && leftIsSimpleVar) {
          // Pre-set the assignment reuse variable name so that address recording happens during right side evaluation
          // The actual address will be set after evaluating the right side
          addressReuseContext.setAssignmentReuseVarName(leftVarName);
        }
        
        IrValue value;
        IrTemp addr;
        
        if (rightHasCast && leftIsArrayIndex) {
          // Special case: array indexing with cast
          // Order: base address, indexed address, cast
          IrTemp baseAddr = getArrayBaseAddress(leftNode, functionContext);
          addr = computeArrayIndexAddress(leftNode, baseAddr, functionContext);
          value = emitter.emitRValue(rightNode, functionContext);
        } else if (rightHasCast) {
          // Evaluate cast first, then compute address
          value = emitter.emitRValue(rightNode, functionContext);
          
          if (shouldReuse) {
            addr = lValueEmitter.emitLValue(leftNode, functionContext);
            addressReuseContext.setAssignmentReuse(addr, leftVarName);
          } else {
            addr = lValueEmitter.emitLValue(leftNode, functionContext);
          }
        } else if (rightHasPreInc && leftIsSimpleVar) {
          // Special case: right has pre-increment, left is simple variable
          // Evaluate pre-increment first, then get left address
          // This matches expected IR for expressions like x = ++y
          value = emitter.emitRValue(rightNode, functionContext);
          addr = lValueEmitter.emitLValue(leftNode, functionContext);
        } else if (leftIsSimpleVar) {
          // Check if this is a struct assignment - use standard order for structs
          Type leftTypeCheck = leftNode.attributes().type();
          Type strippedLeftType = TypeSystem.stripConst(leftTypeCheck);
          boolean isStructAssignment = strippedLeftType instanceof StructType;
          
          if (isStructAssignment) {
            // Struct assignment: use standard order (l-value first, then r-value)
            addr = lValueEmitter.emitLValue(leftNode, functionContext);
            value = emitter.emitRValue(rightNode, functionContext);
          } else {
            // When left side is a simple non-struct variable, evaluate r-value first
            // This matches expected IR order: compute value, then address, then store
            // When shouldReuse is true, we set up the assignment reuse context BEFORE evaluating
            // the right side, so that PostfixExpressionGenerator can record addresses for variables
            // loaded in the right side. Then we retrieve and reuse that address.
            value = emitter.emitRValue(rightNode, functionContext);
            
            // Get address after evaluating right side
            // If shouldReuse, try to reuse the address that was used in the right side evaluation
            if (shouldReuse) {
              IrTemp lastLoadAddr = addressReuseContext.getLastLoadAddress(leftVarName);
              if (lastLoadAddr != null) {
                // Reuse the address that was used when loading the variable in the right side
                addr = lastLoadAddr;
                // Clear the last load address and restore the saved assignment reuse context
                addressReuseContext.clearLastLoadAddress();
                addressReuseContext.setAssignmentReuse(savedReuseAddr, savedReuseVarName);
              } else {
                // Fallback: emitLValue will check for last load address automatically
                addr = lValueEmitter.emitLValue(leftNode, functionContext);
                addressReuseContext.setAssignmentReuse(savedReuseAddr, savedReuseVarName);
              }
            } else {
              addr = lValueEmitter.emitLValue(leftNode, functionContext);
            }
          }
        } else {
          // Standard order: left side (address) first, then right side (value)
          if (shouldReuse) {
            addr = lValueEmitter.emitLValue(leftNode, functionContext);
            addressReuseContext.setAssignmentReuse(addr, leftVarName);
          } else {
            addr = lValueEmitter.emitLValue(leftNode, functionContext);
          }
          
          try {
            value = emitter.emitRValue(rightNode, functionContext);
          } finally {
            addressReuseContext.setAssignmentReuse(savedReuseAddr, savedReuseVarName);
          }
        }

        Type leftType = leftNode.attributes().type();
        IrType irType = TypeMapper.toIrType(leftType);

        // Handle null pointer assignment: when assigning 0 to a pointer, use NullConst
        if (irType instanceof IrPointerType ptrType) {
          if (value instanceof hr.fer.ppj.ir.model.IrConst.IntConst intConst && intConst.value() == 0) {
            value = new hr.fer.ppj.ir.model.IrConst.NullConst(ptrType);
          }
        }

        builder.addInstruction(new IrInstruction.IrStoreInstr(addr, value, irType));
        
        // Invalidate last loaded value for this variable since it has changed
        // This ensures statement-local value reuse doesn't use stale values
        if (leftVarName != null) {
          addressReuseContext.clearLastLoadedValue(leftVarName);
        }
        
        return value;
      }
    }

    if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode nt) {
      return emitter.emitRValue(nt, functionContext);
    }

    throw new IllegalArgumentException("Cannot emit r-value for assignment expression");
  }
  
  private boolean hasSideEffects(NonTerminalNode node) {
    String symbol = node.symbol();
    // Check if node contains cast or preinc/predec
    if (symbol.equals("<cast_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() >= 4) {
        // Has explicit cast: L_ZAGRADA <ime_tipa> D_ZAGRADA <cast_izraz>
        return true;
      }
    }
    if (symbol.equals("<unarni_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() >= 2) {
        ParseNode first = children.get(0);
        if (first instanceof TerminalNode term) {
          if (term.symbol().equals("OP_INC") || term.symbol().equals("OP_DEC")) {
            return true;
          }
        } else if (first instanceof NonTerminalNode nt && nt.symbol().equals("<unarni_operator>")) {
          List<ParseNode> opChildren = nt.children();
          if (!opChildren.isEmpty() && opChildren.get(0) instanceof TerminalNode opTerm) {
            if (opTerm.symbol().equals("OP_INC") || opTerm.symbol().equals("OP_DEC")) {
              return true;
            }
          }
        }
      }
    }
    // Recursively check children
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt) {
        if (hasSideEffects(nt)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Checks if an expression node contains a cast operation.
   */
  private boolean containsCast(NonTerminalNode node) {
    String symbol = node.symbol();
    if (symbol.equals("<cast_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() >= 4) {
        // Has explicit cast: L_ZAGRADA <ime_tipa> D_ZAGRADA <cast_izraz>
        return true;
      }
    }
    // Recursively check children
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt) {
        if (containsCast(nt)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Checks if an expression node contains array indexing.
   */
  private boolean containsArrayIndexing(NonTerminalNode node) {
    String symbol = node.symbol();
    
    // Check if this is a postfix expression with array indexing
    if (symbol.equals("<postfiks_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() >= 3) {
        ParseNode second = children.get(1);
        if (second instanceof TerminalNode term && term.symbol().equals("L_UGL_ZAGRADA")) {
          return true;
        }
      }
    }
    
    // Recursively check children
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt) {
        if (containsArrayIndexing(nt)) {
          return true;
        }
      }
    }
    
    return false;
  }

  /**
   * Checks if an expression node contains pre-increment or pre-decrement.
   */
  private boolean containsPreIncrement(NonTerminalNode node) {
    String symbol = node.symbol();
    
    // Check if this is a unary expression with pre-increment/decrement
    if (symbol.equals("<unarni_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() >= 2) {
        ParseNode first = children.get(0);
        if (first instanceof TerminalNode term) {
          if (term.symbol().equals("OP_INC") || term.symbol().equals("OP_DEC")) {
            return true;
          }
        } else if (first instanceof NonTerminalNode nt && nt.symbol().equals("<unarni_operator>")) {
          List<ParseNode> opChildren = nt.children();
          if (!opChildren.isEmpty() && opChildren.get(0) instanceof TerminalNode opTerm) {
            if (opTerm.symbol().equals("OP_INC") || opTerm.symbol().equals("OP_DEC")) {
              return true;
            }
          }
        }
      }
    }
    
    // Recursively check children
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt) {
        if (containsPreIncrement(nt)) {
          return true;
        }
      }
    }
    
    return false;
  }

  /**
   * Checks if an expression node is array indexing.
   */
  private boolean isArrayIndexing(NonTerminalNode node) {
    String symbol = node.symbol();
    if (symbol.equals("<postfiks_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() >= 3) {
        ParseNode second = children.get(1);
        if (second instanceof TerminalNode term && term.symbol().equals("L_UGL_ZAGRADA")) {
          return true;
        }
      }
    }
    // Check if it's wrapped in unary/cast expression
    if (symbol.equals("<unarni_izraz>") || symbol.equals("<cast_izraz>")) {
      List<ParseNode> children = node.children();
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
        return isArrayIndexing(child);
      }
    }
    return false;
  }

  /**
   * Gets the base address of an array indexing expression.
   */
  private IrTemp getArrayBaseAddress(
      NonTerminalNode arrayIndexNode, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    // Unwrap if needed
    NonTerminalNode postfixNode = arrayIndexNode;
    if (arrayIndexNode.symbol().equals("<unarni_izraz>") || arrayIndexNode.symbol().equals("<cast_izraz>")) {
      List<ParseNode> children = arrayIndexNode.children();
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
        postfixNode = child;
      }
    }
    
    if (!postfixNode.symbol().equals("<postfiks_izraz>")) {
      throw new IllegalArgumentException("Not an array indexing expression");
    }
    
    List<ParseNode> children = postfixNode.children();
    if (children.size() < 3) {
      throw new IllegalArgumentException("Invalid array indexing expression");
    }
    
    NonTerminalNode baseNode = NodeUtils.asNonTerminal(children.get(0), "<postfiks_izraz>");
    
    // Get base address using l-value emitter
    return lValueEmitter.emitLValue(baseNode, functionContext);
  }

  /**
   * Computes the indexed address for an array indexing expression given the base address.
   */
  private IrTemp computeArrayIndexAddress(
      NonTerminalNode arrayIndexNode, IrTemp baseAddr, hr.fer.ppj.ir.lowering.FunctionContext functionContext) {
    // Unwrap if needed
    NonTerminalNode postfixNode = arrayIndexNode;
    if (arrayIndexNode.symbol().equals("<unarni_izraz>") || arrayIndexNode.symbol().equals("<cast_izraz>")) {
      List<ParseNode> children = arrayIndexNode.children();
      if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
        postfixNode = child;
      }
    }
    
    if (!postfixNode.symbol().equals("<postfiks_izraz>")) {
      throw new IllegalArgumentException("Not an array indexing expression");
    }
    
    List<ParseNode> children = postfixNode.children();
    if (children.size() < 3) {
      throw new IllegalArgumentException("Invalid array indexing expression");
    }
    
    NonTerminalNode baseNode = NodeUtils.asNonTerminal(children.get(0), "<postfiks_izraz>");
    NonTerminalNode indexNode = NodeUtils.asNonTerminal(children.get(2), "<izraz>");
    
    SemanticAttributes baseAttrs = baseNode.attributes();
    Type baseType = baseAttrs.type();
    
    IrValue index = emitter.emitRValue(indexNode, functionContext);
    
    Type elementType;
    Type strippedBaseType = hr.fer.ppj.semantics.types.TypeSystem.stripConst(baseType);
    if (strippedBaseType instanceof hr.fer.ppj.semantics.types.ArrayType arrayType) {
      elementType = arrayType.elementType();
    } else if (strippedBaseType instanceof hr.fer.ppj.semantics.types.PointerType pointerType) {
      elementType = pointerType.baseType();
    } else {
      throw new IllegalArgumentException("Base type is not array or pointer: " + baseType);
    }
    
    IrType irElementType = TypeMapper.toIrType(elementType);
    int elemSize = hr.fer.ppj.ir.build.TypeSizeCalculator.getTypeSize(irElementType);
    
    IrFunctionBuilder builder = functionContext.functionBuilder();
    IrRhs.AddrIndex addrIndex =
        new IrRhs.AddrIndex(baseAddr, index, elemSize, new IrPointerType(irElementType));
    IrTemp result = builder.tempFactory().newTemp(addrIndex.resultType());
    builder.addInstruction(new IrInstruction.IrAssignInstr(result, addrIndex));
    return result;
  }
}
