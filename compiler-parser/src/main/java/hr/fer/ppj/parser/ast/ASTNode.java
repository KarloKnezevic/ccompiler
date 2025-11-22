package hr.fer.ppj.parser.ast;

/**
 * Base interface for all AST nodes.
 * 
 * <p>All AST nodes have source location information.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public sealed interface ASTNode
    permits Expression, Statement, Declaration, Type, Program {
  
  int line();
  
  int column();
}

