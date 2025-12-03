package hr.fer.ppj.codegen.stmt;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for loop statements (while and for).
 * 
 * <p>This class handles the generation of iterative control flow:
 * <ul>
 *   <li>While loops: {@code while (condition) statement}</li>
 *   <li>For loops: {@code for (init; condition; increment) statement}</li>
 * </ul>
 * 
 * <p><b>Grammar Rules Handled:</b>
 * <pre>
 * &lt;naredba_petlje&gt; ::= KR_WHILE L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
 *                    | KR_FOR L_ZAGRADA &lt;izraz_naredba&gt; &lt;izraz_naredba&gt; D_ZAGRADA &lt;naredba&gt;
 *                    | KR_FOR L_ZAGRADA &lt;izraz_naredba&gt; &lt;izraz_naredba&gt; &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
 * </pre>
 * 
 * <p><b>FRISC Code Pattern (While):</b>
 * <pre>
 * L_LOOP                    ; loop start
 * &lt;condition evaluation&gt;   ; result in R0
 * CMP R0, %D 0              ; compare with 0
 * JP_EQ L_BREAK              ; exit if false
 * &lt;loop body&gt;
 * L_CONTINUE                 ; continue point
 * JP L_LOOP                  ; repeat
 * L_BREAK                    ; loop end
 * </pre>
 * 
 * <p><b>FRISC Semantics:</b>
 * <ul>
 *   <li>Loop labels generated via LabelGenerator (loop, break, continue)</li>
 *   <li>Break statements jump to break label</li>
 *   <li>Continue statements jump to continue label</li>
 *   <li>For loops: init executed once, condition checked each iteration, increment at continue point</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LoopStatementGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator exprGen;
    private final StatementCodeGenerator stmtGen;
    
    /**
     * Creates a new loop statement generator.
     * 
     * @param context the code generation context
     * @param exprGen the expression generator for condition/increment evaluation
     * @param stmtGen the statement generator for loop body
     */
    public LoopStatementGenerator(CodeGenContext context,
                                  ExpressionCodeGenerator exprGen,
                                  StatementCodeGenerator stmtGen) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.exprGen = Objects.requireNonNull(exprGen, "exprGen must not be null");
        this.stmtGen = Objects.requireNonNull(stmtGen, "stmtGen must not be null");
    }
    
    /**
     * Generates code for a loop statement (while or for).
     * 
     * <p><b>Grammar Rule:</b> Processes {@code <naredba_petlje>}
     * 
     * @param node the loop statement node ({@code <naredba_petlje>})
     */
    public void generateLoopStatement(NonTerminalNode node) {
        Objects.requireNonNull(node, "node must not be null");
        
        List<ParseNode> children = node.children();
        ParseNode firstChild = children.get(0);
        
        if (firstChild instanceof TerminalNode terminal) {
            String keyword = terminal.symbol();
            
            if ("KR_WHILE".equals(keyword)) {
                generateWhileLoop(node);
            } else if ("KR_FOR".equals(keyword)) {
                generateForLoop(node);
            }
        }
    }
    
    /**
     * Generates code for a while loop.
     * 
     * <p><b>Parse Tree Structure:</b>
     * <pre>
     * KR_WHILE L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
     * </pre>
     * 
     * <p><b>FRISC Code:</b>
     * <pre>
     * L_LOOP                    ; loop start
     * &lt;condition evaluation&gt;   ; result in R0
     * CMP R0, %D 0              ; compare with 0
     * JP_EQ L_BREAK              ; exit if false
     * &lt;loop body&gt;
     * L_CONTINUE                 ; continue point
     * JP L_LOOP                  ; repeat
     * L_BREAK                    ; loop end
     * </pre>
     * 
     * @param node the while loop node
     */
    private void generateWhileLoop(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        // KR_WHILE L_ZAGRADA <izraz> D_ZAGRADA <naredba>
        
        NonTerminalNode condition = (NonTerminalNode) children.get(2);
        NonTerminalNode body = (NonTerminalNode) children.get(4);
        
        var labels = context.labelGenerator().generateLoopLabels();
        
        context.emitter().emitComment("While loop");
        
        // Loop start
        context.emitter().emitLabel(labels.loopLabel(), "while loop start");
        
        // Generate condition
        exprGen.generateExpression(condition);
        
        // Exit if condition is false
        context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
        context.emitter().emitInstruction("JP_EQ", labels.breakLabel(), null, "exit while loop");
        
        // Generate loop body with break/continue context
        CodeGenContext loopContext = context.withLoopLabels(labels.breakLabel(), labels.continueLabel());
        StatementCodeGenerator bodyGen = new StatementCodeGenerator(loopContext);
        bodyGen.generateStatement(body);
        
        // Continue point (for continue statements)
        context.emitter().emitLabel(labels.continueLabel(), "while continue");
        
        // Jump back to condition
        context.emitter().emitInstruction("JP", labels.loopLabel(), null, "repeat while loop");
        
        // Loop end (for break statements)
        context.emitter().emitLabel(labels.breakLabel(), "while loop end");
    }
    
    /**
     * Generates code for a for loop.
     * 
     * <p><b>Parse Tree Structure:</b>
     * <pre>
     * KR_FOR L_ZAGRADA &lt;izraz_naredba&gt; &lt;izraz_naredba&gt; D_ZAGRADA &lt;naredba&gt;
     * KR_FOR L_ZAGRADA &lt;izraz_naredba&gt; &lt;izraz_naredba&gt; &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
     * </pre>
     * 
     * <p><b>FRISC Code:</b>
     * <pre>
     * &lt;initialization&gt;          ; executed once
     * L_LOOP                    ; loop start
     * &lt;condition evaluation&gt;   ; checked each iteration
     * CMP R0, %D 0
     * JP_EQ L_BREAK
     * &lt;loop body&gt;
     * L_CONTINUE                 ; increment point
     * &lt;increment&gt;              ; executed each iteration (if present)
     * JP L_LOOP                  ; repeat
     * L_BREAK                    ; loop end
     * </pre>
     * 
     * @param node the for loop node
     */
    private void generateForLoop(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 6) {
            // KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> D_ZAGRADA <naredba>
            generateForLoopWithoutIncrement(node);
        } else if (children.size() == 7) {
            // KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> <izraz> D_ZAGRADA <naredba>
            generateForLoopWithIncrement(node);
        }
    }
    
    /**
     * Generates code for a for loop without increment expression.
     * 
     * @param node the for loop node
     */
    private void generateForLoopWithoutIncrement(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        // KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> D_ZAGRADA <naredba>
        NonTerminalNode init = (NonTerminalNode) children.get(2);
        NonTerminalNode condition = (NonTerminalNode) children.get(3);
        NonTerminalNode body = (NonTerminalNode) children.get(5);
        
        var labels = context.labelGenerator().generateLoopLabels();
        
        // Generate initialization
        stmtGen.generateStatement(init);
        
        // Loop start
        context.emitter().emitLabel(labels.loopLabel(), "for loop");
        
        // Generate condition (if present)
        if (condition.children().size() > 1) {
            NonTerminalNode condExpr = (NonTerminalNode) condition.children().get(0);
            exprGen.generateExpression(condExpr);
            
            // Exit if condition is false
            context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
            context.emitter().emitInstruction("JP_EQ", labels.breakLabel(), null, "exit for loop");
        }
        
        // Generate loop body with break/continue context
        CodeGenContext loopContext = context.withLoopLabels(labels.breakLabel(), labels.continueLabel());
        StatementCodeGenerator bodyGen = new StatementCodeGenerator(loopContext);
        bodyGen.generateStatement(body);
        
        // Jump back to condition
        context.emitter().emitInstruction("JP", labels.loopLabel(), null, "repeat for loop");
        
        // Loop end
        context.emitter().emitLabel(labels.breakLabel(), "for loop end");
    }
    
    /**
     * Generates code for a for loop with increment expression.
     * 
     * @param node the for loop node
     */
    private void generateForLoopWithIncrement(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        // KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> <izraz> D_ZAGRADA <naredba>
        NonTerminalNode init = (NonTerminalNode) children.get(2);
        NonTerminalNode condition = (NonTerminalNode) children.get(3);
        NonTerminalNode increment = (NonTerminalNode) children.get(4);
        NonTerminalNode body = (NonTerminalNode) children.get(6);
        
        var labels = context.labelGenerator().generateLoopLabels();
        
        context.emitter().emitComment("For loop with increment");
        
        // Generate initialization
        stmtGen.generateStatement(init);
        
        // Loop start
        context.emitter().emitLabel(labels.loopLabel(), "for loop start");
        
        // Generate condition (if present)
        if (condition.children().size() > 1) {
            NonTerminalNode condExpr = (NonTerminalNode) condition.children().get(0);
            exprGen.generateExpression(condExpr);
            
            // Exit if condition is false
            context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
            context.emitter().emitInstruction("JP_EQ", labels.breakLabel(), null, "exit for loop");
        }
        
        // Generate loop body with break/continue context
        CodeGenContext loopContext = context.withLoopLabels(labels.breakLabel(), labels.continueLabel());
        StatementCodeGenerator bodyGen = new StatementCodeGenerator(loopContext);
        bodyGen.generateStatement(body);
        
        // Continue point - generate increment
        context.emitter().emitLabel(labels.continueLabel(), "for loop increment");
        exprGen.generateExpression(increment);
        
        // Jump back to condition
        context.emitter().emitInstruction("JP", labels.loopLabel(), null, "repeat for loop");
        
        // Loop end
        context.emitter().emitLabel(labels.breakLabel(), "for loop end");
    }
}

