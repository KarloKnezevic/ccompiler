# Semantic Analyzer Implementation

This document provides a comprehensive technical reference for the semantic analysis implementation in the PPJ compiler. It covers the internal architecture, algorithms, data structures, and implementation details necessary for understanding, maintaining, and extending the semantic analyzer.

## Implementation Architecture

The semantic analyzer is implemented as a multi-layered system with clear separation of concerns and modular design principles.

### Core Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    SemanticAnalyzer (Facade)                   │
├─────────────────────────────────────────────────────────────────┤
│  • ParseTree → NonTerminalNode conversion                      │
│  • Analysis orchestration                                      │
│  • Debug report generation                                     │
└─────────────────────┬───────────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────────┐
│                   SemanticChecker (Core Engine)                │
├─────────────────────────────────────────────────────────────────┤
│  • Tree traversal and rule dispatch                            │
│  • Scope management and symbol tracking                        │
│  • Error detection and reporting                               │
│  • Attribute synthesis and propagation                         │
└─────┬───────────────┬───────────────┬─────────────────────────────┘
      │               │               │
┌─────▼─────┐  ┌──────▼──────┐  ┌─────▼──────┐
│Declaration│  │ Expression  │  │ Statement  │
│   Rules   │  │    Rules    │  │   Rules    │
├───────────┤  ├─────────────┤  ├────────────┤
│• Symbols  │  │• Type check │  │• Control   │
│• Scopes   │  │• Operators  │  │  flow      │
│• Functions│  │• Conversions│  │• Blocks    │
└───────────┘  └─────────────┘  └────────────┘
```

### Module Responsibilities

**SemanticAnalyzer**: Entry point facade that handles:
- Parse tree conversion to semantic tree representation
- Analysis orchestration and error handling
- Debug report generation when analysis succeeds
- Integration with the CLI and compilation pipeline

**SemanticChecker**: Core analysis engine implementing:
- Visitor pattern for tree traversal
- Rule dispatch based on production patterns
- Scope stack management and symbol resolution
- Attribute synthesis and propagation
- Centralized error detection and reporting

**Rule Modules**: Specialized handlers for semantic rule categories:
- **DeclarationRules**: Symbol management, function definitions, variable declarations
- **ExpressionRules**: Type checking, operator semantics, implicit conversions
- **StatementRules**: Control flow validation, block scoping, jump statements

## Data Structures

### Semantic Tree Representation

The semantic analyzer operates on a mutable tree structure that augments the parser's immutable parse tree with semantic attributes.

#### NonTerminalNode Structure

```java
public class NonTerminalNode implements ParseNode {
    private final String symbol;                    // Production left-hand side
    private final List<ParseNode> children;         // Ordered child nodes
    private final SemanticAttributes attributes;    // Semantic information
    
    // Navigation and utility methods
    public ParseNode child(int index);
    public int childCount();
    public boolean matches(String... expectedSymbols);
}
```

#### TerminalNode Structure

```java
public record TerminalNode(
    String symbol,      // Terminal symbol (e.g., "IDN", "KR_INT")
    int line,          // Source line number
    String lexeme      // Actual text from source
) implements ParseNode {
    // Immutable terminal representation
}
```

#### SemanticAttributes Container

```java
public class SemanticAttributes {
    private Type type;                          // Computed type
    private boolean lValue;                     // L-value designation
    private boolean isConst;                    // Const qualification
    private String identifier;                  // Declared identifier
    private List<Type> parameterTypes;          // Function parameters
    private List<String> parameterNames;        // Parameter identifiers
    private FunctionType functionType;          // Complete function signature
    private int elementCount;                   // Array element count
    private Type inheritedType;                 // Inherited type attribute
    
    // Fluent setters for attribute manipulation
    public SemanticAttributes type(Type type);
    public SemanticAttributes lValue(boolean lValue);
    public SemanticAttributes identifier(String identifier);
    // ... additional setters
}
```

### Symbol Table Implementation

The symbol table implements hierarchical scoping using a tree structure that mirrors lexical nesting in the source program.

#### SymbolTable Structure

```java
public class SymbolTable {
    private final Map<String, Symbol> entries;     // Current scope symbols
    private final SymbolTable parent;              // Parent scope reference
    
    // Core operations
    public boolean declare(Symbol symbol);          // Add symbol to current scope
    public Symbol lookup(String identifier);       // Search symbol hierarchy
    public SymbolTable enterChildScope();          // Create nested scope
    public SymbolTable exit();                     // Return to parent scope
    
    // Utility operations
    public boolean isDeclaredInCurrentScope(String identifier);
    public Map<String, Symbol> entries();          // Read-only access
    public void dump(PrintWriter writer, int indentLevel);  // Debug output
}
```

#### Symbol Hierarchy

The symbol system uses a sealed interface hierarchy for type safety:

```java
public sealed interface Symbol permits VariableSymbol, FunctionSymbol {
    String name();
    Type type();
}

