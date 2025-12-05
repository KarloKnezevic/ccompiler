package hr.fer.ppj.semantics.analysis.util;

import hr.fer.ppj.semantics.errors.SemanticErrorReporter;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.Objects;

/**
 * Utility class for type checking operations during semantic analysis.
 * 
 * <p>This class provides convenient methods for common type checking operations
 * that are used throughout semantic analysis, such as:
 * <ul>
 *   <li>Ensuring types are int-convertible</li>
 *   <li>Ensuring assignment compatibility</li>
 *   <li>Checking initialization requirements</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TypeChecker {
  
  private final SemanticErrorReporter errorReporter;
  
  /**
   * Creates a new type checker with the specified error reporter.
   * 
   * @param errorReporter the error reporter for type checking failures
   * @throws NullPointerException if errorReporter is null
   */
  public TypeChecker(SemanticErrorReporter errorReporter) {
    this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter must not be null");
  }
  
  /**
   * Ensures that the given type is convertible to {@code int} according to PPJ-C rules.
   * 
   * <p>A type is int-convertible if:
   * <ul>
   *   <li>It is {@code int} (after stripping const qualifiers)</li>
   *   <li>It is {@code char} (after stripping const qualifiers)</li>
   * </ul>
   * 
   * <p>This check is used in contexts such as:
   * <ul>
   *   <li>Array indexing expressions (index must be int-convertible)</li>
   *   <li>Arithmetic and logical operations (operands must be int-convertible)</li>
   *   <li>Control flow conditions (condition expressions must be int-convertible)</li>
   * </ul>
   * 
   * @param type the type to check for int-convertibility
   * @param ctx the parse node context for error reporting
   * @throws SemanticException if the type is not int-convertible
   */
  public void ensureIntConvertible(Type type, NonTerminalNode ctx) {
    if (type == null || !TypeSystem.isIntConvertible(TypeSystem.stripConst(type))) {
      errorReporter.reportError(ctx);
    }
  }
  
  /**
   * Ensures that a value of source type can be assigned to a variable of target type.
   * 
   * <p>This method implements the assignment compatibility rules defined in PPJ-C specification.
   * The assignment is valid if the source type can be implicitly converted to the target type
   * according to the language's type conversion rules.
   * 
   * <p>This check is used in contexts such as:
   * <ul>
   *   <li>Assignment expressions</li>
   *   <li>Function call argument passing</li>
   *   <li>Return statement type checking</li>
   *   <li>Variable initialization</li>
   * </ul>
   * 
   * @param source the type of the value being assigned
   * @param target the type of the variable receiving the assignment
   * @param ctx the parse node context for error reporting
   * @throws SemanticException if the assignment is not type-compatible
   */
  public void ensureAssignable(Type source, Type target, NonTerminalNode ctx) {
    if (!TypeSystem.canAssign(source, target)) {
      errorReporter.reportError(ctx);
    }
  }
  
  /**
   * Checks whether a type requires initialization.
   * 
   * <p>Types that require initialization include:
   * <ul>
   *   <li>Const-qualified types</li>
   *   <li>Array types (if element type requires initialization)</li>
   * </ul>
   * 
   * @param type the type to check
   * @return true if the type requires initialization, false otherwise
   */
  public boolean requiresInitialization(Type type) {
    if (type instanceof hr.fer.ppj.semantics.types.ConstType) {
      return true;
    }
    if (type instanceof hr.fer.ppj.semantics.types.ArrayType arrayType) {
      return requiresInitialization(arrayType.elementType());
    }
    return false;
  }
  
  /**
   * Ensures that a type is not void.
   * 
   * <p>This check is used when declaring variables, as void cannot be used
   * as a variable type.
   * 
   * @param type the type to check
   * @param ctx the parse node context for error reporting
   * @throws SemanticException if the type is void
   */
  public void ensureNotVoid(Type type, NonTerminalNode ctx) {
    if (TypeSystem.stripConst(type) == PrimitiveType.VOID) {
      errorReporter.reportError(ctx);
    }
  }
}

