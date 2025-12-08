# Glossary

## Introduction

This glossary provides definitions for key terms used throughout the PPJ compiler documentation. Terms are organized alphabetically and include cross-references to related concepts. Each definition provides a clear, concise explanation suitable for readers learning compiler construction.

When a term appears in **bold** within a definition, it indicates that term is also defined in this glossary. Related terms are listed at the end of each definition where applicable.

## A

**Abstract Syntax Tree (AST)**: A tree representation of program structure that abstracts away syntactic details (such as parentheses, semicolons, and keyword placement) while preserving semantic structure (such as expression hierarchy, control flow, and data dependencies). The AST is used by semantic analysis and code generation phases. Unlike parse trees, ASTs remove non-semantic intermediate nodes, making them simpler and more suitable for program analysis. **Related terms**: Parse Tree, Intermediate Representation, Syntax Tree.

**Activation Record**: A data structure representing a function call's **stack frame**, containing all information needed for a single function invocation: parameters passed to the function, local variables allocated by the function, saved register values, and the return address. Each function call creates a new activation record on the stack. Activation records are managed according to the calling convention, with the caller and callee each responsible for specific parts of frame setup and cleanup. **Related terms**: Stack Frame, Calling Convention, Frame Pointer.

**Attribute Grammar**: A grammar extended with **attributes** (values associated with grammar symbols) and **semantic rules** (computations that define attribute values). Attributes can be **synthesized** (computed from children to parent, flowing bottom-up) or **inherited** (computed from parent to children, flowing top-down). Attribute grammars provide a formal framework for specifying semantic analysis, allowing type checking and other semantic computations to be defined alongside syntax rules. **Related terms**: Synthesized Attribute, Inherited Attribute, Semantic Analysis.

**Automaton** (plural: **Automata**): A computational model for recognizing formal languages. An automaton reads input symbols and transitions between states based on the current state and input symbol. Automata are classified by their capabilities: **finite automata** recognize regular languages, **pushdown automata** recognize context-free languages, and **Turing machines** recognize recursively enumerable languages. The PPJ compiler uses finite automata (specifically DFAs) for lexical analysis. **Related terms**: DFA, NFA, Finite Automaton, State Machine.

## B

**Backtracking**: In lexical analysis, returning to a previous position in input to try alternative matches.

**Bottom-Up Parsing**: A parsing strategy that builds parse trees from leaves to root (e.g., LR parsing).

## C

**Canonical LR(1)**: A variant of LR parsing that uses full lookahead sets, providing maximum parsing power but larger tables.

**Code Generation**: The compiler phase that transforms intermediate representation into target machine code.

**Context-Free Grammar (CFG)**: A grammar where production rules depend only on a single non-terminal, not on context.

**Const Qualification**: A type qualifier (`const`) indicating that a value cannot be modified.

## D

**Deterministic Finite Automaton (DFA)**: A **finite automaton** where, for each state and input symbol, there is **exactly one** transition. This determinism means that the automaton's behavior is completely predictable—given a state and input symbol, there's no ambiguity about which state to transition to. DFAs are used in the PPJ compiler's lexical analyzer for efficient token recognition. DFAs can be constructed from NFAs using the **subset construction algorithm**. **Related terms**: NFA, Finite Automaton, Subset Construction, Lexical Analysis.

**Derivation**: A sequence of **production** applications that transforms the grammar's **start symbol** into a string of **terminals** (tokens). A derivation shows how a program can be constructed from the grammar rules. For example, the derivation `S → E → E + T → T + T → id + T → id + id` shows how the expression `id + id` can be derived from the start symbol S. Derivations can be **leftmost** (always expand the leftmost non-terminal) or **rightmost** (always expand the rightmost non-terminal). **Related terms**: Production, Grammar, Parse Tree, Start Symbol.

## E

**ε-NFA**: A nondeterministic finite automaton that allows epsilon (empty string) transitions.

**Epsilon Production**: A production with empty right-hand side, denoted `$` or `ε`.

**Error Recovery**: Techniques for continuing parsing after syntax errors.

## F

**FIRST Set**: The set of terminals that can begin strings derived from a grammar symbol.

**Fixed-Point Arithmetic**: Arithmetic using integers to represent fractional values (e.g., Q16.16 format).

**FOLLOW Set**: The set of terminals that can appear immediately after a non-terminal in some derivation.

**FRISC**: Faculty RISC, a simplified RISC architecture used as the compiler's target.

## G

**GOTO**: In LR parsing, a transition function from state and non-terminal to new state.

**Grammar**: A formal specification of language syntax using production rules.

## I

**Intermediate Representation (IR)**: A program representation between source code and target code (e.g., AST).

**Item**: In LR parsing, a production with a dot indicating parsing progress.

**Item Set**: A set of LR items representing a parser state.

## L

**Lexical Analysis**: The compiler phase that transforms source code into tokens.

**Lexer**: A program that performs lexical analysis (also called tokenizer or scanner).

**L-value**: An expression that can appear on the left side of an assignment (a location).

**Lookahead**: In parsing, examining upcoming tokens to resolve parsing decisions.

**LR Parsing**: A bottom-up parsing technique (Left-to-right, Rightmost derivation).

## M

**Maximal Munch**: A lexical analysis strategy that always selects the longest matching token.

**Macro**: In lexer definitions, a reusable pattern that can be referenced in rules.

## N

**Nondeterministic Finite Automaton (NFA)**: A finite automaton allowing multiple transitions per state-symbol pair.

**Non-terminal**: A grammar symbol that can be rewritten using production rules.

## P

**Parse Tree**: A tree representation of how a string is derived from a grammar.

**Parser**: A program that performs syntax analysis, constructing parse trees.

**Production**: A grammar rule specifying how a non-terminal can be rewritten.

## R

**R-value**: An expression that can appear on the right side of an assignment (a value).

**Regular Expression**: A notation for describing regular languages.

**Regular Language**: A language recognized by a finite automaton.

**Reduce**: In LR parsing, replacing a handle on the stack with a non-terminal.

## S

**Semantic Analysis**: The compiler phase that validates language-specific constraints (type checking, scope resolution).

**Shift**: In LR parsing, pushing a token onto the stack and advancing input.

**Symbol Table**: A data structure mapping identifiers to symbol information (type, scope, etc.).

**Syntax Analysis**: The compiler phase that constructs parse trees from token streams (also called parsing).

**Synthesized Attribute**: An attribute that flows from children to parent in a parse tree.

## T

**Terminal**: A grammar symbol that cannot be rewritten (a token).

**Token**: A lexical unit (keyword, identifier, operator, etc.) produced by lexical analysis.

**Translation Unit**: A complete source file ready for compilation.

**Type Checking**: Validating that operations are performed on compatible types.

## V

**Visitor Pattern**: A design pattern for traversing tree structures without modifying the tree.

---

*This glossary defines key terms used throughout the compiler documentation. For detailed explanations, see the relevant chapters.*