public record VariableSymbol(
    String name,
    Type type,
    boolean isConst
) implements Symbol {
    // Represents variables and constants
}

public record FunctionSymbol(
    String name,
    FunctionType type,
    boolean defined
) implements Symbol {
    // Represents function declarations and definitions
}
```

### Type System Implementation

The type system provides a comprehensive representation of PPJ-C types with support for const qualification, arrays, and functions.

#### Type Hierarchy

```java
public sealed interface Type permits PrimitiveType, ArrayType, FunctionType, ConstType {
    boolean isVoid();
    boolean isScalar();
    boolean isArray();
    boolean isFunction();
    boolean isConst();
}

public enum PrimitiveType implements Type {
    VOID, CHAR, INT;
    
    @Override
    public boolean isVoid() { return this == VOID; }
    @Override
    public boolean isScalar() { return this != VOID; }
}

public record ArrayType(Type elementType) implements Type {
    @Override
    public boolean isArray() { return true; }
    @Override
    public boolean isScalar() { return false; }
}

public record FunctionType(
    Type returnType,
    List<Type> parameterTypes
) implements Type {
    @Override
    public boolean isFunction() { return true; }
    @Override
    public boolean isScalar() { return false; }
}

public record ConstType(Type baseType) implements Type {
    @Override
    public boolean isConst() { return true; }
    // Delegates other methods to baseType
}
```

#### TypeSystem Utilities

```java
public final class TypeSystem {
    // Const qualification operations
    public static Type stripConst(Type type);
    public static Type applyConst(Type type);
    
    // Type compatibility checking
    public static boolean canAssign(Type from, Type to);
    public static boolean canConvert(Type from, Type to);
    public static boolean isIntConvertible(Type type);
    
