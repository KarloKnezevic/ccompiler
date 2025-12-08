package hr.fer.ppj.codegen.expr.binary;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.utils.OperandEvaluator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.util.Objects;

/**
 * Generates code for comparison operations (==, !=, <, >, <=, >=).
 * 
 * <p>Comparison operations return 1 (true) or 0 (false) in R0.
 * The comparison is performed using CMP instruction, followed by
 * conditional jumps to set the result.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ComparisonOperationGenerator {
    
    private final CodeGenContext context;
    private final OperandEvaluator operandEvaluator;
    
    /**
     * Creates a new comparison operation generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the expression generator for recursive calls
     */
    public ComparisonOperationGenerator(CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.operandEvaluator = new OperandEvaluator(context, expressionGenerator);
    }
    
    /**
     * Generates code for binary comparison operations.
     * 
     * @param left the left operand expression
     * @param right the right operand expression
     * @param operator the comparison operator (OP_EQ, OP_NEQ, OP_LT, OP_GT, OP_LTE, OP_GTE)
     */
    public void generateComparison(NonTerminalNode left, NonTerminalNode right, String operator) {
        operandEvaluator.evaluateOperands(left, right);
        
        // Compare operands
        context.emitter().emitInstruction("CMP", "R0", "R1", null);
        
        // Generate conditional result
        var labels = context.labelGenerator().generateShortCircuitLabels();
        
        String jumpCondition = switch (operator) {
            case "OP_EQ" -> "JP_EQ";
            case "OP_NEQ" -> "JP_NE";
            case "OP_LT" -> "JP_SLT";
            case "OP_GT" -> "JP_SGT";
            case "OP_LTE" -> "JP_SLE";
            case "OP_GTE" -> "JP_SGE";
            default -> "JP_EQ"; // fallback
        };
        
        context.emitter().emitInstruction(jumpCondition, labels.trueLabel(), null, 
                                        "comparison " + operator);
        
        // False case (decimal 0)
        context.emitter().emitInstruction("MOVE", "%D 0", "R0", "comparison result false");
        context.emitter().emitInstruction("JP", labels.endLabel(), null, null);
        
        // True case (decimal 1)
        context.emitter().emitLabel(labels.trueLabel());
        context.emitter().emitInstruction("MOVE", "%D 1", "R0", "comparison result true");
        
        context.emitter().emitLabel(labels.endLabel());
    }
}

