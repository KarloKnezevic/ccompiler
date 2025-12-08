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
 * <p>This class handles the generation of iterative control flow, implementing the
 * <b>loop code generation algorithm</b> that translates C loop constructs into
 * FRISC assembly with proper label management and control flow.
 * 
 * <p><b>Algorithm: Loop Code Generation</b>
 * 
 * <p>Loops are translated using a <b>structured control flow pattern</b>:
 * <ol>
 *   <li><b>Label Generation:</b> Generate unique labels for loop start, continue point, and break point</li>
 *   <li><b>Context Setup:</b> Create a new code generation context with loop labels for break/continue</li>
 *   <li><b>Condition Check:</b> Evaluate condition at the start of each iteration</li>
 *   <li><b>Body Execution:</b> Generate code for loop body with break/continue support</li>
 *   <li><b>Iteration Control:</b> Jump back to condition check (continue) or exit loop (break)</li>
 * </ol>
 * 
 * <p><b>Loop Types Handled:</b>
 * <ul>
 *   <li><b>While Loops:</b> {@code while (condition) statement}</li>
 *   <li><b>For Loops:</b> {@code for (init; condition; increment) statement}</li>
 * </ul>
 * 
 * <p><b>Grammar Rules Handled:</b>
 * <pre>
 * &lt;naredba_petlje&gt; ::= KR_WHILE L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
 *                    | KR_FOR L_ZAGRADA &lt;izraz_naredba&gt; &lt;izraz_naredba&gt; D_ZAGRADA &lt;naredba&gt;
 *                    | KR_FOR L_ZAGRADA &lt;izraz_naredba&gt; &lt;izraz_naredba&gt; &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
 * </pre>
 * 
 * <p><b>While Loop Algorithm:</b>
 * 
 * <p>The while loop is translated as follows:
 * <ol>
 *   <li><b>Loop Start Label:</b> Marks the beginning of each iteration</li>
 *   <li><b>Condition Evaluation:</b> Evaluate condition, result in R0</li>
 *   <li><b>Exit Check:</b> If condition is false (R0 == 0), jump to break label</li>
 *   <li><b>Body Execution:</b> Generate loop body code (with break/continue context)</li>
 *   <li><b>Continue Label:</b> Marks the continue point (for continue statements)</li>
 *   <li><b>Loop Back:</b> Jump back to loop start label</li>
 *   <li><b>Break Label:</b> Marks the loop exit point (for break statements)</li>
 * </ol>
 * 
 * <p><b>For Loop Algorithm:</b>
 * 
 * <p>The for loop is translated as follows:
 * <ol>
 *   <li><b>Initialization:</b> Execute initialization expression once (before loop)</li>
 *   <li><b>Loop Start Label:</b> Marks the beginning of each iteration</li>
 *   <li><b>Condition Evaluation:</b> Evaluate condition, result in R0</li>
 *   <li><b>Exit Check:</b> If condition is false (R0 == 0), jump to break label</li>
 *   <li><b>Body Execution:</b> Generate loop body code (with break/continue context)</li>
 *   <li><b>Continue Label:</b> Marks the continue point (for continue statements and increment)</li>
 *   <li><b>Increment:</b> Execute increment expression (if present)</li>
 *   <li><b>Loop Back:</b> Jump back to loop start label</li>
 *   <li><b>Break Label:</b> Marks the loop exit point (for break statements)</li>
 * </ol>
 * 
 * <p><b>Break and Continue Statements:</b>
 * 
 * <p>Break and continue statements are handled via labels in the code generation context:
 * <ul>
 *   <li><b>Break:</b> Jumps to the break label, exiting the loop</li>
 *   <li><b>Continue:</b> Jumps to the continue label, skipping to the next iteration</li>
 * </ul>
 * 
 * <p>The loop generator creates a new context with these labels, allowing nested loops
 * to have their own break/continue labels.
 * 
 * <p><b>FRISC Code Pattern (While Loop):</b>
 * <pre>
 * L_LOOP1:                   ; loop start label
 *     &lt;condition evaluation&gt; ; result in R0
 *     CMP R0, %D 0           ; compare with 0
 *     JP_EQ L_BREAK1         ; exit if false
 *     &lt;loop body&gt;           ; may contain break/continue
 * L_CONTINUE1:               ; continue label (for continue statements)
 *     JP L_LOOP1             ; repeat loop
 * L_BREAK1:                  ; break label (for break statements and loop exit)
 * </pre>
 * 
 * <p><b>FRISC Code Pattern (For Loop):</b>
 * <pre>
 * &lt;initialization&gt;           ; executed once
 * L_LOOP1:                   ; loop start label
 *     &lt;condition evaluation&gt; ; result in R0
 *     CMP R0, %D 0           ; compare with 0
 *     JP_EQ L_BREAK1         ; exit if false
 *     &lt;loop body&gt;           ; may contain break/continue
 * L_CONTINUE1:               ; continue label (for continue statements and increment)
 *     &lt;increment&gt;            ; executed each iteration (if present)
 *     JP L_LOOP1             ; repeat loop
 * L_BREAK1:                  ; break label (for break statements and loop exit)
 * </pre>
 * 
 * <p><b>Nested Loops:</b>
 * 
 * <p>Nested loops are handled correctly because:
 * <ul>
 *   <li>Each loop creates its own set of labels (loop, continue, break)</li>
 *   <li>Each loop creates a new context with its labels</li>
 *   <li>Break/continue statements in inner loops use the inner loop's labels</li>
 *   <li>Break/continue statements in outer loops use the outer loop's labels</li>
 * </ul>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) for code generation (constant number of instructions),
 *       but actual runtime depends on loop body and iteration count</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only a few labels and registers</li>
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

