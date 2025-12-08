package hr.fer.ppj.semantics.analysis.rules;
import hr.fer.ppj.semantics.analysis.SemanticConstants;
import hr.fer.ppj.semantics.analysis.SemanticChecker;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ConstType;
import hr.fer.ppj.semantics.types.PointerType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.util.NodeUtils;

/**
 * Semantic rules for type specifications and qualifiers.
 * 
 * <p>Handles:
 * <ul>
 *   <li>Type specifiers (void, char, int, float, struct)</li>
 *   <li>Const qualifiers</li>
 *   <li>Pointer types</li>
 *   <li>Type names</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class TypeSpecificationRules {
  
  private final SemanticChecker checker;
  
  TypeSpecificationRules(SemanticChecker checker) {
    this.checker = checker;
    checker.registerRule("<ime_tipa>", this::visitImeTipa);
    checker.registerRule("<lista_specifikatora_kvalifikatora>", this::visitListaSpecifikatoraKvalifikatora);
    checker.registerRule("<specifikator_tipa>", this::visitSpecifikatorTipa);
    checker.registerRule("<pokazivac>", this::visitPokazivac);
    checker.registerRule("<specifikatori_deklaracije>", this::visitSpecifikatoriDeklaracije);
  }
  
  private void visitImeTipa(NonTerminalNode node) {
    var children = node.children();
    if (children.size() == 1) {
      NonTerminalNode specList = NodeUtils.asNonTerminal(children.get(0));
      checker.visitNonTerminal(specList);
      node.attributes().type(specList.attributes().type());
      return;
    }
    if (children.size() == 2) {
      NonTerminalNode specList = NodeUtils.asNonTerminal(children.get(0));
      NonTerminalNode pointer = NodeUtils.asNonTerminal(children.get(1));
      checker.visitNonTerminal(specList);
      Type baseType = specList.attributes().type();
      if (baseType == null) {
        checker.fail(node);
        return;
      }
      pointer.attributes().inheritedType(baseType);
      checker.visitNonTerminal(pointer);
      Type pointerType = pointer.attributes().type();
      if (pointerType == null) {
        checker.fail(node);
        return;
      }
      node.attributes().type(pointerType);
      return;
    }
    checker.fail(node);
  }
  
  /**
   * Performs semantic analysis for type specifier and qualifier lists.
   * 
   * <p>Grammar:
   *   <lista_specifikatora_kvalifikatora> ::= <specifikator_tipa>
   *                                        | <lista_specifikatora_kvalifikatora> <specifikator_tipa>
   *                                        | <lista_specifikatora_kvalifikatora> KR_CONST
   * 
   * <p>This method:
   * <ol>
   *   <li>Collects all type specifiers (must be consistent)</li>
   *   <li>Detects const qualifier</li>
   *   <li>Applies const qualification if present</li>
   * </ol>
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>All type specifiers must be the same type</li>
   *   <li>Const cannot be applied to void</li>
   *   <li>At least one type specifier must be present</li>
   * </ul>
   * 
   * @param node the type specifier list node
   */
  private void visitListaSpecifikatoraKvalifikatora(NonTerminalNode node) {
    var children = node.children();
    Type baseType = null;
    boolean isConst = false;
    
    // Process all children: type specifiers and const qualifiers
    for (ParseNode child : children) {
      if (child instanceof TerminalNode token && SemanticConstants.KR_CONST.equals(token.symbol())) {
        // Const qualifier detected
        isConst = true;
      } else if (child instanceof NonTerminalNode spec) {
        // Type specifier: void, char, int, float, or struct
        checker.visitNonTerminal(spec);
        Type specType = spec.attributes().type();
        if (specType == null) {
          checker.fail(node);
          return;
        }
        // All type specifiers must be the same (e.g., "int int" is invalid)
        if (baseType != null) {
          if (!baseType.equals(specType)) {
            checker.fail(node);
            return;
          }
        } else {
          baseType = specType;
        }
      }
    }
    
    // At least one type specifier must be present
    if (baseType == null) {
      checker.fail(node);
      return;
    }
    
    // Apply const qualification if present
    if (isConst) {
      // Const cannot be applied to void
      if (baseType.isVoid()) {
        checker.fail(node);
        return;
      }
      baseType = new ConstType(baseType);
    }
    
    node.attributes().type(baseType);
  }
  
  /**
   * Performs semantic analysis for pointer declarators.
   * 
   * <p>Grammar:
   *   <pokazivac> ::= ASTERISK
   *                | ASTERISK KR_CONST
   *                | ASTERISK <pokazivac>
   *                | ASTERISK KR_CONST <pokazivac>
   * 
   * <p>This method handles nested pointers (e.g., int **, int * const *).
   * The base type is inherited from the parent and passed down recursively.
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Base type must be provided (inherited attribute)</li>
   *   <li>Const qualifier applies to the pointer itself (T * const), not the pointed-to type</li>
   * </ul>
   * 
   * @param node the pointer declarator node
   */
  private void visitPokazivac(NonTerminalNode node) {
    var children = node.children();
    Type baseType = node.attributes().inheritedType();
    if (baseType == null) {
      checker.fail(node);
      return;
    }
    
    // Handle nested pointers: ASTERISK <pokazivac> or ASTERISK KR_CONST <pokazivac>
    if (children.size() > 1 && children.get(children.size() - 1) instanceof NonTerminalNode nested) {
      // Recursively process nested pointer with inherited base type
      nested.attributes().inheritedType(baseType);
      checker.visitNonTerminal(nested);
      Type nestedType = nested.attributes().type();
      if (nestedType == null) {
        checker.fail(node);
        return;
      }
      // Check if this pointer level is const-qualified (T * const)
      boolean isConst = children.size() == 3 && 
          children.get(1) instanceof TerminalNode constToken &&
          SemanticConstants.KR_CONST.equals(constToken.symbol());
      node.attributes().type(new PointerType(nestedType, isConst));
      return;
    }
    
    // Handle simple pointer: ASTERISK
    if (children.size() == 1 && children.get(0) instanceof TerminalNode asterisk) {
      if (!SemanticConstants.ASTERISK.equals(asterisk.symbol())) {
        checker.fail(node);
        return;
      }
      // Simple pointer to base type (not const)
      node.attributes().type(new PointerType(baseType, false));
      return;
    }
    
    // Handle const pointer: ASTERISK KR_CONST
    if (children.size() == 2) {
      TerminalNode first = (TerminalNode) children.get(0);
      TerminalNode second = (TerminalNode) children.get(1);
      if (SemanticConstants.ASTERISK.equals(first.symbol()) 
          && SemanticConstants.KR_CONST.equals(second.symbol())) {
        // Const pointer to base type (pointer itself is const)
        node.attributes().type(new PointerType(baseType, true));
        return;
      }
    }
    
    checker.fail(node);
  }
  
  private void visitSpecifikatorTipa(NonTerminalNode node) {
    ParseNode first = node.children().get(0);
    if (first instanceof TerminalNode token) {
      switch (token.symbol()) {
        case SemanticConstants.KR_VOID -> node.attributes().type(PrimitiveType.VOID);
        case SemanticConstants.KR_CHAR -> node.attributes().type(PrimitiveType.CHAR);
        case SemanticConstants.KR_INT -> node.attributes().type(PrimitiveType.INT);
        case SemanticConstants.KR_FLOAT -> node.attributes().type(PrimitiveType.FLOAT);
        default -> checker.fail(node);
      }
    } else if (first instanceof NonTerminalNode structSpec) {
      checker.visitNonTerminal(structSpec);
      node.attributes().type(structSpec.attributes().type());
    } else {
      checker.fail(node);
    }
  }
  
  private void visitSpecifikatoriDeklaracije(NonTerminalNode node) {
    var children = node.children();
    if (children.size() == 1 && children.get(0) instanceof NonTerminalNode spec) {
      checker.visitNonTerminal(spec);
      node.attributes().type(spec.attributes().type());
      return;
    }
    if (children.size() == 2) {
      if (children.get(0) instanceof TerminalNode constToken
          && SemanticConstants.KR_CONST.equals(constToken.symbol())
          && children.get(1) instanceof NonTerminalNode spec) {
        checker.visitNonTerminal(spec);
        Type base = spec.attributes().type();
        if (base == null || base.isVoid()) {
          checker.fail(node);
        }
        node.attributes().type(new ConstType(base));
        return;
      }
    }
    checker.fail(node);
  }
}

