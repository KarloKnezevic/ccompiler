package hr.fer.ppj.ir.lowering;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.lowering.func.FrameLayoutGenerator;
import hr.fer.ppj.ir.lowering.func.ParameterExtractor;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.semantics.symbols.FunctionSymbol;
import hr.fer.ppj.semantics.symbols.Symbol;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.util.NodeUtils;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Generates IR for function definitions.
 *
 * <p>This generator handles:
 * <ul>
 *   <li>Function definitions</li>
 *   <li>Function frame layout</li>
 *   <li>Parameter slots</li>
 *   <li>Function body generation (delegates to statement generators)</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FunctionGenerator {

  private final SymbolTable globalScope;
  private final IrProgram.Builder programBuilder;
  private final StatementGenerator statementGenerator;
  private final ParameterExtractor parameterExtractor;
  private final FrameLayoutGenerator frameLayoutGenerator;

  public FunctionGenerator(
      SymbolTable globalScope,
      IrProgram.Builder programBuilder,
      StatementGenerator statementGenerator) {
    this.globalScope = Objects.requireNonNull(globalScope, "globalScope must not be null");
    this.programBuilder = Objects.requireNonNull(programBuilder, "programBuilder must not be null");
    this.statementGenerator = Objects.requireNonNull(
        statementGenerator, "statementGenerator must not be null");
    this.parameterExtractor = new ParameterExtractor();
    this.frameLayoutGenerator = new FrameLayoutGenerator();
  }

  /**
   * Generates a function definition.
   */
  public void generateFunctionDefinition(NonTerminalNode node) {
    Objects.requireNonNull(node, "node must not be null");
    
    List<ParseNode> children = node.children();
    if (children.size() < 3) {
      return;
    }

    // Find the declarator node
    NonTerminalNode declaratorNode = findDeclaratorNode(children);
    if (declaratorNode == null) {
      return;
    }

    // Get function name from declarator
    String functionName = declaratorNode.attributes().identifier();
    if (functionName == null || functionName.isBlank()) {
      return;
    }

    // Get function from symbol table
    Optional<Symbol> funcSymbolOpt = globalScope.lookup(functionName);
    if (funcSymbolOpt.isEmpty() || !(funcSymbolOpt.get() instanceof FunctionSymbol funcSymbol)) {
      return;
    }

    FunctionType funcType = funcSymbol.type();
    if (funcType == null) {
      return;
    }

    // Extract return type and parameters
    Type returnType = funcType.returnType();
    IrType irReturnType = returnType.isVoid()
        ? null
        : TypeMapper.toIrType(returnType);
    
    List<IrFunction.Parameter> parameters = parameterExtractor.extractParameters(declaratorNode, funcType);

    // Create function builder
    IrFunctionBuilder builder = new IrFunctionBuilder(functionName, irReturnType);
    for (IrFunction.Parameter param : parameters) {
      builder.addParameter(param.name(), param.type());
    }

    // Create function scope
    SymbolTable functionScope = globalScope.enterChildScope();
    for (int i = 0; i < parameters.size(); i++) {
      IrFunction.Parameter param = parameters.get(i);
      Type paramType = i < funcType.parameterTypes().size()
          ? funcType.parameterTypes().get(i)
          : PrimitiveType.INT;
      VariableSymbol paramSymbol = new VariableSymbol(param.name(), paramType, false);
      functionScope.declare(paramSymbol);
    }

    // Create function context
    FunctionContext functionContext = new FunctionContext(builder, functionScope, irReturnType);

    // Wire block start callback to clear block-local caches
    builder.setOnBlockStartCallback(() -> functionContext.onNewBlock(builder.getCurrentBlockLabel()));

    try {
      // Generate parameter slots
      frameLayoutGenerator.generateParameterSlots(builder, parameters);

      // Generate function body
      NonTerminalNode bodyNode = NodeUtils.asNonTerminal(
          children.get(children.size() - 1), "<slozena_naredba>");
      statementGenerator.generateCompoundStatement(bodyNode, functionContext, true);

      // For void functions, add implicit return if function body doesn't end with one
      if (irReturnType == null && builder.getCurrentBlockLabel() != null) {
        // Current block exists and doesn't have a terminator - add implicit return
        builder.setTerminator(new IrTerminator.IrRetTerm(null));
      }

      // Compute final frame size
      frameLayoutGenerator.computeFrameSize(builder);

      // Build and add function
      IrFunction function = builder.build();
      programBuilder.addFunction(function);
    } finally {
      // Context cleanup handled by StatementGenerator
    }
  }

  private NonTerminalNode findDeclaratorNode(List<ParseNode> children) {
    NonTerminalNode declaratorNode = null;
    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nt) {
        String symbol = nt.symbol();
        if (symbol.equals("<izravni_deklarator>") || symbol.equals("<deklarator>")) {
          declaratorNode = nt;
          break;
        }
      }
    }
    
    if (declaratorNode == null) {
      return null;
    }

    // If we found <deklarator>, find <izravni_deklarator> inside it
    if (declaratorNode.symbol().equals("<deklarator>")) {
      NonTerminalNode innerDeclarator = null;
      for (ParseNode child : declaratorNode.children()) {
        if (child instanceof NonTerminalNode nt && nt.symbol().equals("<izravni_deklarator>")) {
          innerDeclarator = nt;
          break;
        }
      }
      if (innerDeclarator != null) {
        declaratorNode = innerDeclarator;
      } else {
        return null;
      }
    }

    return declaratorNode;
  }
}
