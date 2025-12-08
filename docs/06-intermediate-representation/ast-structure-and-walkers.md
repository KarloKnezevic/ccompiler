# AST Structure and Walkers

## Overview

This document provides detailed specifications for AST node structures and traversal mechanisms used in the PPJ compiler. The AST serves as the intermediate representation between parsing and code generation.

## AST Node Interface

All AST nodes implement the `ASTNode` interface:

```java
public interface ASTNode {
    int line();
    int column();  // Optional, may be -1 if not tracked
}
```

## Declaration Nodes

### VariableDeclaration

Represents variable declarations:

```java
public record VariableDeclaration(
    Type type,
    String name,
    Expression initializer,  // null if uninitialized
    boolean isConst,
    int line
) implements Declaration, ASTNode
```

**Fields**:
- `type`: Variable type (int, char, array, pointer, etc.)
- `name`: Variable identifier
- `initializer`: Initialization expression (null if uninitialized)
- `isConst`: Const qualification flag
- `line`: Source line number

**Usage**: Global and local variable declarations

### FunctionDeclaration

Represents function definitions:

```java
public record FunctionDeclaration(
    Type returnType,
    String name,
    List<Parameter> parameters,
    BlockStatement body,
    int line
) implements Declaration, ASTNode
```

**Fields**:
- `returnType`: Function return type (void, int, char, etc.)
- `name`: Function identifier
- `parameters`: Function parameter list
- `body`: Function body (block statement)
- `line`: Source line number

**Usage**: Function definitions

### StructDeclaration

Represents struct type definitions:

```java
public record StructDeclaration(
    String tagName,  // null for anonymous structs
    List<StructField> fields,
    int line
) implements Declaration, ASTNode
```

**Fields**:
- `tagName`: Struct tag name (null for anonymous structs)
- `fields`: List of struct fields
- `line`: Source line number

**Usage**: Struct type definitions

## Statement Nodes

### BlockStatement

Represents compound statements (`{ }`):

```java
public record BlockStatement(
    List<Statement> statements,
    int line
) implements Statement, ASTNode
```

**Fields**:
- `statements`: List of statements in block
- `line`: Source line number

**Usage**: Function bodies, block statements

### IfStatement

Represents conditional statements:

```java
public record IfStatement(
    Expression condition,
    Statement thenBranch,
    Statement elseBranch,  // null if no else clause
    int line
) implements Statement, ASTNode
```

**Fields**:
- `condition`: Boolean expression
- `thenBranch`: Statement executed if condition is true
- `elseBranch`: Statement executed if condition is false (null if no else)
- `line`: Source line number

**Usage**: `if` and `if-else` statements

### WhileStatement

Represents while loops:

```java
public record WhileStatement(
    Expression condition,
    Statement body,
    int line
) implements Statement, ASTNode
```

**Fields**:
- `condition`: Loop condition expression
- `body`: Loop body statement
- `line`: Source line number

**Usage**: `while` loops

### ForStatement

Represents for loops:

```java
public record ForStatement(
    ExpressionStatement init,    // null if no initialization
    Expression condition,         // null if no condition
    Expression increment,         // null if no increment
    Statement body,
    int line
) implements Statement, ASTNode
```

**Fields**:
- `init`: Initialization expression statement (null if omitted)
- `condition`: Loop condition expression (null if omitted)
- `increment`: Increment expression (null if omitted)
- `body`: Loop body statement
- `line`: Source line number

**Usage**: `for` loops

### ReturnStatement

Represents return statements:

```java
public record ReturnStatement(
    Expression value,  // null for void returns
    int line
) implements Statement, ASTNode
```

**Fields**:
- `value`: Return value expression (null for void functions)
- `line`: Source line number

**Usage**: `return` statements

### BreakStatement

Represents break statements:

```java
public record BreakStatement(
    int line
) implements Statement, ASTNode
```

**Usage**: `break` statements in loops

### ContinueStatement

Represents continue statements:

```java
public record ContinueStatement(
    int line
) implements Statement, ASTNode
```

**Usage**: `continue` statements in loops

## Expression Nodes

### BinaryExpression

Represents binary operations:

```java
public record BinaryExpression(
    Expression left,
    BinaryOperator operator,
    Expression right,
    int line
) implements Expression, ASTNode
```

**Operators**:
- Arithmetic: `+`, `-`, `*`, `/`, `%`
- Relational: `<`, `>`, `<=`, `>=`, `==`, `!=`
- Logical: `&&`, `||`
- Bitwise: `&`, `|`, `^`

**Usage**: Binary operator expressions

### UnaryExpression

Represents unary operations:

