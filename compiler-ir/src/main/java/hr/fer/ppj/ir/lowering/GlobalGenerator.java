package hr.fer.ppj.ir.lowering;

import hr.fer.ppj.ir.build.StructLayoutRegistry;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.lowering.global.GlobalInitializerExtractor;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrGlobalVar;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.List;
import java.util.Objects;

/**
 * Generates IR for global variable declarations.
 *
 * <p>This generator handles:
 * <ul>
 *   <li>Global variable declarations</li>
 *   <li>Global initializers (constant evaluation)</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class GlobalGenerator {

  private final SymbolTable globalScope;
  private final IrProgram.Builder programBuilder;
  private final GlobalInitializerExtractor initializerExtractor;
  private final StructLayoutRegistry structLayoutRegistry;

  public GlobalGenerator(
      SymbolTable globalScope,
      IrProgram.Builder programBuilder,
      StructLayoutRegistry structLayoutRegistry) {
    this.globalScope = Objects.requireNonNull(globalScope, "globalScope must not be null");
    this.programBuilder = Objects.requireNonNull(programBuilder, "programBuilder must not be null");
    this.structLayoutRegistry = structLayoutRegistry; // Can be null
    this.initializerExtractor = new GlobalInitializerExtractor();
  }

  /**
   * @deprecated Use the constructor with StructLayoutRegistry
   */
  @Deprecated
  public GlobalGenerator(SymbolTable globalScope, IrProgram.Builder programBuilder) {
    this(globalScope, programBuilder, null);
  }

  /**
   * Generates a global declaration.
   */
  public void generateDeclaration(NonTerminalNode node) {
    Objects.requireNonNull(node, "node must not be null");
    List<ParseNode> children = node.children();
    if (children.isEmpty()) {
      return;
    }

    // Get type - can be from <ime_tipa> or <specifikatori_deklaracije>
    Type baseType = null;
    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nt) {
        String symbol = nt.symbol();
        if (symbol.equals("<ime_tipa>")) {
          baseType = nt.attributes().type();
          break;
        } else if (symbol.equals("<specifikatori_deklaracije>")) {
          baseType = nt.attributes().type();
          break;
        }
      }
    }

    // Try to get type from node attributes as fallback
    if (baseType == null) {
      baseType = node.attributes().type();
    }

    if (baseType == null) {
      return;
    }

    // Process <lista_init_deklaratora>
    NonTerminalNode initDeclList = null;
    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nt && nt.symbol().equals("<lista_init_deklaratora>")) {
        initDeclList = nt;
        break;
      }
    }

    if (initDeclList != null) {
      generateInitDeclaratorList(initDeclList, baseType);
    } else {
      // Try to find <init_deklarator> directly
      for (ParseNode child : children) {
        if (child instanceof NonTerminalNode nt && nt.symbol().equals("<init_deklarator>")) {
          generateInitDeclarator(nt, baseType);
          break;
        }
      }
    }
  }

  private void generateInitDeclaratorList(NonTerminalNode node, Type baseType) {
    List<ParseNode> children = node.children();

    if (children.size() == 1) {
      ParseNode firstChild = children.get(0);
      if (firstChild instanceof NonTerminalNode nt && nt.symbol().equals("<init_deklarator>")) {
        generateInitDeclarator(nt, baseType);
      }
    } else if (children.size() == 3) {
      ParseNode firstChild = children.get(0);
      ParseNode thirdChild = children.get(2);
      if (firstChild instanceof NonTerminalNode listNt && listNt.symbol().equals("<lista_init_deklaratora>")) {
        generateInitDeclaratorList(listNt, baseType);
      }
      if (thirdChild instanceof NonTerminalNode initDeclNt && initDeclNt.symbol().equals("<init_deklarator>")) {
        generateInitDeclarator(initDeclNt, baseType);
      }
    }
  }

  private void generateInitDeclarator(NonTerminalNode node, Type baseType) {
    List<ParseNode> children = node.children();
    if (children.isEmpty()) {
      return;
    }

    // Get declarator - can be <izravni_deklarator> or <deklarator>
    ParseNode firstChild = children.get(0);
    NonTerminalNode declarator = null;
    if (firstChild instanceof NonTerminalNode nt) {
      String symbol = nt.symbol();
      if (symbol.equals("<izravni_deklarator>") || symbol.equals("<deklarator>")) {
        declarator = nt;
      }
    }

    if (declarator == null) {
      return;
    }

    // If we found <deklarator>, find <izravni_deklarator> inside it
    final NonTerminalNode originalDeclarator = declarator;
    NonTerminalNode finalDeclarator = declarator;
    if (declarator.symbol().equals("<deklarator>")) {
      NonTerminalNode innerDeclarator = null;
      for (ParseNode child : declarator.children()) {
        if (child instanceof NonTerminalNode nt && nt.symbol().equals("<izravni_deklarator>")) {
          innerDeclarator = nt;
          break;
        }
      }
      if (innerDeclarator != null) {
        finalDeclarator = innerDeclarator;
      }
    }

    String varName = finalDeclarator.attributes().identifier();
    Type varType = finalDeclarator.attributes().type();
    if (varType == null) {
      varType = baseType;
    }

    // If varName is still null, try the original declarator
    if (varName == null && originalDeclarator.symbol().equals("<deklarator>")) {
      varName = originalDeclarator.attributes().identifier();
      if (varType == null) {
        varType = originalDeclarator.attributes().type();
        if (varType == null) {
          varType = baseType;
        }
      }
    }

    if (varName == null || varType == null) {
      return;
    }

    // Ensure struct types are registered and their definitions emitted
    if (structLayoutRegistry != null) {
      ensureStructTypeReady(varType, structLayoutRegistry);
    }

    // Global variable - add to program globals
    IrType irType = TypeMapper.toIrType(varType);
    IrConst initializer = null;

    // Handle global initializer if present
    if (children.size() == 3) {
      NonTerminalNode initializerNode =
          hr.fer.ppj.semantics.util.NodeUtils.asNonTerminal(children.get(2), "<inicijalizator>");
      initializer = initializerExtractor.extractInitializer(initializerNode, varType);
    }

    programBuilder.addGlobal(new IrGlobalVar(varName, irType, initializer));
  }

  /**
   * Ensures that any struct type within a type (including array element types) is registered.
   */
  private void ensureStructTypeReady(Type type, StructLayoutRegistry registry) {
    Type stripped = TypeSystem.stripConst(type);
    if (stripped instanceof StructType structType) {
      registry.ensureStructReady(structType);
    } else if (stripped instanceof ArrayType arrayType) {
      ensureStructTypeReady(arrayType.elementType(), registry);
    }
  }
}
