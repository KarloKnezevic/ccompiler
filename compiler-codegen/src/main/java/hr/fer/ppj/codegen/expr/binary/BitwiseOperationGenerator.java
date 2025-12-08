package hr.fer.ppj.codegen.expr.binary;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.utils.OperandEvaluator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.util.Objects;

/**
 * Generates code for bitwise operations (|, ^, &).
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class BitwiseOperationGenerator {
    
    private final CodeGenContext context;
    private final OperandEvaluator operandEvaluator;
    
    /**
     * Creates a new bitwise operation generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the expression generator for recursive calls
     */
    public BitwiseOperationGenerator(CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.operandEvaluator = new OperandEvaluator(context, expressionGenerator);
    }
    
    /**
     * Generates code for bitwise OR operation (|).
     * 
     * @param left the left operand
     * @param right the right operand
     */
    public void generateBitwiseOr(NonTerminalNode left, NonTerminalNode right) {
        operandEvaluator.evaluateOperands(left, right);
        context.emitter().emitInstruction("OR", "R0", "R1", "R0", "bitwise OR");
    }
    
    /**
     * Generates code for bitwise XOR operation (^).
     * 
     * @param left the left operand
     * @param right the right operand
     */
    public void generateBitwiseXor(NonTerminalNode left, NonTerminalNode right) {
        operandEvaluator.evaluateOperands(left, right);
        context.emitter().emitInstruction("XOR", "R0", "R1", "R0", "bitwise XOR");
    }
    
    /**
     * Generates code for bitwise AND operation (&).
     * 
     * @param left the left operand
     * @param right the right operand
     */
    public void generateBitwiseAnd(NonTerminalNode left, NonTerminalNode right) {
        operandEvaluator.evaluateOperands(left, right);
        context.emitter().emitInstruction("AND", "R0", "R1", "R0", "bitwise AND");
    }
}

