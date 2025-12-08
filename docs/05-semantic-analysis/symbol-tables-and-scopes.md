# Symbol Tables and Scopes

The semantic analyzer constitutes the third phase of the PPJ compiler pipeline, responsible for enforcing language-specific semantic constraints that cannot be expressed through context-free grammars. This phase validates type compatibility, scope resolution, control flow semantics, and ensures adherence to the PPJ-C language specification.

**See Also**:
- **[Type System and Checking](type-system-and-checking.md)**: Detailed type system implementation
- **[Semantic Passes](semantic-passes.md)**: Semantic analysis pipeline and passes
- **[Syntax Analysis](../04-syntax-analysis/grammar-specification.md)**: Parser output format
- **[Intermediate Representation](../06-intermediate-representation/ir-design.md)**: AST structure used by semantic analyzer

## Compilation Pipeline Integration

The semantic analyzer operates as an intermediate phase between syntactic analysis and code generation:

```
Source Code → Lexical Analysis → Syntactic Analysis → Semantic Analysis → Code Generation
    (.c)           (tokens)         (parse tree)      (validated AST)     (assembly)
```

**Input Contract**: The analyzer receives a complete parse tree (`ParseTree`) from the LR(1) parser, representing a syntactically valid PPJ-C program according to the grammar defined in `config/parser_definition.txt`.

**Output Contract**: Upon successful analysis, the analyzer produces no output and allows compilation to proceed. Upon detecting semantic violations, it emits a single canonical error message and terminates compilation.

**Semantic Specification**: All semantic rules are formally defined in `config/semantics_definition.txt`, which serves as the authoritative specification for type checking, scope resolution, and semantic validation.

## Core Responsibilities

### Type System Enforcement

The analyzer implements a comprehensive static type system with the following primitive types:
- `void`: Used exclusively for function return types and parameter lists
- `char`: 8-bit signed integer type
- `int`: 32-bit signed integer type
- `float`: 32-bit floating-point type

Composite types include:
- **Array types**: `T[]` where T is any non-void type
- **Pointer types**: `T*` where T is any type (including `void*`)
- **Struct types**: Named or anonymous structures with named fields
- **Function types**: `T(T1, T2, ..., Tn)` representing function signatures
- **Const-qualified types**: `const T` for immutable values

Type compatibility rules enforce:
- Implicit conversions between `char`, `int`, and `float` following C's usual arithmetic conversions
- Pointer compatibility (same base type, array-to-pointer decay)
- Assignment compatibility with const-qualification constraints
- Array-to-pointer decay in function parameters and expressions
- Function signature matching for calls and definitions
- Struct type equality (by tag name for tagged structs, by structure for anonymous structs)

### Scope and Symbol Management

The analyzer maintains a hierarchical symbol table structure that mirrors lexical scoping:

```
Global Scope
├── Function declarations/definitions
├── Global variable declarations
├── Struct tag definitions
└── Block Scopes (nested)
    ├── Local variable declarations
    ├── Function parameters
    └── Nested block scopes
```

**Symbol Table Structure**: Each scope maintains a mapping from identifiers to symbol records:
- **Variable Symbols**: Store type information, const-qualification, and declaration location
- **Function Symbols**: Store function signatures, definition status, and parameter metadata

**Struct Tag Table**: A separate global table tracks struct type definitions by tag name, enabling forward declarations and self-referential structures.

**Scope Resolution**: Identifier lookup follows lexical scoping rules, searching from innermost to outermost scope until a matching declaration is found.

### Symbol Table Implementation

The symbol table is implemented as a hierarchical structure using the `SymbolTable` class:

```java
public class SymbolTable {
    private final Map<String, Symbol> entries;
    private final SymbolTable parent;
    
    public boolean declare(Symbol symbol);
    public Symbol lookup(String identifier);
    public SymbolTable enterChildScope();
    public SymbolTable exit();
}
```

**Example Symbol Table Structure**:
```
Global Scope (Level 0):
  main : int(void) [defined=true]
  factorial : int(int) [defined=true]
  global_var : int [const=false]
  struct Node : StructType [tag=Node, fields={data:int, next:Node*}]

Function Scope - factorial (Level 1):
  n : int [const=false]
  
Block Scope - if statement (Level 2):
  result : int [const=false]
```

The symbol table supports:
- **Declaration**: Adding new symbols to the current scope with duplicate detection
- **Lookup**: Searching for symbols following lexical scoping rules
- **Scope Management**: Creating and destroying nested scopes with RAII semantics

