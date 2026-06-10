# Appendix A. Glossary

> **📖 From the book.** This chapter accompanies *Building a C-Subset Compiler for the FRISC Architecture: From Formal Languages to Executable Code* by Dr. Karlo Knežević (Zenodo, 2026). For the complete treatment — formal development, proofs, and figures — read the book: [📄 PDF](../book/Building-a-C-Subset-Compiler-for-the-FRISC-Architecture.pdf) · DOI [10.5281/zenodo.20511073](https://doi.org/10.5281/zenodo.20511073) · ISBN 978-953-47198-0-0.


This glossary defines the principal terms used throughout this book. Entries are
organized alphabetically. Where a term has both a general meaning and a
project-specific meaning, the project-specific usage is noted explicitly.

---

**ABI (Application Binary Interface)**
The set of conventions governing register usage, calling sequences, stack frame
layout, and data alignment that allows separately compiled functions to
interoperate. In this project the ABI is defined by the FRISC code generator:
R7 serves as the stack pointer, R5 as the frame pointer, and R6 as the return
value register. (Chapter 9)

**Abstract Syntax Tree (AST)**
A tree representation of source program structure in which each node corresponds
to a syntactic construct (expression, statement, declaration) and syntactic
sugar such as parentheses and semicolons is elided. The AST is produced by the
parser and consumed by semantic analysis. (Chapter 4)

**Activation Record**
See *Frame*.

**Addressing Mode**
A method by which an instruction specifies the location of an operand. FRISC
supports register, immediate (20-bit sign-extended), absolute, register-indirect
with displacement, and PC-relative addressing modes. (Appendix H)

**Artifact**
A persisted output file produced by a compiler phase, such as `tokens.txt`,
`ast.txt`, or `intermediate.ir`. Artifacts enable reproducibility and
inter-phase diagnostics. (Chapter 10)

**Basic Block**
A maximal sequence of instructions with exactly one entry point (at the
beginning) and one exit point (at the end). Control flow enters at the top and
leaves at the bottom without branching or branch targets in between. (Chapter 6)

**BNF (Backus--Naur Form)**
A metalanguage for expressing context-free grammars. Each production rule has
the form `<nonterminal> ::= sequence of symbols`. Extended BNF (EBNF) adds
repetition (`{ }`) and optionality (`[ ]`) notation. (Chapter 2)

**Bounds Checking**
Runtime verification that an array index lies within the declared bounds of the
array. In this project, bounds checking is optionally emitted by the code
generator and, upon failure, terminates execution with error code -6 via the
`L_BOUNDS_ERROR` handler. (Chapter 9)

**Callee-Saved Register**
A register whose value must be preserved across function calls by the called
function. In the FRISCcc ABI, R5 (the frame pointer) is callee-saved; the
callee pushes it in the prologue and restores it in the epilogue. (Chapter 9)

**Caller-Saved Register**
A register whose value the calling function must assume will be destroyed by a
function call. In the FRISCcc ABI, R0 through R4 are caller-saved (scratch
registers). (Chapter 9)

**Canonical Form**
A normalized representation chosen so that structurally equivalent programs map
to identical outputs, facilitating comparison and testing. (Chapter 11)

**CFG (Control-Flow Graph)**
A directed graph whose nodes are basic blocks and whose edges represent possible
transfers of control. The CFG is the primary data structure for intraprocedural
analysis and optimization. (Chapter 6)

**Condition Flags**
Bits in the FRISC status register (SR) set by ALU operations: Z (zero), N
(negative), C (carry/borrow), and V (signed overflow). Conditional branch
instructions test these flags. (Appendix H)

**Conditional Helper Emission**
The mechanism by which the code generator emits only those helper routines
(F_MUL, F_DIV, F_FMUL, etc.) that are actually referenced by the generated
code. The `HelperLibrary` class queries usage flags on the `FriscEmitter` to
determine which helpers to include. (Chapter 9)

**Constant Folding**
An optimization that evaluates constant expressions at compile time rather than
generating code to compute them at runtime. For example, `3 + 4` is replaced by
`7` during IR construction or optimization. (Chapter 7)

**Constant Propagation**
An optimization that replaces uses of a variable known to hold a constant value
with the constant itself, potentially enabling further constant folding.
(Chapter 7)

**Context-Free Grammar (CFG)**
A formal grammar in which every production rule has a single nonterminal on its
left-hand side. Context-free grammars are the standard formalism for specifying
programming language syntax. Not to be confused with control-flow graph.
(Chapter 2)

**Dead Code Elimination (DCE)**
An optimization that removes instructions whose results are never used by any
subsequent computation or program output. (Chapter 7)

**DFA (Deterministic Finite Automaton)**
A finite automaton with exactly one transition per (state, input symbol) pair.
DFAs are used as the execution model for lexical analyzers because they
guarantee O(n) scanning in the length of the input. (Chapter 2)

**EBNF (Extended Backus--Naur Form)**
See *BNF*.

**Epilogue**
The instruction sequence at the end of a function that restores the caller's
frame pointer, deallocates the stack frame, and returns to the caller. In
FRISC, the epilogue is: `MOVE R5, R7` / `POP R5` / `RET`. (Chapter 8)

**Epsilon (empty string)**
In the lexer specification, the symbol `$` denotes epsilon. In formal language
theory, epsilon represents the empty string, a string of length zero.
(Chapter 2)

**FIRST Set**
For a grammar symbol X, FIRST(X) is the set of terminal symbols that can appear
as the first symbol of any string derivable from X. FIRST sets are used in
constructing LL and LR parsing tables. (Chapter 4)

**Fixed-Point Arithmetic**
Arithmetic on numbers represented with a fixed number of fractional bits. This
project uses Q16.16 format: 16 integer bits and 16 fractional bits stored in a
signed 32-bit integer. Fixed-point avoids the need for hardware floating-point
support on FRISC. (Chapter 9)

**FOLLOW Set**
For a nonterminal A, FOLLOW(A) is the set of terminal symbols that can appear
immediately after A in some sentential form. FOLLOW sets are used alongside
FIRST sets in parser table construction. (Chapter 4)

**Frame (Stack Frame, Activation Record)**
A region of the stack allocated for a single function invocation. It contains
parameters, saved registers, local variables, and spill slots. The IR `.frame`
and `.slots` directives describe the frame layout explicitly. (Chapters 6, 8)

**Frame Pointer (FP)**
A register that points to a fixed location within the current stack frame,
providing a stable base for accessing parameters and locals. In this project,
R5 serves as the frame pointer. (Chapter 9)

**FRISCjs**
The JavaScript-based FRISC simulator used as the execution backend for compiled
programs. FRISCjs assembles and executes FRISC assembly in a Node.js process,
controlled by the Java-side `FriscRunner` class. (Chapter 10)

**FriscRunner**
The Java class (`hr.fer.ppj.cli.FriscRunner`) that manages the lifecycle of a
FRISC simulation: it spawns a Node.js process with an inline step-runner script,
feeds it the assembled program, executes a synchronous `performCycle()` loop,
and extracts the final register state. (Chapter 10)

**Helper Routine**
A backend-emitted FRISC subroutine that implements an operation absent from the
native instruction set, such as integer multiplication (`F_MUL`), division
(`F_DIV`), modulo (`F_MOD`), or Q16.16 fixed-point arithmetic (`F_FMUL`,
`F_FDIV`, `F_I2F`, `F_F2I`). (Chapter 9)

**Implicit Conversion**
A type conversion inserted automatically by the semantic analyzer when an
expression of one type appears in a context requiring a different type. For
example, assigning a `char` value to an `int` variable triggers an implicit
widening conversion. (Chapter 5)

**Instruction Count**
The total number of FRISC instructions executed during a simulation run. Because
the FRISC simulator executes one instruction per cycle with no pipeline effects,
instruction count serves as the primary performance metric for generated code.
(Chapter 12)

**Intermediate Representation (IR)**
A typed, explicit representation of a program that sits between the frontend
(parsing and semantic analysis) and the backend (code generation). The IR in
this project uses a three-address code style with explicit types, temporaries,
basic blocks, and a slot table. (Chapter 6)

**IR Interpreter**
The Java class (`hr.fer.ppj.cli.ir.IrInterpreter`) that directly executes IR
programs using a byte-addressable virtual memory model and native Java
arithmetic. The IR interpreter serves as a reference oracle for validating the
correctness of FRISC code generation. (Chapter 10)

**Lattice**
A partially ordered set in which every pair of elements has a least upper bound
(join) and a greatest lower bound (meet). Lattices provide the mathematical
foundation for dataflow analysis frameworks. (Chapter 2)

**Lexeme**
The actual character sequence in the source text matched by a lexer rule. For
example, the lexeme `while` produces a `KR_WHILE` token. (Chapter 3)

**Lexer (Scanner, Tokenizer)**
The compiler phase that reads the source character stream and produces a
sequence of tokens. The lexer in this project is generated from a DFA
constructed from the specification in `config/lexer_definition.txt`. (Chapter 3)

**Linear Scan Allocation**
A register allocation algorithm that assigns physical registers by scanning
temporaries in order of their live ranges. When all registers are occupied, the
temporary with the longest remaining live range is spilled. Linear scan has
O(n log n) complexity and is a practical alternative to graph coloring for
architectures with few registers. (Chapter 13)

**Little-Endian**
A byte ordering convention in which the least significant byte of a multi-byte
value is stored at the lowest memory address. FRISC uses little-endian format.
(Appendix H)

**Live Range**
The interval from the first definition of a temporary to its last use. Two
temporaries whose live ranges overlap cannot share the same physical register
(they interfere). (Chapters 7, 12)

**Loop-Invariant Code Motion (LICM)**
An optimization that moves computations whose operands do not change within a
loop to the loop's preheader, so they execute once rather than on every
iteration. (Chapter 7)

**LR(1) Parser**
A bottom-up parser that reads input left-to-right, produces a rightmost
derivation in reverse, and uses one token of lookahead to make parsing
decisions. LR(1) parsers can handle a larger class of grammars than LL parsers.
(Chapter 4)

**Lvalue**
An expression that designates a memory location and can appear on the left-hand
side of an assignment. In the IR, lvalues are lowered to address computations
followed by store instructions. (Chapter 6)

**Maximal Munch (Longest Match)**
A lexical disambiguation rule that selects the longest possible matching token
at the current input position. This ensures, for example, that `++` is
recognized as a single increment operator rather than two plus signs. (Chapter 3)

**NFA (Nondeterministic Finite Automaton)**
A finite automaton that may have multiple transitions for a given (state, input
symbol) pair, or epsilon transitions that consume no input. NFAs are typically
constructed first (via Thompson's construction) and then converted to DFAs.
(Chapter 2)

**Nonterminal Symbol**
A grammar symbol that can be expanded by production rules. Nonterminals
represent syntactic categories such as "expression" or "statement." (Chapter 2)

**Optimization Pass**
A transformation applied to the IR that preserves program semantics while
improving some quality metric (code size, execution speed, or register
pressure). Passes in this project include constant folding, dead code
elimination, peephole rewriting, and strength reduction. (Chapter 7)

**Parse Tree (Concrete Syntax Tree)**
A tree that represents the full syntactic derivation of a source program
according to the grammar, including all terminals and nonterminals. The parse
tree is typically simplified into an AST before further processing. (Chapter 4)

**Peephole Optimization**
A local optimization technique that examines a small sliding window of
instructions and applies pattern-based rewrites without global analysis. Examples
include replacing `add t, 0` with a copy and eliminating redundant loads.
(Chapter 7)

**Phi Function**
An SSA-form construct placed at control-flow merge points that selects the
correct value based on which predecessor block was executed. Phi functions have
zero runtime cost; they are resolved during SSA deconstruction by inserting
copies on predecessor edges. Not currently used in this project's IR but
discussed as a future direction. (Chapter 13)

**Pipeline (Compilation Pipeline)**
The sequence of compilation stages through which a source program passes:
LEX, PARSE, SEMANTIC, IR, OPT, FRISC, RUN. Each stage produces artifacts
consumed by subsequent stages. (Chapter 2)

**PipelineRunner**
The Java class (`hr.fer.ppj.cli.pipeline.PipelineRunner`) that orchestrates the
execution of compilation stages, dispatching to the appropriate phase
implementation and measuring elapsed time for each stage. (Chapter 10)

**Production Rule**
A rule in a context-free grammar that specifies how a nonterminal can be
expanded into a sequence of terminals and nonterminals. (Chapter 2)

**Prologue**
The instruction sequence at the beginning of a function that saves the caller's
frame pointer, establishes the new frame, and allocates space for local
variables. In FRISC, the prologue is: `PUSH R5` / `MOVE R7, R5` /
`SUB R7, frameSize, R7`. (Chapter 8)

**Q16.16**
A signed fixed-point number format using 16 integer bits and 16 fractional bits,
stored in a 32-bit integer. Multiplication and division in Q16.16 require
post-operation shifts to maintain correct scaling. See *Fixed-Point Arithmetic*.
(Chapter 9)

**Register Allocation**
The process of mapping IR temporaries to physical machine registers. When the
number of live temporaries exceeds the number of available registers, some
values must be spilled to memory. The current compiler does not perform register
allocation; all temporaries are spilled to stack slots. (Chapters 12, 13)

**Restoring Division**
The division algorithm used by the `F_DIV` helper routine. It processes the
dividend bit by bit (32 iterations), subtracting the divisor from an accumulator
and "restoring" the accumulator (adding the divisor back) when the subtraction
produces a negative result. (Chapter 9)

**RISC (Reduced Instruction Set Computer)**
A processor design philosophy emphasizing a small, uniform instruction set with
fixed-width encoding, a load-store memory model, and a large register file.
FRISC is a RISC architecture. (Appendix H)

**Semantic Analysis**
The compiler phase that checks the well-formedness of a program beyond what
syntax alone can express: type checking, scope resolution, implicit conversion
insertion, and constraint validation. (Chapter 5)

**Shift-and-Add Multiplication**
The multiplication algorithm used by the `F_MUL` helper routine. It iterates
through each bit of the multiplier; for each set bit, it adds a shifted copy of
the multiplicand to the accumulator. Sign handling is performed separately.
(Chapter 9)

**Slot**
A named storage entry in the IR's `.slots` table, representing a parameter,
local variable, or spill location. Each slot has an explicit byte offset within
the stack frame and a declared type. (Chapter 6)

**Spill**
The act of storing a register value to a stack slot when register pressure
exceeds the number of physical registers, and reloading it later when needed.
In the current compiler, every temporary is spilled because no register
allocation is performed. (Chapters 8, 12)

**SSA (Static Single Assignment)**
An IR property in which every variable is assigned exactly once. SSA form
simplifies many optimizations by making def-use chains explicit. This project's
IR uses SSA-style temporaries (each `tN` is defined once), but variables in
memory are not in SSA form. (Chapters 6, 13)

**Stack Pointer (SP)**
A register that points to the top of the runtime stack. In FRISC, R7 serves as
the stack pointer by architectural convention. The stack grows downward (toward
lower addresses). (Chapter 9)

**Step Limit**
The maximum number of instructions (FRISC) or IR steps the simulator or
interpreter will execute before terminating with a timeout. The FRISC simulator
defaults to 200,000,000 steps; the IR interpreter defaults to 2,000,000 steps.
(Chapter 10)

**Strength Reduction**
An optimization that replaces expensive operations with cheaper equivalents. A
common example is replacing multiplication by a power of two with a left shift.
In this project, strength reduction targets Q16.16 multiplication and division
by constants. (Chapter 7)

**Struct**
A composite data type consisting of named fields with potentially different
types. Structs are laid out in memory in field-declaration order with each field
at a known byte offset. Struct field access in the IR uses the `addr_field`
instruction. (Chapters 5, 6)

**Subset Construction (Powerset Construction)**
An algorithm that converts an NFA into an equivalent DFA by constructing states
that are sets of NFA states. Each DFA state represents the set of all NFA states
reachable via the same input sequence. (Chapter 3)

**Symbol Table**
A data structure maintained during compilation that maps identifiers to their
attributes (type, scope, storage class, memory location). Symbol tables support
nested scopes through a stack or tree of scope records. (Chapter 5)

**Synchronization Token**
A token used for panic-mode error recovery in the parser. When a syntax error is
detected, the parser discards tokens until a synchronization token (such as `;`
or `}`) is found, then resumes parsing. (Chapter 4)

**Temporary**
A compiler-generated named value in the IR, denoted `tN` (e.g., `t0`, `t1`).
Temporaries are assigned exactly once and represent intermediate computation
results. (Chapter 6)

**Terminal Symbol**
A grammar symbol that appears literally in the input token stream and cannot be
expanded further. Terminal symbols correspond to token types emitted by the
lexer. (Chapter 2)

**Terminator**
The final instruction of a basic block, which determines control flow to
successor blocks. Terminators include unconditional jumps (`jmp`), conditional
branches (`br`), function returns (`ret`), and halt instructions. Every basic
block must end with exactly one terminator. (Chapter 6)

**Thompson's Construction**
An algorithm that converts a regular expression into an equivalent NFA. Each
regular expression operator (concatenation, alternation, Kleene star)
corresponds to a specific NFA construction pattern. (Chapter 3)

**Three-Address Code**
An IR format in which each instruction has at most three operands (typically one
destination and two sources), as in `t2 = add t0, t1 : int32`. (Chapter 6)

**Token**
A classified lexical unit consisting of a type (e.g., `KR_INT`, `IDN`, `PLUS`)
and the matched lexeme text. Tokens are the output of the lexer and the input to
the parser. (Chapter 3)

**Translation Unit**
The top-level syntactic unit of a C program: a sequence of external declarations
(function definitions and variable declarations). Corresponds to the
`<prijevodna_jedinica>` nonterminal in the grammar. (Chapter 4)

**Translation Validation**
A verification approach that checks the correctness of each individual
compilation run, rather than proving the compiler itself correct. After the
compiler produces output, a separate validator confirms that the output is
semantically equivalent to the input. (Chapter 13)

**Triangulation**
The validation methodology used in this project in which the same program is
executed via both the IR interpreter and the FRISC simulator, and the results
are compared against each other and against the expected output. Agreement
between independent execution paths provides strong evidence of correctness.
(Chapter 11)

**Type System**
The set of rules that assigns types to program expressions and checks that
operations are applied to operands of compatible types. The type system in this
project supports `int`, `char`, `float`, `void`, pointers, arrays, and structs,
with implicit conversions between arithmetic types. (Chapter 5)

**Von Neumann Architecture**
A computer architecture in which instructions and data share a single memory
space and address bus. FRISC follows this model. (Appendix H)

**Zero-Initialization**
The practice of initializing all local variable storage to zero at the beginning
of a function. In the generated FRISC code, the prologue includes a loop that
writes zero to every byte of the stack frame. This ensures deterministic
behavior for uninitialized variables at the cost of additional instructions.
(Chapter 8, 12)
