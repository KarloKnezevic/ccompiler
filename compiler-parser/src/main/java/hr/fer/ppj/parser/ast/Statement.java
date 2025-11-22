package hr.fer.ppj.parser.ast;

/**
 * Base sealed interface for all statements.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public sealed interface Statement extends ASTNode
    permits ExpressionStatement, IfStatement, WhileStatement, ForStatement, 
            ReturnStatement, BreakStatement, ContinueStatement, BlockStatement {}

