package hr.fer.ppj.codegen.stmt;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.func.FunctionPrologueEpilogueGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for return statements.
 * 
 * <p>This class handles both return statements with values and void returns,
 * optimizing simple literal returns by generating them directly into R6.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ReturnStatementGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator exprGen;
    private final FunctionPrologueEpilogueGenerator prologueEpilogueGenerator;
    
    /**
     * Creates a new return statement generator.
     * 
     * @param context the code generation context
     * @param exprGen the expression generator for return expressions
     */
    public ReturnStatementGenerator(CodeGenContext context, ExpressionCodeGenerator exprGen) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.exprGen = Objects.requireNonNull(exprGen, "exprGen must not be null");
        this.prologueEpilogueGenerator = new FunctionPrologueEpilogueGenerator();
    }
    
    /**
     * Generates code for a return statement.
     * 
     * @param node the return statement node ({@code <naredba_skoka>} with KR_RETURN)
     */
    public void generateReturnStatement(NonTerminalNode node) {
        Objects.requireNonNull(node, "node must not be null");
        
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
                // If parsing fails (e.g., float literal), fall through to normal generation
                // This prevents incorrect optimization of float expressions
            }
        }
        
        // For other cases (including float expressions), generate into R0 and move to R6
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
            if ("<unarni_izraz>".equals(node.symbol()) && children.size() == 2) {
                ParseNode first = children.get(0);
                if (first instanceof hr.fer.ppj.semantics.tree.NonTerminalNode unaryOp && 
                    "<unarni_operator>".equals(unaryOp.symbol())) {
                    for (ParseNode opChild : unaryOp.children()) {
                        if (opChild instanceof hr.fer.ppj.semantics.tree.TerminalNode terminal && "MINUS".equals(terminal.symbol())) {
                            isNegative = true;
                            if (children.get(1) instanceof NonTerminalNode operand) {
                                node = operand;
                                continue outer;
                            }
                            break outer;
                        }
                    }
                }
            }
            
            // Check for <primarni_izraz> with BROJ terminal
            if ("<primarni_izraz>".equals(node.symbol())) {
                for (ParseNode child : children) {
                    if (child instanceof hr.fer.ppj.semantics.tree.TerminalNode terminal && "BROJ".equals(terminal.symbol())) {
                        String value = terminal.lexeme();
                        // CRITICAL FIX: Only optimize if this is truly an INTEGER literal, not a float
                        // This prevents incorrect optimization of expressions like -(3.0 * 4.0)
                        // where we might extract "3.0" and treat it as "-3.0", ignoring the multiplication
                        if (hr.fer.ppj.codegen.util.FloatCodegenHelper.isFloatLiteral(value)) {
                            // This is a float literal - do not optimize it here
                            // Let the normal expression generation handle it
                            return null;
                        }
                        return isNegative ? "-" + value : value;
                    }
                }
                break;
            }
            
            // Look for single non-terminal child to continue drilling
            NonTerminalNode singleChild = null;
            for (ParseNode child : children) {
                if (child instanceof NonTerminalNode nt) {
                    if (singleChild != null) {
                        break outer;
                    }
                    singleChild = nt;
                } else if (child instanceof hr.fer.ppj.semantics.tree.TerminalNode) {
                    break outer;
                }
            }
            
            if (singleChild == null) {
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
                continue;
            }
            
            break;
        }
        
        return null;
    }
    
    /**
     * Generates the function epilogue (deallocate locals, restore frame pointer, return).
     * 
     * <p>This is a fallback method used when a return statement is encountered
     * but no function exit label is available.
     */
    private void generateFunctionEpilogue() {
        if (context.activationRecord() == null) {
            context.emitter().emitInstruction("RET", null, null, "return from function");
            return;
        }
        
        prologueEpilogueGenerator.generateEpilogue(context, context.activationRecord());
    }
}

