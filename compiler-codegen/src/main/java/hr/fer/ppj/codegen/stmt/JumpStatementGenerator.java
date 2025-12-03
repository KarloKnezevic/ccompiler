package hr.fer.ppj.codegen.stmt;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.func.FunctionPrologueEpilogueGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for jump statements (break, continue, return).
 * 
 * <p>This class handles the generation of control flow jumps:
 * <ul>
 *   <li>Return statements: {@code return expression;} or {@code return;}</li>
 *   <li>Break statements: {@code break;}</li>
 *   <li>Continue statements: {@code continue;}</li>
 * </ul>
 * 
 * <p><b>Grammar Rules Handled:</b>
 * <pre>
 * &lt;naredba_skoka&gt; ::= KR_RETURN &lt;izraz&gt; TOCKAZAREZ
 *                    | KR_RETURN TOCKAZAREZ
 *                    | KR_BREAK TOCKAZAREZ
 *                    | KR_CONTINUE TOCKAZAREZ
 * </pre>
 * 
 * <p><b>FRISC Code Pattern (Return):</b>
 * <pre>
 * &lt;expression evaluation&gt;     ; result in R0 (or R6 for literals)
 * MOVE R0, R6                 ; move to return register (if needed)
 * JP L_EXIT                   ; jump to function exit label
 * </pre>
 * 
 * <p><b>FRISC Semantics:</b>
 * <ul>
 *   <li>Return value placed in R6 register</li>
 *   <li>Return statements jump to function exit label (avoids duplicate epilogues)</li>
 *   <li>Break statements jump to loop break label</li>
 *   <li>Continue statements jump to loop continue label</li>
 *   <li>Optimization: simple literals generated directly into R6</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class JumpStatementGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator exprGen;
    private final FunctionPrologueEpilogueGenerator prologueEpilogueGenerator;
    
    /**
     * Creates a new jump statement generator.
     * 
     * @param context the code generation context
     * @param exprGen the expression generator for return expressions
     */
    public JumpStatementGenerator(CodeGenContext context, ExpressionCodeGenerator exprGen) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.exprGen = Objects.requireNonNull(exprGen, "exprGen must not be null");
        this.prologueEpilogueGenerator = new FunctionPrologueEpilogueGenerator();
    }
    
    /**
     * Generates code for a jump statement (return, break, or continue).
     * 
     * <p><b>Grammar Rule:</b> Processes {@code <naredba_skoka>}
     * 
     * @param node the jump statement node ({@code <naredba_skoka>})
     */
    public void generateJumpStatement(NonTerminalNode node) {
        Objects.requireNonNull(node, "node must not be null");
        
        List<ParseNode> children = node.children();
        TerminalNode keyword = (TerminalNode) children.get(0);
        
        String jumpType = keyword.symbol();
        
        switch (jumpType) {
            case "KR_RETURN" -> generateReturnStatement(node);
            case "KR_BREAK" -> generateBreakStatement();
            case "KR_CONTINUE" -> generateContinueStatement();
        }
    }
    
    /**
     * Generates code for a return statement.
     * 
     * <p><b>Parse Tree Structure:</b>
     * <ul>
     *   <li>{@code KR_RETURN <izraz> TOCKAZAREZ} - return with value</li>
     *   <li>{@code KR_RETURN TOCKAZAREZ} - void return</li>
     * </ul>
     * 
     * <p><b>FRISC Code:</b>
     * <pre>
     * ; Return with value:
     * &lt;expression evaluation&gt;     ; result in R0 (or R6 for literals)
     * MOVE R0, R6                 ; move to return register (if needed)
     * JP L_EXIT                   ; jump to function exit label
     * 
     * ; Void return:
     * MOVE %D 0, R6              ; return 0
     * JP L_EXIT                   ; jump to function exit label
     * </pre>
     * 
     * @param node the return statement node
     */
    private void generateReturnStatement(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 3) {
            // KR_RETURN <izraz> TOCKAZAREZ
            NonTerminalNode expression = (NonTerminalNode) children.get(1);
            
            // Optimization: try to generate expression directly into R6 when possible
            if (tryGenerateReturnExpressionDirectly(expression)) {
                // Expression was generated directly into R6
            } else {
                // Fallback: generate into R0, then move to R6
                exprGen.generateExpression(expression);
                context.emitter().emitInstruction("MOVE", "R0", "R6", "return value");
            }
        } else {
            // KR_RETURN TOCKAZAREZ (void return)
            context.emitter().emitInstruction("MOVE", "%D 0", "R6", "void return");
        }
        
        // Jump to function exit label to execute epilogue (avoids duplicate epilogues)
        if (context.functionExitLabel() != null) {
            context.emitter().emitInstruction("JP", context.functionExitLabel(), "jump to function exit");
        } else {
            // Fallback: generate epilogue directly if no exit label
            generateFunctionEpilogue();
        }
    }
    
    /**
     * Generates code for a break statement.
     * 
     * <p><b>FRISC Code:</b>
     * <pre>
     * JP L_BREAK                 ; jump to loop break label
     * </pre>
     * 
     * @throws IllegalStateException if break is used outside of a loop
     */
    private void generateBreakStatement() {
        if (context.loopBreakLabel() != null) {
            context.emitter().emitInstruction("JP", context.loopBreakLabel(), "break from loop");
        } else {
            throw new IllegalStateException("Break statement outside of loop");
        }
    }
    
    /**
     * Generates code for a continue statement.
     * 
     * <p><b>FRISC Code:</b>
     * <pre>
     * JP L_CONTINUE              ; jump to loop continue label
     * </pre>
     * 
     * @throws IllegalStateException if continue is used outside of a loop
     */
    private void generateContinueStatement() {
        if (context.loopContinueLabel() != null) {
            context.emitter().emitInstruction("JP", context.loopContinueLabel(), "continue loop");
        } else {
            throw new IllegalStateException("Continue statement outside of loop");
        }
    }
    
    /**
     * Attempts to generate a return expression directly into R6, avoiding
     * unnecessary MOVE R0, R6 instructions.
     * 
     * <p>This optimization handles simple cases like:
     * <ul>
     *   <li>{@code return 31;}</li>
     *   <li>{@code return -84;}</li>
     * </ul>
     * 
     * @param expression the return expression
     * @return true if expression was generated directly into R6, false otherwise
     */
    private boolean tryGenerateReturnExpressionDirectly(NonTerminalNode expression) {
        // Try simple cases first: integer literals (including negative)
        String literal = tryGetSimpleIntegerLiteral(expression);
        if (literal != null) {
            try {
                int value = Integer.parseInt(literal);
                context.emitter().emitLoadIntConstant(value, "R6", "return constant " + literal);
                return true;
            } catch (NumberFormatException e) {
                // If parsing fails, fall through to normal generation
            }
        }
        
        // For other cases, we'd need to modify ExpressionCodeGenerator to support
        // generating directly into R6, which is more complex. For now, we'll
        // generate into R0 and move to R6 (which is acceptable).
        return false;
    }
    
    /**
     * Attempts to detect simple return of an integer literal, e.g.:
     * <ul>
     *   <li>{@code return 31;}</li>
     *   <li>{@code return -84;}</li>
     * </ul>
     * 
     * <p>Navigates through single-child expression wrappers until reaching
     * a {@code <primarni_izraz>} with a BROJ terminal, or a unary minus
     * followed by a literal.
     * 
     * @param expression the expression node to analyze
     * @return the literal value as string (with minus if negative), or null if not a simple literal
     */
    private String tryGetSimpleIntegerLiteral(NonTerminalNode expression) {
        NonTerminalNode node = expression;
        boolean isNegative = false;
        
        // Drill down while there is exactly one non-terminal child
        outer:
        while (true) {
            List<ParseNode> children = node.children();
            
            // Check for unary minus at <unarni_izraz> level
            // Structure: <unarni_izraz> -> <unarni_operator> -> MINUS + <cast_izraz>
            if ("<unarni_izraz>".equals(node.symbol()) && children.size() == 2) {
                ParseNode first = children.get(0);
                // Check if first child is <unarni_operator> containing MINUS
                if (first instanceof NonTerminalNode unaryOp && 
                    "<unarni_operator>".equals(unaryOp.symbol())) {
                    // Check if <unarni_operator> contains MINUS
                    for (ParseNode opChild : unaryOp.children()) {
                        if (opChild instanceof TerminalNode terminal && "MINUS".equals(terminal.symbol())) {
                            isNegative = true;
                            // Continue with the operand (<cast_izraz>)
                            if (children.get(1) instanceof NonTerminalNode operand) {
                                node = operand;
                                continue outer; // Continue outer loop with new node
                            }
                            break outer;
                        }
                    }
                }
            }
            
            // Check for <primarni_izraz> with BROJ terminal
            if ("<primarni_izraz>".equals(node.symbol())) {
                for (ParseNode child : children) {
                    if (child instanceof TerminalNode terminal && "BROJ".equals(terminal.symbol())) {
                        String value = terminal.lexeme();
                        return isNegative ? "-" + value : value;
                    }
                }
                // If we found <primarni_izraz> but no BROJ, stop
                break;
            }
            
            // Look for single non-terminal child to continue drilling
            NonTerminalNode singleChild = null;
            for (ParseNode child : children) {
                if (child instanceof NonTerminalNode nt) {
                    if (singleChild != null) {
                        // Multiple non-terminals - can't be a simple literal
                        break outer;
                    }
                    singleChild = nt;
                } else if (child instanceof TerminalNode) {
                    // Terminal found - check if it's part of a simple structure
                    // If it's not BROJ in <primarni_izraz>, we're done
                    break outer;
                }
            }
            
            if (singleChild == null) {
                // No single child to continue with
                break;
            }
            
            node = singleChild;
            
            // Continue drilling through expression wrappers
            String symbol = node.symbol();
            if ("<cast_izraz>".equals(symbol) || 
                "<unarni_izraz>".equals(symbol) ||
                "<postfiks_izraz>".equals(symbol) ||
                "<primarni_izraz>".equals(symbol) ||
                "<izraz_pridruzivanja>".equals(symbol) ||
                "<log_ili_izraz>".equals(symbol) ||
                "<log_i_izraz>".equals(symbol) ||
                "<bin_ili_izraz>".equals(symbol) ||
                "<bin_xili_izraz>".equals(symbol) ||
                "<bin_i_izraz>".equals(symbol) ||
                "<jednakosni_izraz>".equals(symbol) ||
                "<odnosni_izraz>".equals(symbol) ||
                "<aditivni_izraz>".equals(symbol) ||
                "<multiplikativni_izraz>".equals(symbol)) {
                // Continue drilling down - these are all expression wrappers
                continue;
            }
            
            // If we hit something else, stop
            break;
        }
        
        return null;
    }
    
    /**
     * Generates the function epilogue (deallocate locals, restore frame pointer, return).
     * 
     * <p>This is a fallback method used when a return statement is encountered
     * but no function exit label is available. Normally, return statements jump
     * to the function exit label (which precedes the epilogue generated by
     * {@link hr.fer.ppj.codegen.func.FunctionCodeGenerator}).
     * 
     * <p>Delegates to {@link FunctionPrologueEpilogueGenerator#generateEpilogue}
     * to avoid code duplication.
     * 
     * <p><b>Note:</b> This method handles the edge case where
     * {@code activationRecord()} is null (not in a function context).
     * In normal function contexts, the epilogue is generated by
     * {@link hr.fer.ppj.codegen.func.FunctionCodeGenerator} at the function exit label.
     */
    private void generateFunctionEpilogue() {
        if (context.activationRecord() == null) {
            // Edge case: not in a function context, just return
            // This should rarely happen, but we handle it gracefully
            context.emitter().emitInstruction("RET", null, null, "return from function");
            return;
        }
        
        // Delegate to FunctionPrologueEpilogueGenerator to avoid code duplication
        prologueEpilogueGenerator.generateEpilogue(context, context.activationRecord());
    }
}

