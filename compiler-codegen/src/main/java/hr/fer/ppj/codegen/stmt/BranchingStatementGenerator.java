package hr.fer.ppj.codegen.stmt;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for branching statements (if-else).
 * 
 * <p>This class handles the generation of conditional control flow:
 * <ul>
 *   <li>If statements: {@code if (condition) statement}</li>
 *   <li>If-else statements: {@code if (condition) statement1 else statement2}</li>
 * </ul>
 * 
 * <p><b>Grammar Rules Handled:</b>
 * <pre>
 * &lt;naredba_grananja&gt; ::= KR_IF L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
 *                        | KR_IF L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt; KR_ELSE &lt;naredba&gt;
 * </pre>
 * 
 * <p><b>FRISC Code Pattern:</b>
 * <pre>
 * ; If statement
 * &lt;condition evaluation&gt;     ; result in R0
 * CMP R0, %D 0              ; compare with 0
 * JP_EQ L_END                ; jump to end if false
 * &lt;then statement&gt;
 * L_END                      ; end label
 * </pre>
 * 
 * <p><b>FRISC Semantics:</b>
 * <ul>
 *   <li>Condition evaluated as boolean (0 = false, non-zero = true)</li>
 *   <li>CMP instruction sets flags based on R0 - 0</li>
 *   <li>JP_EQ jumps if condition is false (R0 == 0)</li>
 *   <li>Labels generated via LabelGenerator for unique names</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class BranchingStatementGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator exprGen;
    private final StatementCodeGenerator stmtGen;
    
    /**
     * Creates a new branching statement generator.
     * 
     * @param context the code generation context
     * @param exprGen the expression generator for condition evaluation
     * @param stmtGen the statement generator for then/else branches
     */
    public BranchingStatementGenerator(CodeGenContext context, 
                                       ExpressionCodeGenerator exprGen,
                                       StatementCodeGenerator stmtGen) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.exprGen = Objects.requireNonNull(exprGen, "exprGen must not be null");
        this.stmtGen = Objects.requireNonNull(stmtGen, "stmtGen must not be null");
    }
    
    /**
     * Generates code for a branching statement (if or if-else).
     * 
     * <p><b>Grammar Rule:</b> Processes {@code <naredba_grananja>}
     * 
     * @param node the branching statement node ({@code <naredba_grananja>})
     */
    public void generateBranchingStatement(NonTerminalNode node) {
        Objects.requireNonNull(node, "node must not be null");
        
        List<ParseNode> children = node.children();
        
        if (children.size() == 5) {
            // KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba>
            generateIfStatement(node);
        } else if (children.size() == 7) {
            // KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba> KR_ELSE <naredba>
            generateIfElseStatement(node);
        }
    }
    
    /**
     * Generates code for an if statement without else clause.
     * 
     * <p><b>Parse Tree Structure:</b>
     * <pre>
     * KR_IF L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
     * </pre>
     * 
     * <p><b>FRISC Code:</b>
     * <pre>
     * ; If statement
     * &lt;condition evaluation&gt;     ; result in R0
     * CMP R0, %D 0              ; compare with 0
     * JP_EQ L_END                ; jump to end if false
     * &lt;then statement&gt;
     * L_END                      ; end label
     * </pre>
     * 
     * @param node the if statement node
     */
    private void generateIfStatement(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        // KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba>
        NonTerminalNode condition = (NonTerminalNode) children.get(2);
        NonTerminalNode thenStmt = (NonTerminalNode) children.get(4);
        
        var labels = context.labelGenerator().generateIfLabels();
        
        context.emitter().emitComment("If statement");
        
        // Generate condition
        exprGen.generateExpression(condition);
        
        // Jump to end if condition is false
        context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
        context.emitter().emitInstruction("JP_EQ", labels.endLabel(), null, "if condition is false");
        
        // Generate then statement
        stmtGen.generateStatement(thenStmt);
        
        context.emitter().emitLabel(labels.endLabel(), "end if");
    }
    
    /**
     * Generates code for an if-else statement.
     * 
     * <p><b>Parse Tree Structure:</b>
     * <pre>
     * KR_IF L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt; KR_ELSE &lt;naredba&gt;
     * </pre>
     * 
     * <p><b>FRISC Code:</b>
     * <pre>
     * ; If-else statement
     * &lt;condition evaluation&gt;     ; result in R0
     * CMP R0, %D 0              ; compare with 0
     * JP_EQ L_ELSE               ; jump to else if false
     * &lt;then statement&gt;
     * JP L_END                   ; skip else
     * L_ELSE                     ; else label
     * &lt;else statement&gt;
     * L_END                      ; end label
     * </pre>
     * 
     * @param node the if-else statement node
     */
    private void generateIfElseStatement(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        // KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba> KR_ELSE <naredba>
        NonTerminalNode condition = (NonTerminalNode) children.get(2);
        NonTerminalNode thenStmt = (NonTerminalNode) children.get(4);
        NonTerminalNode elseStmt = (NonTerminalNode) children.get(6);
        
        var labels = context.labelGenerator().generateIfLabels();
        
        context.emitter().emitComment("If-else statement");
        
        // Generate condition
        exprGen.generateExpression(condition);
        
        // Jump to else if condition is false
        context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
        context.emitter().emitInstruction("JP_EQ", labels.elseLabel(), null, "if condition is false");
        
        // Generate then statement
        stmtGen.generateStatement(thenStmt);
        context.emitter().emitInstruction("JP", labels.endLabel(), null, "skip else");
        
        // Generate else statement
        context.emitter().emitLabel(labels.elseLabel(), "else clause");
        stmtGen.generateStatement(elseStmt);
        
        context.emitter().emitLabel(labels.endLabel(), "end if-else");
    }
}

