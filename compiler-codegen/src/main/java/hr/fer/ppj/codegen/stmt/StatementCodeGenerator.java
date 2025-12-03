package hr.fer.ppj.codegen.stmt;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
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
    private final BranchingStatementGenerator branchingGen;
    private final LoopStatementGenerator loopGen;
    private final JumpStatementGenerator jumpGen;
    private final LocalDeclarationGenerator localDeclGen;
    
    /**
     * Creates a new statement code generator.
     * 
     * <p>Initializes specialized generators for different statement types:
     * <ul>
     *   <li>Branching statements (if-else)</li>
     *   <li>Loop statements (while, for)</li>
     *   <li>Jump statements (return, break, continue)</li>
     *   <li>Local declarations</li>
     * </ul>
     * 
     * @param context the code generation context
     */
    public StatementCodeGenerator(CodeGenContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.exprGen = new ExpressionCodeGenerator(context);
        this.branchingGen = new BranchingStatementGenerator(context, exprGen, this);
        this.loopGen = new LoopStatementGenerator(context, exprGen, this);
        this.jumpGen = new JumpStatementGenerator(context, exprGen);
        this.localDeclGen = new LocalDeclarationGenerator(context, exprGen);
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
            case "<naredba_grananja>" -> branchingGen.generateBranchingStatement(statement);
            case "<naredba_petlje>" -> loopGen.generateLoopStatement(statement);
            case "<naredba_skoka>" -> jumpGen.generateJumpStatement(statement);
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
                    // Handle local variable declarations
                    localDeclGen.generateLocalDeclarations(nonTerminal);
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
                        // Use this instance to maintain context
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
    
    
}