### Semantic Tree Representation

The analyzer constructs a semantic tree that augments the parse tree with semantic attributes:

```java
public class NonTerminalNode implements ParseNode {
    private final String symbol;
    private final List<ParseNode> children;
    private final SemanticAttributes attributes;
}

public class SemanticAttributes {
    private Type type;                          // Computed type
    private boolean lValue;                     // L-value designation
    private boolean isConst;                    // Const qualification
    private String identifier;                  // Declared identifier
    private List<Type> parameterTypes;          // Function parameters
    private FunctionType functionType;          // Complete function signature
    private Map<String, Type> structFields;     // Struct field types
}
```

**Semantic Tree Example**:
```
<definicija_funkcije> [type=int(int), id=factorial]
├── <ime_tipa> [type=int]
│   └── KR_INT (1,int)
├── <izravni_deklarator> [type=int(int), id=factorial]
│   ├── IDN (1,factorial)
│   ├── L_ZAGRADA (1,()
│   ├── <lista_parametara> [paramTypes=[int]]
│   │   └── <deklaracija_parametra> [type=int, id=n]
│   │       ├── <ime_tipa> [type=int]
│   │       │   └── KR_INT (1,int)
│   │       └── IDN (1,n)
│   └── D_ZAGRADA (1,))
└── <slozena_naredba>
    ├── L_VIT_ZAGRADA (2,{)
    ├── <lista_naredbi>
    │   └── <naredba_skoka>
    │       ├── KR_RETURN (3,return)
    │       ├── <izraz> [type=int, lvalue=false]
    │       │   └── <primarni_izraz> [type=int, lvalue=true]
    │       │       └── IDN (3,n)
    │       └── TOCKAZAREZ (3,;)
    └── D_VIT_ZAGRADA (4,})
```

### Control Flow Validation

The analyzer enforces control flow semantics including:

**Jump Statement Validation**:
- `break` and `continue` statements must appear within loop constructs
- `return` statements must match function return types
- `return;` is valid only in `void` functions
- `return expression;` requires type compatibility with function signature

**Loop Construct Validation**:
- Loop conditions must be scalar types (int, char, float, or pointer)
- Empty loop bodies (`{}`) are explicitly permitted
- Nested loop depth tracking for jump statement validation

**Block Structure Validation**:
- Empty blocks (`{}`) are valid compound statements
- Block scopes properly nest and maintain symbol visibility
- Variable declarations follow C-style scoping rules

## Architecture Overview

The semantic analyzer employs a modular architecture with clear separation of concerns:

```
SemanticAnalyzer (Facade)
├── ParseTreeConverter (Tree Conversion)
├── SemanticChecker (Core Analysis Engine)
│   ├── DeclarationRules (Symbol Management)
│   │   ├── TypeSpecificationRules
│   │   ├── DeclaratorRules
│   │   ├── StructRules
│   │   ├── ParameterRules
│   │   └── InitializerRules
│   ├── ExpressionRules (Type Checking)
│   │   ├── PrimaryExpressionRules
│   │   ├── PostfixExpressionRules
│   │   ├── UnaryExpressionRules
│   │   └── BinaryExpressionRules
│   ├── StatementRules (Control Flow)
│   │   ├── ControlFlowRules
│   │   └── JumpStatementRules
│   └── GlobalConstraintVerifier
├── SymbolTable (Scope Management)
├── TypeSystem (Type Operations)
└── SemanticReport (Debug Output)
```

### Core Components

**SemanticAnalyzer**: Provides the primary API for semantic analysis, handling tree conversion and orchestrating the analysis process.

**SemanticChecker**: Implements the core semantic analysis logic using a visitor pattern to traverse the semantic tree and apply semantic rules.

**Rule Modules**: Specialized handlers for different categories of semantic rules:
- `DeclarationRules`: Handles variable and function declarations, parameter processing, and symbol table management
- `ExpressionRules`: Implements type checking for all expression forms, operator semantics, and type conversions
- `StatementRules`: Manages control flow validation, block scoping, and jump statement semantics

**Type System**: Provides utilities for type representation, compatibility checking, const-qualification handling, and arithmetic conversions.

## Grammar and Language Subset

The semantic analyzer supports the following C subset as defined by the grammar:

### Primitive Types

- `void`: Function return types and parameter lists only
- `char`: 8-bit signed integer
- `int`: 32-bit signed integer
- `float`: 32-bit floating-point

### Composite Types