    // Type equality and comparison
    public static boolean typesEqual(Type t1, Type t2);
    public static boolean isCompatibleFunctionType(FunctionType f1, FunctionType f2);
}
```

## Analysis Algorithms

### Tree Traversal Strategy

The semantic analyzer employs a recursive descent traversal strategy with rule-based dispatch:

```java
public void visitNonTerminal(NonTerminalNode node) {
    String symbol = node.symbol();
    
    // Dispatch to appropriate rule handler
    if (rules.containsKey(symbol)) {
        rules.get(symbol).apply(node);
    } else {
        // Default traversal for unhandled productions
        for (ParseNode child : node.children()) {
            if (child instanceof NonTerminalNode nonTerminal) {
                visitNonTerminal(nonTerminal);
            }
        }
    }
}
```

### Attribute Synthesis

Semantic attributes are synthesized bottom-up during tree traversal:

1. **Child Processing**: Visit all child nodes to compute their attributes
2. **Rule Application**: Apply semantic rules based on the production pattern
3. **Attribute Computation**: Synthesize parent attributes from child attributes
4. **Constraint Validation**: Verify semantic constraints and report errors

### Scope Management Algorithm

Scope management follows a stack-based approach with RAII semantics:

```java
public void withNewScope(Runnable action) {
    SymbolTable previousScope = currentScope;
    currentScope = currentScope.enterChildScope();
    try {
        action.run();
    } finally {
        currentScope = previousScope;
    }
}
```

## Semantic Rule Implementation

### Declaration Processing

Declaration rules handle symbol introduction and scope management:

#### Variable Declarations

```java
private void visitDeklaracija(NonTerminalNode node) {
    // Process: <ime_tipa> <lista_init_deklaratora>
    NonTerminalNode typeNode = (NonTerminalNode) node.child(0);
    NonTerminalNode declaratorList = (NonTerminalNode) node.child(1);
    
    // Synthesize base type
    visitNonTerminal(typeNode);
    Type baseType = typeNode.attributes().type();
    
    // Validate void type restriction
    if (baseType.isVoid()) {
        fail(node); // Variables cannot have void type
    }
    
    // Process declarator list with inherited type
    declaratorList.attributes().inheritedType(baseType);
    visitNonTerminal(declaratorList);
}
```

#### Function Definitions

```java
private void visitDefinicijaFunkcije(NonTerminalNode node) {
    // Process: <ime_tipa> <izravni_deklarator> <slozena_naredba>
    NonTerminalNode typeNode = (NonTerminalNode) node.child(0);
    NonTerminalNode declarator = (NonTerminalNode) node.child(1);
    NonTerminalNode body = (NonTerminalNode) node.child(2);
    
    // Synthesize return type
    visitNonTerminal(typeNode);
    Type returnType = typeNode.attributes().type();
    
    // Process function declarator
    declarator.attributes().inheritedType(returnType);
    visitNonTerminal(declarator);
    
    String functionName = declarator.attributes().identifier();
    FunctionType functionType = declarator.attributes().functionType();
    
    // Declare or verify function symbol
    FunctionSymbol symbol = new FunctionSymbol(functionName, functionType, true);
    if (!currentScope.declare(symbol)) {
        // Check for compatible redeclaration
        Symbol existing = currentScope.lookup(functionName);
        if (!isCompatibleFunctionRedefinition(existing, symbol)) {
            fail(node);
        }
    }
    
    // Process function body with parameter scope
    withFunctionScope(functionType, () -> {
        visitNonTerminal(body);
    });
}
```

### Expression Type Checking

Expression rules implement type checking and implicit conversion logic:

#### Binary Expression Processing

```java
private void visitBinaryExpression(NonTerminalNode node, BinaryOperator operator) {
    NonTerminalNode left = (NonTerminalNode) node.child(0);
    NonTerminalNode right = (NonTerminalNode) node.child(2);
    
    // Process operands
    visitNonTerminal(left);
    visitNonTerminal(right);
    
    Type leftType = left.attributes().type();
    Type rightType = right.attributes().type();
    
    // Apply operator-specific type rules
    switch (operator) {
        case ARITHMETIC -> {
            if (!TypeSystem.isIntConvertible(leftType) || 
                !TypeSystem.isIntConvertible(rightType)) {
                fail(node);
            }
            node.attributes().type(PrimitiveType.INT);
            node.attributes().lValue(false);
        }
        case RELATIONAL -> {
            if (!TypeSystem.isIntConvertible(leftType) || 
                !TypeSystem.isIntConvertible(rightType)) {
                fail(node);
            }
            node.attributes().type(PrimitiveType.INT);
            node.attributes().lValue(false);
        }
        case ASSIGNMENT -> {
            if (!left.attributes().isLValue() || 
                !TypeSystem.canAssign(rightType, leftType)) {
                fail(node);
            }
            node.attributes().type(leftType);
            node.attributes().lValue(false);
        }
    }
}
```

#### Array Indexing Validation

```java
private void handleArrayElement(NonTerminalNode node) {
    // Process: <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
    NonTerminalNode base = (NonTerminalNode) node.child(0);
    NonTerminalNode index = (NonTerminalNode) node.child(2);
    
    visitNonTerminal(base);
    visitNonTerminal(index);
    
    Type baseType = base.attributes().type();
    Type indexType = index.attributes().type();
    
    // Validate array base type
    Type stripped = TypeSystem.stripConst(baseType);
    if (!(stripped instanceof ArrayType arrayType)) {
        fail(node);
    }
    
    // Validate index type
    if (!TypeSystem.isIntConvertible(indexType)) {
        fail(node);
    }
    
    // Synthesize result attributes
    node.attributes().type(arrayType.elementType());
    node.attributes().lValue(true);
}
```

### Statement Validation

Statement rules handle control flow and block structure validation:

#### Block Statement Processing

```java
private void processBlock(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    
    if (children.size() == 2) {
        // Empty block: L_VIT_ZAGRADA D_VIT_ZAGRADA
        return; // Empty blocks are explicitly allowed
    }
    
    // Process block contents within new scope
    withNewScope(() -> {
        if (children.size() == 3) {
            // Block with declarations or statements only
            visitNonTerminal((NonTerminalNode) children.get(1));
        } else if (children.size() == 4) {
            // Block with both declarations and statements
            visitNonTerminal((NonTerminalNode) children.get(1)); // declarations
            visitNonTerminal((NonTerminalNode) children.get(2)); // statements
        } else {
            fail(node);
        }
    });
}
```

#### Return Statement Validation

```java
private void handleReturn(NonTerminalNode node) {
    List<ParseNode> children = node.children();
    
    if (children.size() == 2) {
        // return;
        if (!currentFunction.returnType().isVoid()) {
            fail(node); // Non-void functions require return expression
        }
    } else if (children.size() == 3) {
        // return <expression>;
        NonTerminalNode expr = (NonTerminalNode) children.get(1);
        visitNonTerminal(expr);
        
        if (currentFunction.returnType().isVoid()) {
            fail(node); // Void functions cannot return expressions
        }
        
        if (!TypeSystem.canAssign(expr.attributes().type(), currentFunction.returnType())) {
            fail(node); // Return type mismatch
        }
    } else {
        fail(node);
    }
}
```

## Error Handling Implementation

### Error Detection Strategy

The semantic analyzer implements a fail-fast error detection strategy:

```java
private void fail(NonTerminalNode node) {
    String production = ProductionFormatter.format(node);
    System.out.println(production);
    System.out.println();
    System.out.println("semantic error");
    throw new SemanticException("Semantic analysis failed at: " + production);
}
```

### Production Formatting

Error messages include the exact production where the error occurred:

```java
public class ProductionFormatter {
    public static String format(NonTerminalNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append("<").append(node.symbol()).append("> ::=");
        
        for (ParseNode child : node.children()) {
            sb.append(" ");
            if (child instanceof TerminalNode terminal) {
                sb.append(terminal.symbol())
                  .append("(").append(terminal.line())
                  .append(",").append(terminal.lexeme()).append(")");
            } else {
                sb.append("<").append(child.symbol()).append(">");
            }
        }
        
        return sb.toString();
    }
}
```

## Debug Output Generation

### Symbol Table Dumping

The semantic analyzer can generate detailed symbol table dumps for debugging:

```java
public void writeSymbolTable(SymbolTable globalScope) {
    Path outputFile = outputDirectory.resolve("tablica_simbola.txt");
    try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputFile))) {
        writer.println("=== SYMBOL TABLE DUMP ===");
        dumpSymbolTableRecursive(globalScope, writer, 0);
    } catch (IOException e) {
        System.err.println("Warning: Failed to write symbol table report: " + e.getMessage());
    }
}

