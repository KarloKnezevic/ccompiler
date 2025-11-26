package hr.fer.ppj.codegen.stmt;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for statements.
 * 
 * <p>This class handles the generation of code for all types of statements
 * in ppjC, including:
 * <ul>
 *   <li>Expression statements</li>
 *   <li>Compound statements (blocks)</li>
 *   <li>Conditional statements (if-else)</li>
 *   <li>Loop statements (while, for)</li>
 *   <li>Jump statements (break, continue, return)</li>
 * </ul>
 * 
 * <p>The generator maintains proper control flow by generating appropriate
 * labels and jump instructions, and handles nested scopes for compound
 * statements.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class StatementCodeGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator exprGen;
    
    /**
     * Creates a new statement code generator.
     * 
     * @param context the code generation context
     */
    public StatementCodeGenerator(CodeGenContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.exprGen = new ExpressionCodeGenerator(context);
    }
    
    /**
     * Generates code for a statement.
     * 
     * @param statement the statement node to generate code for
     */
    public void generateStatement(NonTerminalNode statement) {
        Objects.requireNonNull(statement, "statement must not be null");
        
        String symbol = statement.symbol();
        
        switch (symbol) {
            case "<naredba>" -> {
                // Delegate to the specific statement type
                NonTerminalNode child = (NonTerminalNode) statement.children().get(0);
                generateStatement(child);
            }
            case "<slozena_naredba>" -> generateCompoundStatement(statement);
            case "<izraz_naredba>" -> generateExpressionStatement(statement);
            case "<naredba_grananja>" -> generateBranchingStatement(statement);
            case "<naredba_petlje>" -> generateLoopStatement(statement);
            case "<naredba_skoka>" -> generateJumpStatement(statement);
            default -> throw new IllegalArgumentException("Unknown statement type: " + symbol);
        }
    }
    
    /**
     * Generates code for compound statements (blocks).
     */
    private void generateCompoundStatement(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        context.emitter().emitComment("Compound statement");
        
        // Process all statements in the block
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal) {
                String symbol = nonTerminal.symbol();
                
                if ("<lista_naredbi>".equals(symbol)) {
                    generateStatementList(nonTerminal);
                } else if ("<lista_deklaracija>".equals(symbol)) {
                    // TODO: Handle local variable declarations
                    context.emitter().emitComment("Local declarations (TODO)");
                }
            }
        }
    }
    
    /**
     * Generates code for a list of statements.
     */
    private void generateStatementList(NonTerminalNode node) {
        String symbol = node.symbol();
        
        if ("<lista_naredbi>".equals(symbol)) {
            List<ParseNode> children = node.children();
            
            for (ParseNode child : children) {
                if (child instanceof NonTerminalNode nonTerminal) {
                    String childSymbol = nonTerminal.symbol();
                    if ("<lista_naredbi>".equals(childSymbol)) {
                        // Recursive case for nested statement lists
                        generateStatementList(nonTerminal);
                    } else if ("<naredba>".equals(childSymbol)) {
                        generateStatement(nonTerminal);
                    }
                }
            }
        }
    }
    
    /**
     * Generates code for expression statements.
     */
    private void generateExpressionStatement(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 2) {
            // <izraz> TOCKAZAREZ
            NonTerminalNode expression = (NonTerminalNode) children.get(0);
            exprGen.generateExpression(expression);
            // Result is discarded (expression statement)
        }
        // If only TOCKAZAREZ, it's an empty statement - no code needed
    }
    
    /**
     * Generates code for branching statements (if-else).
     */
    private void generateBranchingStatement(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 5) {
            // KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba>
            NonTerminalNode condition = (NonTerminalNode) children.get(2);
            NonTerminalNode thenStmt = (NonTerminalNode) children.get(4);
            
            var labels = context.labelGenerator().generateIfLabels();
            
            context.emitter().emitComment("If statement");
            
            // Generate condition
            exprGen.generateExpression(condition);
            
            // Jump to end if condition is false
            context.emitter().emitInstruction("CMP", "R0", "0", null);
            context.emitter().emitInstruction("JP_EQ", labels.endLabel(), null, "if condition is false");
            
            // Generate then statement
            generateStatement(thenStmt);
            
            context.emitter().emitLabel(labels.endLabel(), "end if");
            
        } else if (children.size() == 7) {
            // KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba> KR_ELSE <naredba>
            NonTerminalNode condition = (NonTerminalNode) children.get(2);
            NonTerminalNode thenStmt = (NonTerminalNode) children.get(4);
            NonTerminalNode elseStmt = (NonTerminalNode) children.get(6);
            
            var labels = context.labelGenerator().generateIfLabels();
            
            context.emitter().emitComment("If-else statement");
            
            // Generate condition
            exprGen.generateExpression(condition);
            
            // Jump to else if condition is false
            context.emitter().emitInstruction("CMP", "R0", "0", null);
            context.emitter().emitInstruction("JP_EQ", labels.elseLabel(), null, "if condition is false");
            
            // Generate then statement
            generateStatement(thenStmt);
            context.emitter().emitInstruction("JP", labels.endLabel(), null, "skip else");
            
            // Generate else statement
            context.emitter().emitLabel(labels.elseLabel(), "else clause");
            generateStatement(elseStmt);
            
            context.emitter().emitLabel(labels.endLabel(), "end if-else");
        }
    }
    
    /**
     * Generates code for loop statements (while, for).
     */
    private void generateLoopStatement(NonTerminalNode node) {
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
     * Generates code for while loops.
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
        context.emitter().emitInstruction("CMP", "R0", "0", null);
        context.emitter().emitInstruction("JP_EQ", labels.breakLabel(), null, "exit while loop");
        
        // Generate loop body with break/continue context
        StatementCodeGenerator bodyGen = new StatementCodeGenerator(context);
        bodyGen.generateStatement(body);
        
        // Continue point (for continue statements)
        context.emitter().emitLabel(labels.continueLabel(), "while continue");
        
        // Jump back to condition
        context.emitter().emitInstruction("JP", labels.loopLabel(), null, "repeat while loop");
        
        // Loop end (for break statements)
        context.emitter().emitLabel(labels.breakLabel(), "while loop end");
    }
    
    /**
     * Generates code for for loops.
     */
    private void generateForLoop(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 6) {
            // KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> D_ZAGRADA <naredba>
            NonTerminalNode init = (NonTerminalNode) children.get(2);
            NonTerminalNode condition = (NonTerminalNode) children.get(3);
            NonTerminalNode body = (NonTerminalNode) children.get(5);
            
            var labels = context.labelGenerator().generateLoopLabels();
            
            // Generate initialization
            generateStatement(init);
            
            // Loop start
            context.emitter().emitLabel(labels.loopLabel(), "for loop");
            
            // Generate condition (if present)
            if (condition.children().size() > 1) {
                NonTerminalNode condExpr = (NonTerminalNode) condition.children().get(0);
                exprGen.generateExpression(condExpr);
                
                // Exit if condition is false
                context.emitter().emitInstruction("CMP", "R0", "0", null);
                context.emitter().emitInstruction("JP_EQ", labels.breakLabel(), null, "exit for loop");
            }
            
            // Generate loop body
            generateStatement(body);
            
            // Jump back to condition
            context.emitter().emitInstruction("JP", labels.loopLabel(), null, "repeat for loop");
            
            // Loop end
            context.emitter().emitLabel(labels.breakLabel());
            
        } else if (children.size() == 7) {
            // KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> <izraz> D_ZAGRADA <naredba>
            // TODO: Handle for loop with increment expression
            context.emitter().emitComment("For loop with increment (TODO)");
        }
    }
    
    /**
     * Generates code for jump statements (break, continue, return).
     */
    private void generateJumpStatement(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        TerminalNode keyword = (TerminalNode) children.get(0);
        
        String jumpType = keyword.symbol();
        
        switch (jumpType) {
            case "KR_RETURN" -> {
                if (children.size() == 3) {
                    // KR_RETURN <izraz> TOCKAZAREZ
                    NonTerminalNode expression = (NonTerminalNode) children.get(1);
                    exprGen.generateExpression(expression);
                    context.emitter().emitInstruction("MOVE", "R0", "R6", "return value");
                } else {
                    // KR_RETURN TOCKAZAREZ (void return)
                    context.emitter().emitInstruction("MOVE", "0", "R6", "void return");
                }
                context.emitter().emitInstruction("RET", null, null, "return from function");
            }
            case "KR_BREAK" -> {
                // TODO: Jump to break label of current loop
                context.emitter().emitComment("Break statement (TODO)");
            }
            case "KR_CONTINUE" -> {
                // TODO: Jump to continue label of current loop
                context.emitter().emitComment("Continue statement (TODO)");
            }
        }
    }
}