- **Arrays**: `T[N]` where T is any non-void type and N is a compile-time constant
- **Pointers**: `T*` where T is any type, with optional `const` qualifier on the pointer itself
- **Structs**: Named (`struct Tag { ... }`) or anonymous (`struct { ... }`) structures with named fields
- **Functions**: `T(T1, T2, ..., Tn)` representing function signatures

### Type Qualifiers

- `const`: Applied to any type, making values immutable

### Expressions

**Primary expressions**:
- Identifiers (variables, functions)
- Numeric literals (integer, float, character)
- String literals
- Parenthesized expressions

**Postfix expressions**:
- Array indexing: `array[index]`
- Function calls: `func()` or `func(arg1, arg2, ...)`
- Struct member access: `struct.field`
- Postfix increment/decrement: `expr++`, `expr--`

**Unary expressions**:
- Prefix increment/decrement: `++expr`, `--expr`
- Unary operators: `+expr`, `-expr`, `~expr`, `!expr`
- Address-of: `&expr` (requires l-value)
- Dereference: `*expr` (requires pointer type)

**Cast expressions**:
- Explicit type casts: `(type) expr`

**Binary operators**:
- Multiplicative: `*`, `/`, `%`
- Additive: `+`, `-`
- Relational: `<`, `>`, `<=`, `>=`
- Equality: `==`, `!=`
- Bitwise: `&`, `^`, `|`
- Logical: `&&`, `||`
- Assignment: `=`

### Statements and Control Flow

- **Compound statements**: `{ declarations statements }`
- **Expression statements**: `expression;`
- **Conditional**: `if (condition) statement` and `if (condition) statement else statement`
- **Loops**: `while (condition) statement` and `for (init; condition; update) statement`
- **Jump statements**: `break;`, `continue;`, `return;`, `return expression;`

### Declarations

- **Global variables**: Declared at translation unit level
- **Local variables**: Declared within function bodies or blocks
- **Function definitions**: `return_type name(parameters) { body }`
- **Function parameters**: Typed parameters, arrays decay to pointers
- **Arrays**: Fixed-size arrays with compile-time constant size
- **Struct declarations**: Field declarations within struct definitions
- **Pointer declarators**: `*identifier` or `*const identifier`

## Type System Details

### Type Representation

Types are represented using a sealed interface hierarchy:

- **PrimitiveType**: Enum for `VOID`, `CHAR`, `INT`, `FLOAT`
- **ArrayType**: `record ArrayType(Type elementType)`
- **PointerType**: `record PointerType(Type baseType, boolean isConst)`
- **StructType**: `record StructType(String tag, Map<String, Type> fields)`
- **FunctionType**: `record FunctionType(Type returnType, List<Type> parameterTypes)`
- **ConstType**: `record ConstType(Type baseType)` - wraps any type with const qualifier

### Type Compatibility Rules

**Assignment Compatibility**:
- Numeric types: `char` → `int` → `float` (implicit promotion)
- Pointers: Same base type (ignoring const on pointed-to type), or `int 0` → any pointer (NULL)
- Arrays: Decay to pointer in assignment contexts
- Structs: Exact type match required
- Const: Can assign non-const to const, but not vice versa

