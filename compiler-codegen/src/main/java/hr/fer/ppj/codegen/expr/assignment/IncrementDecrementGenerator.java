package hr.fer.ppj.codegen.expr.assignment;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.env.VariableAddressResolver;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.util.Objects;

/**
 * Generates FRISC assembly code for increment and decrement operations.
 *
 * <p>This class handles:
 *
 * <ul>
 *   <li>Pre-increment (++var) - returns new value
 *   <li>Pre-decrement (--var) - returns new value
 *   <li>Post-increment (var++) - returns old value
 *   <li>Post-decrement (var--) - returns old value
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class IncrementDecrementGenerator {

  private final CodeGenContext context;
  private final ExpressionCodeGenerator expressionGenerator;
  private final VariableAddressResolver addressResolver;

  /**
   * Creates a new increment/decrement generator.
   *
   * @param context the code generation context
   * @param expressionGenerator the expression generator for complex operands
   */
  public IncrementDecrementGenerator(
      CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.expressionGenerator =
        Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
    this.addressResolver = new VariableAddressResolver(context);
  }

  /**
   * Generates code for pre-increment (++var). Returns the new value.
   *
   * @param operand the operand expression
   */
  public void generatePreIncrement(NonTerminalNode operand) {
    String variableName = addressResolver.extractVariableName(operand);

    if (variableName != null) {
      String address = addressResolver.getVariableAddress(variableName);
      context.emitter().emitInstruction("LOAD", "R0", address, "load " + variableName);
      context.emitter().emitInstruction("ADD", "R0", "%D 1", "R0", "pre-increment");
      context
          .emitter()
          .emitInstruction("STORE", "R0", address, "store incremented " + variableName);
    } else {
      expressionGenerator.generateExpression(operand);
      context.emitter().emitInstruction("ADD", "R0", "%D 1", "R0", "pre-increment");
    }
  }

  /**
   * Generates code for pre-decrement (--var). Returns the new value.
   *
   * @param operand the operand expression
   */
  public void generatePreDecrement(NonTerminalNode operand) {
    String variableName = addressResolver.extractVariableName(operand);

    if (variableName != null) {
      String address = addressResolver.getVariableAddress(variableName);
      context.emitter().emitInstruction("LOAD", "R0", address, "load " + variableName);
      context.emitter().emitInstruction("SUB", "R0", "%D 1", "R0", "pre-decrement");
      context
          .emitter()
          .emitInstruction("STORE", "R0", address, "store decremented " + variableName);
    } else {
      expressionGenerator.generateExpression(operand);
      context.emitter().emitInstruction("SUB", "R0", "%D 1", "R0", "pre-decrement");
    }
  }

  /**
   * Generates code for post-increment (var++). Returns the old value.
   *
   * @param operand the operand expression
   */
  public void generatePostIncrement(NonTerminalNode operand) {
    String variableName = addressResolver.extractVariableName(operand);

    if (variableName != null) {
      String address = addressResolver.getVariableAddress(variableName);
      context.emitter().emitInstruction("LOAD", "R0", address, "load " + variableName);
      context.emitter().emitInstruction("MOVE", "R0", "R1", "save old value");
      context.emitter().emitInstruction("ADD", "R0", "%D 1", "R0", "post-increment");
      context
          .emitter()
          .emitInstruction("STORE", "R0", address, "store incremented " + variableName);
      context.emitter().emitInstruction("MOVE", "R1", "R0", "return old value");
    } else {
      expressionGenerator.generateExpression(operand);
      context.emitter().emitInstruction("MOVE", "R0", "R1", "save old value");
      context.emitter().emitInstruction("ADD", "R0", "%D 1", "R0", "post-increment");
      context.emitter().emitInstruction("MOVE", "R1", "R0", "return old value");
    }
  }

  /**
   * Generates code for post-decrement (var--). Returns the old value.
   *
   * @param operand the operand expression
   */
  public void generatePostDecrement(NonTerminalNode operand) {
    String variableName = addressResolver.extractVariableName(operand);

    if (variableName != null) {
      String address = addressResolver.getVariableAddress(variableName);
      context.emitter().emitInstruction("LOAD", "R0", address, "load " + variableName);
      context.emitter().emitInstruction("MOVE", "R0", "R1", "save old value");
      context.emitter().emitInstruction("SUB", "R0", "%D 1", "R0", "post-decrement");
      context
          .emitter()
          .emitInstruction("STORE", "R0", address, "store decremented " + variableName);
      context.emitter().emitInstruction("MOVE", "R1", "R0", "return old value");
    } else {
      expressionGenerator.generateExpression(operand);
      context.emitter().emitInstruction("MOVE", "R0", "R1", "save old value");
      context.emitter().emitInstruction("SUB", "R0", "%D 1", "R0", "post-decrement");
      context.emitter().emitInstruction("MOVE", "R1", "R0", "return old value");
    }
  }
}