```java
public record UnaryExpression(
    UnaryOperator operator,
    Expression operand,
    int line
) implements Expression, ASTNode
```

**Operators**:
- Arithmetic: `+`, `-`
- Logical: `!`
- Bitwise: `~`
- Increment/Decrement: `++`, `--` (prefix)

**Usage**: Unary operator expressions

### AssignmentExpression

Represents assignment operations:

```java
public record AssignmentExpression(
    Expression left,   // Must be L-value
    Expression right,
    int line
) implements Expression, ASTNode
```

**Usage**: Assignment expressions (`=`)

### PrimaryExpression

Base interface for primary expressions:

```java
public sealed interface PrimaryExpression
    permits Identifier, Literal, FunctionCall, ArrayAccess, ParenthesizedExpression
```

### Identifier

Represents identifier references:

```java
public record Identifier(
    String name,
    int line
) implements PrimaryExpression, Expression, ASTNode
```

**Usage**: Variable and function name references

### Literal

Represents literal values:

```java
public sealed interface Literal
    permits IntegerLiteral, CharacterLiteral, StringLiteral, FloatLiteral
```

**IntegerLiteral**:
```java
public record IntegerLiteral(
    int value,
    int line
) implements Literal, PrimaryExpression, Expression, ASTNode
```

**CharacterLiteral**:
```java
public record CharacterLiteral(
    char value,
    int line
) implements Literal, PrimaryExpression, Expression, ASTNode
```

**StringLiteral**:
```java
public record StringLiteral(
    String value,
    int line
) implements Literal, PrimaryExpression, Expression, ASTNode
```

### FunctionCall

Represents function calls:

```java
public record FunctionCall(
    Expression function,  // Identifier or function pointer
    List<Expression> arguments,
    int line
) implements PrimaryExpression, Expression, ASTNode
```

**Usage**: Function call expressions

### ArrayAccess

Represents array indexing:

```java
public record ArrayAccess(
    Expression array,
    Expression index,
    int line
) implements PrimaryExpression, Expression, ASTNode
```

**Usage**: Array element access (`array[index]`)

## AST Walker Interface

### Visitor Pattern

The AST uses visitor pattern for traversal:

```java
public interface ASTVisitor<T> {
    // Declaration visitors
    T visitVariableDeclaration(VariableDeclaration node);
    T visitFunctionDeclaration(FunctionDeclaration node);
    T visitStructDeclaration(StructDeclaration node);
    
    // Statement visitors
    T visitBlockStatement(BlockStatement node);
    T visitIfStatement(IfStatement node);
    T visitWhileStatement(WhileStatement node);
    T visitForStatement(ForStatement node);
    T visitReturnStatement(ReturnStatement node);
    T visitBreakStatement(BreakStatement node);
    T visitContinueStatement(ContinueStatement node);
    
    // Expression visitors
    T visitBinaryExpression(BinaryExpression node);
    T visitUnaryExpression(UnaryExpression node);
    T visitAssignmentExpression(AssignmentExpression node);
    T visitIdentifier(Identifier node);
    T visitLiteral(Literal node);
    T visitFunctionCall(FunctionCall node);
    T visitArrayAccess(ArrayAccess node);
}
```

### Traversal Strategies

**Pre-order Traversal**:
```java
public T visitNode(ASTNode node) {
    // Process node
    process(node);
    
    // Visit children
    for (ASTNode child : node.children()) {
        visitNode(child);
    }
}
```

**Post-order Traversal**:
```java
public T visitNode(ASTNode node) {
    // Visit children first
    for (ASTNode child : node.children()) {
        visitNode(child);
    }
    
    // Process node
    process(node);
}
```

**In-order Traversal** (for binary expressions):
```java
public T visitBinaryExpression(BinaryExpression node) {
    visitNode(node.left());      // Left child
    process(node);                // Node itself
    visitNode(node.right());     // Right child
}
```

## AST Usage in Compiler Phases

### Semantic Analysis

**Type Checking**: Post-order traversal to synthesize types
**Symbol Resolution**: Pre-order traversal to build symbol tables
**Scope Management**: Pre-order traversal to enter/exit scopes

### Code Generation

**Expression Code Generation**: Post-order traversal to generate expression code
**Statement Code Generation**: Pre-order traversal to generate statement code
**Function Code Generation**: Pre-order traversal to generate function code

## Further Reading

- **[IR Design](ir-design.md)**: Overall IR architecture
- **[Semantic Analysis](../05-semantic-analysis/semantic-passes.md)**: AST annotation process
- **[Code Generation](../07-code-generation/instruction-selection.md)**: AST to assembly translation

---

*The AST structure provides a clean, type-safe representation of program structure, enabling efficient semantic analysis and code generation.*