**Arithmetic Conversions** (C's usual arithmetic conversions):
- If either operand is `float`, result is `float`
- Otherwise, both operands promoted to `int`, result is `int`
- Applies to: `char`, `int`, `float` in arithmetic operations

**Pointer Operations**:
- Pointer arithmetic: `pointer + int` or `pointer - int` (result is pointer)
- Pointer subtraction: `pointer - pointer` (result is `int`)
- Pointer comparison: Pointers to same base type can be compared
- Array indexing: Arrays decay to pointers, `array[i]` is equivalent to `*(array + i)`

**Cast Rules**:
- Numeric types can be cast to each other
- Pointers can be cast to pointers (if compatible base types)
- Pointers can be cast to integers
- Integers can be cast to pointers
- Struct casts only allowed between identical struct types

### Struct Type System

**Struct Definitions**:
- Tagged structs: `struct Tag { ... }` - can be forward declared
- Anonymous structs: `struct { ... }` - cannot be referenced later

**Forward Declarations**:
- Struct tags can be forward declared with empty field list
- Allows self-referential structures: `struct Node { struct Node *next; }`
- Forward declaration is replaced with full definition when fields are processed

**Struct Field Access**:
- Field access: `struct.field` (base must be struct type, field must exist)
- Pointer dereference: `(*ptr).field` (grammar limitation: no `->` operator)

## Error Reporting

The semantic analyzer implements a fail-fast error reporting strategy:

**Error Format**: Semantic errors are reported using a canonical format that identifies the specific production rule where the error occurred:

```
<production_name> ::= TERMINAL(line,lexeme) NON_TERMINAL ...

semantic error
```

**Example Error Output**:
```
<primarni_izraz> ::= IDN(8,undefined_variable)

semantic error
```

**Error Handling Strategy**:
- Analysis terminates immediately upon detecting the first semantic error
- No error recovery or multiple error reporting is attempted
- Deterministic error reporting ensures consistent behavior for automated testing

## Debug Output Generation

When semantic analysis completes successfully, the analyzer can generate detailed debug information:

### Symbol Table Dump (`tablica_simbola.txt`)

```
=== SYMBOL TABLE DUMP ===
Scope (Level 0):
  main : int(void) [defined=true]
  factorial : int(int) [defined=true]
  global_var : int [const=false]

Struct Tags:
  Node : StructType [tag=Node, fields={data:int, next:Node*}]

Scope (Level 1):
  n : int [const=false]
  local_result : int [const=false]
```

### Semantic Tree Dump (`semanticko_stablo.txt`)

```
=== SEMANTIC TREE DUMP ===
<<prijevodna_jedinica>> [type=null, lvalue=false, id=null, elements=0]
  <<vanjska_deklaracija>> [type=null, lvalue=false, id=null, elements=0]
    <<definicija_funkcije>> [type=int(int), lvalue=false, id=factorial, elements=0]
      <<ime_tipa>> [type=int, lvalue=false, id=null, elements=0]
        KR_INT (1,int) [symbol=null, type=null]
      <<izravni_deklarator>> [type=int(int), lvalue=false, id=factorial, elements=0]
        IDN (1,factorial) [symbol=null, type=null]
        L_ZAGRADA (1,() [symbol=null, type=null]
        <<lista_parametara>> [type=null, lvalue=false, id=null, elements=0]
          <<deklaracija_parametra>> [type=int, lvalue=true, id=n, elements=0]
            <<ime_tipa>> [type=int, lvalue=false, id=null, elements=0]
              KR_INT (1,int) [symbol=null, type=null]
            IDN (1,n) [symbol=null, type=null]
        D_ZAGRADA (1,)) [symbol=null, type=null]
      <<slozena_naredba>> [type=null, lvalue=false, id=null, elements=0]
        L_VIT_ZAGRADA (2,{) [symbol=null, type=null]
        <<lista_naredbi>> [type=null, lvalue=false, id=null, elements=0]
          <<naredba_skoka>> [type=null, lvalue=false, id=null, elements=0]
            KR_RETURN (3,return) [symbol=null, type=null]
            <<izraz>> [type=int, lvalue=true, id=null, elements=0]
              <<primarni_izraz>> [type=int, lvalue=true, id=null, elements=0]
                IDN (3,n) [symbol=null, type=null]
            TOCKAZAREZ (3,;) [symbol=null, type=null]
        D_VIT_ZAGRADA (4,}) [symbol=null, type=null]
```

These debug files provide comprehensive insight into the semantic analysis process and are invaluable for understanding symbol resolution and type inference.

## Usage and Integration

### Command Line Interface

The semantic analyzer is accessible through the CLI with multiple invocation modes:

```bash
# Semantic analysis only
java -jar cli/target/ccompiler.jar semantic program.c

# Full compilation pipeline
java -jar cli/target/ccompiler.jar program.c
```

### Programmatic Interface

```java
// Create analyzer instance
SemanticAnalyzer analyzer = new SemanticAnalyzer();

// Create debug report generator (optional)
SemanticReport report = new SemanticReport(outputDirectory);

// Perform analysis
try {
    analyzer.analyze(parseTree, System.out, report);
    System.err.println("Semantic analysis completed successfully.");
} catch (SemanticException e) {
    System.err.println("Error: semantic error");
    System.exit(1);
}
```

### Integration with Build Pipeline

The semantic analyzer integrates seamlessly with the overall compilation pipeline:

1. **Input Validation**: Receives validated parse trees from the LR(1) parser
2. **Symbol Resolution**: Builds comprehensive symbol tables for all scopes
3. **Type Checking**: Validates all expressions and statements according to PPJ-C semantics
4. **Output Generation**: Produces debug information and validates program correctness
5. **Error Handling**: Provides deterministic error reporting for invalid programs

The analyzer ensures that only semantically valid programs proceed to the code generation phase, maintaining the integrity of the compilation pipeline and enabling reliable code generation.
