package hr.fer.ppj.semantics.analysis.rules;
import hr.fer.ppj.semantics.analysis.SemanticConstants;
import hr.fer.ppj.semantics.analysis.SemanticChecker;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.PointerType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Semantic rules for struct type declarations and definitions.
 * 
 * <p>Handles:
 * <ul>
 *   <li>Struct specifiers (tagged and anonymous)</li>
 *   <li>Struct field declarations</li>
 *   <li>Struct declarators</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class StructRules {
  
  private final SemanticChecker checker;
  
  StructRules(SemanticChecker checker) {
    this.checker = checker;
    checker.registerRule("<struct_specifikator>", this::visitStructSpecifikator);
    checker.registerRule("<struct_lista_deklaracija>", this::visitStructListaDeklaracija);
    checker.registerRule("<struct_deklaracija>", this::visitStructDeklaracija);
    checker.registerRule("<struct_lista_deklaratora>", this::visitStructListaDeklaratora);
    checker.registerRule("<struct_deklarator>", this::visitStructDeklarator);
  }
  
  /**
   * Performs semantic analysis for struct specifiers.
   * 
   * <p>Grammar:
   *   <struct_specifikator> ::= KR_STRUCT IDN L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
   *                           | KR_STRUCT L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
   *                           | KR_STRUCT IDN
   * 
   * <p>This method handles three cases:
   * <ol>
   *   <li>Tagged struct definition: struct Tag { ... } - registers forward declaration first,
   *       then full definition (enables self-referential structs)</li>
   *   <li>Anonymous struct definition: struct { ... } - creates anonymous struct type</li>
   *   <li>Tagged struct reference: struct Tag - looks up existing struct by tag</li>
   * </ol>
   * 
   * <p>Forward declaration mechanism:
   * <ul>
   *   <li>For tagged structs, a forward declaration (empty fields) is registered first</li>
   *   <li>This allows fields to reference the struct type (e.g., struct Node *next)</li>
   *   <li>After processing fields, the forward declaration is replaced with the full definition</li>
   * </ul>
   * 
   * @param node the struct specifier node
   */
  private void visitStructSpecifikator(NonTerminalNode node) {
    var children = node.children();
    
    // Case 1: Tagged struct reference: KR_STRUCT IDN
    if (children.size() == 2) {
      TerminalNode structKeyword = (TerminalNode) children.get(0);
      TerminalNode tagToken = (TerminalNode) children.get(1);
      if (!SemanticConstants.KR_STRUCT.equals(structKeyword.symbol())) {
        checker.fail(node);
        return;
      }
      String tag = tagToken.lexeme();
      // Look up existing struct by tag (must be previously defined)
      StructType structType = checker.lookupStructTag(tag);
      if (structType == null) {
        checker.fail(node);
        return;
      }
      node.attributes().type(structType);
      return;
    }
    
    // Case 2: Anonymous struct definition: KR_STRUCT L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
    if (children.size() == 4) {
      TerminalNode structKeyword = (TerminalNode) children.get(0);
      if (!SemanticConstants.KR_STRUCT.equals(structKeyword.symbol())) {
        checker.fail(node);
        return;
      }
      // Process field list
      NonTerminalNode fieldList = NodeUtils.asNonTerminal(children.get(2));
      checker.visitNonTerminal(fieldList);
      Map<String, Type> fields = fieldList.attributes().structFields();
      if (fields == null) {
        checker.fail(node);
        return;
      }
      // Create anonymous struct (no tag)
      StructType structType = new StructType(null, fields);
      node.attributes().type(structType);
      return;
    }
    
    // Case 3: Tagged struct definition: KR_STRUCT IDN L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
    if (children.size() == 5) {
      TerminalNode structKeyword = (TerminalNode) children.get(0);
      TerminalNode tagToken = (TerminalNode) children.get(1);
      if (!SemanticConstants.KR_STRUCT.equals(structKeyword.symbol())) {
        checker.fail(node);
        return;
      }
      String tag = tagToken.lexeme();
      
      // Register forward declaration with empty fields
      // This allows self-referential structs (e.g., struct Node { struct Node *next; })
      StructType forwardDecl = new StructType(tag, new LinkedHashMap<>());
      checker.registerStructTagForward(tag, forwardDecl, node);
      
      // Process field list
      NonTerminalNode fieldList = NodeUtils.asNonTerminal(children.get(3));
      checker.visitNonTerminal(fieldList);
      Map<String, Type> fields = fieldList.attributes().structFields();
      if (fields == null) {
        checker.fail(node);
        return;
      }
      // Register full definition (replaces forward declaration)
      StructType structType = new StructType(tag, fields);
      checker.registerStructTag(tag, structType, node);
      node.attributes().type(structType);
      return;
    }
    
    checker.fail(node);
  }
  
  /**
   * Performs semantic analysis for struct field declaration lists.
   * 
   * <p>Grammar:
   *   <struct_lista_deklaracija> ::= <struct_deklaracija>
   *                               | <struct_lista_deklaracija> <struct_deklaracija>
   * 
   * <p>This method:
   * <ol>
   *   <li>Processes all field declarations</li>
   *   <li>Collects field names and types</li>
   *   <li>Validates that field names are unique</li>
   * </ol>
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Field names must be unique within a struct</li>
   *   <li>Fields are stored in declaration order</li>
   * </ul>
   * 
   * @param node the struct field declaration list node
   */
  private void visitStructListaDeklaracija(NonTerminalNode node) {
    Map<String, Type> fields = new LinkedHashMap<>();
    // Process each field declaration
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode decl) {
        checker.visitNonTerminal(decl);
        Map<String, Type> declFields = decl.attributes().structFields();
        if (declFields != null) {
          // Add fields from this declaration, checking for duplicates
          for (Map.Entry<String, Type> entry : declFields.entrySet()) {
            // Field names must be unique within a struct
            if (fields.containsKey(entry.getKey())) {
              checker.fail(node);
              return;
            }
            fields.put(entry.getKey(), entry.getValue());
          }
        }
      }
    }
    node.attributes().structFields(fields);
  }
  
  private void visitStructDeklaracija(NonTerminalNode node) {
    var children = node.children();
    if (children.size() < 3) {
      checker.fail(node);
      return;
    }
    NonTerminalNode specList = NodeUtils.asNonTerminal(children.get(0));
    NonTerminalNode declaratorList = NodeUtils.asNonTerminal(children.get(1));
    checker.visitNonTerminal(specList);
    Type fieldType = specList.attributes().type();
    if (fieldType == null || fieldType.isVoid()) {
      checker.fail(node);
      return;
    }
    if (fieldType instanceof FunctionType) {
      checker.fail(node);
      return;
    }
    declaratorList.attributes().inheritedType(fieldType);
    checker.visitNonTerminal(declaratorList);
    Map<String, Type> fields = declaratorList.attributes().structFields();
    node.attributes().structFields(fields);
  }
  
  private void visitStructListaDeklaratora(NonTerminalNode node) {
    var children = node.children();
    Map<String, Type> fields = new LinkedHashMap<>();
    Type baseType = node.attributes().inheritedType();
    if (baseType == null) {
      checker.fail(node);
      return;
    }
    if (children.size() == 1) {
      NonTerminalNode declarator = NodeUtils.asNonTerminal(children.get(0));
      declarator.attributes().inheritedType(baseType);
      checker.visitNonTerminal(declarator);
      String fieldName = declarator.attributes().identifier();
      Type fieldType = declarator.attributes().type();
      if (fieldName == null || fieldType == null) {
        checker.fail(node);
        return;
      }
      fields.put(fieldName, fieldType);
    } else if (children.size() == 3) {
      NonTerminalNode list = NodeUtils.asNonTerminal(children.get(0));
      NonTerminalNode declarator = NodeUtils.asNonTerminal(children.get(2));
      list.attributes().inheritedType(baseType);
      declarator.attributes().inheritedType(baseType);
      checker.visitNonTerminal(list);
      checker.visitNonTerminal(declarator);
      Map<String, Type> listFields = list.attributes().structFields();
      if (listFields != null) {
        fields.putAll(listFields);
      }
      String fieldName = declarator.attributes().identifier();
      Type fieldType = declarator.attributes().type();
      if (fieldName == null || fieldType == null) {
        checker.fail(node);
        return;
      }
      if (fields.containsKey(fieldName)) {
        checker.fail(node);
        return;
      }
      fields.put(fieldName, fieldType);
    } else {
      checker.fail(node);
      return;
    }
    node.attributes().structFields(fields);
  }
  
  private void visitStructDeklarator(NonTerminalNode node) {
    var children = node.children();
    Type baseType = node.attributes().inheritedType();
    if (baseType == null) {
      checker.fail(node);
      return;
    }
    
    if (children.size() == 1 && children.get(0) instanceof NonTerminalNode declarator) {
      declarator.attributes().inheritedType(baseType);
      checker.visitNonTerminal(declarator);
      String identifier = declarator.attributes().identifier();
      Type type = declarator.attributes().type();
      if (identifier == null || type == null) {
        checker.fail(node);
        return;
      }
      node.attributes().identifier(identifier);
      node.attributes().type(type);
      return;
    }
    
    if (children.size() == 1 && children.get(0) instanceof TerminalNode idToken) {
      node.attributes().identifier(idToken.lexeme());
      node.attributes().type(baseType);
      return;
    }
    if (children.size() == 4 && children.get(0) instanceof TerminalNode idToken) {
      String literal = extractArrayLengthLiteral(children.get(2), node);
      int length = checker.parseArrayLength(literal, node);
      node.attributes().identifier(idToken.lexeme());
      node.attributes().type(new ArrayType(baseType, length));
      node.attributes().elementCount(length);
      return;
    }
    if (children.size() == 2 && children.get(0) instanceof NonTerminalNode pointer) {
      pointer.attributes().inheritedType(baseType);
      checker.visitNonTerminal(pointer);
      Type pointerType = pointer.attributes().type();
      if (pointerType == null) {
        checker.fail(node);
        return;
      }
      if (children.get(1) instanceof TerminalNode idToken) {
        node.attributes().identifier(idToken.lexeme());
        node.attributes().type(pointerType);
        return;
      }
    }
    if (children.size() == 5 && children.get(0) instanceof NonTerminalNode pointer) {
      pointer.attributes().inheritedType(baseType);
      checker.visitNonTerminal(pointer);
      Type pointerType = pointer.attributes().type();
      if (pointerType == null) {
        checker.fail(node);
        return;
      }
      if (pointerType instanceof PointerType ptr) {
        String literal = extractArrayLengthLiteral(children.get(3), node);
        int length = checker.parseArrayLength(literal, node);
        if (children.get(1) instanceof TerminalNode idToken) {
          node.attributes().identifier(idToken.lexeme());
          node.attributes().type(new ArrayType(ptr.baseType(), length));
          node.attributes().elementCount(length);
          return;
        }
      }
    }
    checker.fail(node);
  }
  
  private String extractArrayLengthLiteral(ParseNode node, NonTerminalNode ctx) {
    if (node instanceof TerminalNode terminal) {
      if (!"BROJ".equals(terminal.symbol())) {
        checker.fail(ctx);
      }
      return terminal.lexeme();
    }
    if (node instanceof NonTerminalNode nonTerminal) {
      var children = nonTerminal.children();
      if (children.size() == 1) {
        return extractArrayLengthLiteral(children.get(0), ctx);
      }
    }
    checker.fail(ctx);
    return "";
  }
}