private void dumpSymbolTableRecursive(SymbolTable scope, PrintWriter writer, int level) {
    String indent = "  ".repeat(level);
    writer.printf("%sScope (Level %d):%n", indent, level);
    
    if (scope.entries().isEmpty()) {
        writer.printf("%s  (no symbols)%n", indent);
    } else {
        for (Map.Entry<String, Symbol> entry : scope.entries().entrySet()) {
            Symbol symbol = entry.getValue();
            writer.printf("%s  %s : %s", indent, symbol.name(), symbol.type());
            
            if (symbol instanceof FunctionSymbol function) {
                writer.printf(" [defined=%s]", function.defined());
            } else if (symbol instanceof VariableSymbol variable) {
                writer.printf(" [const=%s]", variable.isConst());
            }
            writer.println();
        }
    }
}
```

### Semantic Tree Dumping

Complete semantic tree structures can be dumped with all attributes:

```java
public void writeSemanticTree(NonTerminalNode root) {
    Path outputFile = outputDirectory.resolve("semanticko_stablo.txt");
    try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputFile))) {
        writer.println("=== SEMANTIC TREE DUMP ===");
        dumpSemanticTreeRecursive(root, writer, 0);
    } catch (IOException e) {
        System.err.println("Warning: Failed to write semantic tree report: " + e.getMessage());
    }
}

private void dumpSemanticTreeRecursive(ParseNode node, PrintWriter writer, int level) {
    String indent = "  ".repeat(level);
    
    if (node instanceof NonTerminalNode nonTerminal) {
        writer.printf("%s<<%s>> [type=%s, lvalue=%s, id=%s, elements=%d]%n",
            indent,
            nonTerminal.symbol(),
            nonTerminal.attributes().type(),
            nonTerminal.attributes().isLValue(),
            nonTerminal.attributes().identifier(),
            nonTerminal.attributes().elementCount());
            
        for (ParseNode child : nonTerminal.children()) {
            dumpSemanticTreeRecursive(child, writer, level + 1);
        }
    } else if (node instanceof TerminalNode terminal) {
        writer.printf("%s%s (%d,%s) [symbol=%s]%n",
            indent,
            terminal.symbol(),
            terminal.line(),
            terminal.lexeme(),
            terminal.symbol());
    }
}
```

## Extension Points

### Adding New Semantic Rules

To add new semantic rules to the analyzer:

1. **Update Specification**: Add the new rule to `config/semantics_definition.txt`
2. **Implement Handler**: Create a new rule handler method in the appropriate rule module
3. **Register Rule**: Add the rule to the dispatch table in `SemanticChecker`
4. **Add Tests**: Create unit tests to verify the new rule behavior
5. **Update Documentation**: Document the new rule and its implementation

### Extending Type System

The type system can be extended by:

1. **Adding New Types**: Implement new `Type` interface implementations
2. **Updating TypeSystem**: Add compatibility rules for new types
3. **Modifying Rules**: Update expression and declaration rules to handle new types
4. **Testing**: Ensure comprehensive test coverage for new type interactions

### Enhancing Error Reporting

Error reporting can be enhanced by:

1. **Adding Context**: Include more contextual information in error messages
2. **Multiple Errors**: Implement error recovery to report multiple errors
3. **Warnings**: Add warning system for non-fatal semantic issues
4. **Suggestions**: Provide fix suggestions for common semantic errors

This implementation provides a robust foundation for semantic analysis while maintaining extensibility and maintainability through its modular architecture and clear separation of concerns.