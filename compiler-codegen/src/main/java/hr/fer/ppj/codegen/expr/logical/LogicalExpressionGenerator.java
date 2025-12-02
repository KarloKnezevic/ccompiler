package hr.fer.ppj.codegen.expr.logical;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for logical expressions with short-circuit evaluation.
 * 
 * <p>This class handles the generation of code for logical operators:
 * <ul>
 *   <li>Logical OR (||) - short-circuits if left operand is true</li>
 *   <li>Logical AND (&&) - short-circuits if left operand is false</li>
 * </ul>
 * 
 * <p>Short-circuit evaluation means that the right operand is only evaluated
 * if necessary. For ||, if the left operand is true (non-zero), the result
 * is immediately true and the right operand is skipped. For &&, if the left
 * operand is false (zero), the result is immediately false and the right
 * operand is skipped.
 * 
 * <p>Logical expressions return 1 (true) or 0 (false) in register R0.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LogicalExpressionGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator expressionGenerator;
    
    /**
     * Creates a new logical expression generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the main expression generator for recursive calls
     */
    public LogicalExpressionGenerator(CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
    }
    
    /**
     * Generates code for logical OR expressions (||) with short-circuit evaluation.
     * 
     * <p>Short-circuit logic:
     * <ol>
     *   <li>Evaluate left operand</li>
     *   <li>If left is true (non-zero), jump to true label, result is 1</li>
     *   <li>Otherwise, evaluate right operand</li>
     *   <li>If right is true (non-zero), result is 1, else result is 0</li>
     * </ol>
     * 
     * @param node the logical OR expression node
     */
    public void generateLogicalOrExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Binary logical OR: <log_ili_izraz> OP_ILI <log_i_izraz>
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            var labels = context.labelGenerator().generateShortCircuitLabels();
            
            // Evaluate left operand
            expressionGenerator.generateExpression(left);
            context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
            
            // If left is true (non-zero), short-circuit to true
            context.emitter().emitInstruction("JP_NE", labels.trueLabel(), null, "if left is true, result is true");
            
            // Left is false, evaluate right operand
            expressionGenerator.generateExpression(right);
            context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
            
            // If right is true, result is 1, else 0
            context.emitter().emitInstruction("JP_NE", labels.trueLabel(), null, "if right is true, result is true");
            context.emitter().emitInstruction("MOVE", "%D 0", "R0", "result is false");
            context.emitter().emitInstruction("JP", labels.endLabel(), null, null);
            
            // True case
            context.emitter().emitLabel(labels.trueLabel());
            context.emitter().emitInstruction("MOVE", "%D 1", "R0", "result is true");
            
            context.emitter().emitLabel(labels.endLabel());
        }
    }
    
    /**
     * Generates code for logical AND expressions (&&) with short-circuit evaluation.
     * 
     * <p>Short-circuit logic:
     * <ol>
     *   <li>Evaluate left operand</li>
     *   <li>If left is false (zero), jump to false label, result is 0</li>
     *   <li>Otherwise, evaluate right operand</li>
     *   <li>If right is true (non-zero), result is 1, else result is 0</li>
     * </ol>
     * 
     * @param node the logical AND expression node
     */
    public void generateLogicalAndExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Binary logical AND: <log_i_izraz> OP_I <bin_ili_izraz>
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            var labels = context.labelGenerator().generateShortCircuitLabels();
            
            // Evaluate left operand
            expressionGenerator.generateExpression(left);
            context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
            
            // If left is false (zero), short-circuit to false
            context.emitter().emitInstruction("JP_EQ", labels.falseLabel(), null, "if left is false, result is false");
            
            // Left is true, evaluate right operand
            expressionGenerator.generateExpression(right);
            context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
            
            // If right is true, result is 1, else 0
            context.emitter().emitInstruction("JP_NE", labels.trueLabel(), null, "if right is true, result is true");
            context.emitter().emitInstruction("MOVE", "%D 0", "R0", "result is false");
            context.emitter().emitInstruction("JP", labels.endLabel(), null, null);
            
            // True case
            context.emitter().emitLabel(labels.trueLabel());
            context.emitter().emitInstruction("MOVE", "%D 1", "R0", "result is true");
            context.emitter().emitInstruction("JP", labels.endLabel(), null, null);
            
            // False case
            context.emitter().emitLabel(labels.falseLabel());
            context.emitter().emitInstruction("MOVE", "%D 0", "R0", "result is false");
            
            context.emitter().emitLabel(labels.endLabel());
        }
    }
}

