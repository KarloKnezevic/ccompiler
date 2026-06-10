# Glossary

Terms specific to the FRISCcc compiler pipeline, its IR, and the FRISC target architecture, alphabetized for quick lookup.

---

**ABI (Application Binary Interface)**
The set of conventions governing register usage, calling sequences, stack frame layout, and data alignment. FRISCcc ABI: R0 = primary expression scratch (caller-saved); R1–R4 = additional scratch / argument passing (caller-saved); R5 = frame pointer FP (callee-saved); R6 = function return-value register; R7 = stack pointer SP (grows downward, initialized to `40000`). Entry sequence: `MOVE 40000, R7; CALL F_MAIN; HALT`.

**Abstract Syntax Tree (AST)**
A tree produced by the parser in which each node represents a syntactic construct (expression, statement, declaration) and syntactic sugar (parentheses, semicolons) is elided. The AST is consumed by semantic analysis, which decorates and transforms it into a typed representation before IR lowering.

**ACTION Table**
The part of an LR(1) parse table that maps (parser-state, terminal) pairs to shift, reduce, accept, or error actions. Built by `LRTableBuilder` from the canonical LR(1) item-set collection and serialized to disk by `LRTableCache`.

**Activation Record** — see *Frame*.

**Addressing Mode**
A method for specifying an operand location in a FRISC instruction. Supported modes: register (`Rx`), immediate (20-bit sign-extended), absolute, register-indirect with displacement (`Rx±imm`), and PC-relative.

**`addr_field`**
IR RHS instruction that computes the address of a struct field: `addr_field <baseAddr>, <structType>.<fieldName>`. Lowers to an add of the field's compile-time byte offset.

**`addr_index`**
IR RHS instruction that computes the address of an array element: `addr_index <baseAddr>, <index>, <elemSizeBytes>`. A bounds-checked variant is emitted when bounds checking is enabled.

**`addr_of_symbol`**
IR RHS instruction that produces the address of a named slot or global: `addr_of_symbol local:<name>`, `addr_of_symbol param:<name>`, or `addr_of_symbol global:<name>`. Lowers to a frame-pointer-relative or absolute address in FRISC.

**Artifact**
A persisted output file produced by a pipeline stage: `tokens.txt` (LEX), `ast.txt` (PARSE), `intermediate.ir` (IR), `optimized.ir` (OPT), `a.out` (FRISC). Artifacts enable inter-phase inspection and reproducibility. See [`docs/reference/cli.md`](cli.md).

**Basic Block**
A maximal sequence of IR instructions with exactly one entry point (at the block label) and exactly one exit point (the terminator). In the IR grammar, a block is `Label ":" { Instr } Terminator`. Modeled by `IrBlock`.

**BNF / EBNF**
Metalanguage for context-free grammars. FRISCcc uses BNF for the language grammar (stored in `config/grammar_definition.txt`) and a similar notation in `config/ir_definition.txt` for the IR grammar. EBNF adds `{ }` for repetition and `[ ]` for optionality.

**Bounds Checking**
Runtime verification that an array index satisfies `0 <= index < arraySize`. When the check fails, FRISC execution branches to `L_BOUNDS_ERROR`, which terminates the program with exit code −6.

**Bytecode (FRISCcc)**
The instruction stream produced by `IrToBytecodeCompiler` and executed by `BytecodeVm`. Each instruction is one opcode byte (`Opcode.ordinal()`) followed by zero or more 4-byte little-endian integer operands. The format is defined entirely in `Opcode.java` so the lowerer, VM dispatcher, and disassembler cannot drift apart.

**`BytecodeVm`**
Stack-machine VM in `cli/.../vm/BytecodeVm.java`. Maintains an explicit operand stack, an explicit call stack (frames with a per-frame register file for IR temporaries), and a byte-addressable memory array. Dispatch is a `switch` on `Opcode`. Semantically equivalent to `IrInterpreter`; validated against it on all 437 example IR files.

**Callee-Saved Register**
A register whose value the called function must preserve. In the FRISCcc ABI, only R5 (FP) is callee-saved; the function prologue pushes it and the epilogue pops it.

**Caller-Saved Register**
A register the calling function cannot assume survives a call. In FRISCcc, R0–R4 are caller-saved.

**CFG (Control-Flow Graph)**
A directed graph whose nodes are basic blocks and whose edges represent possible control transfers. The primary structure for intraprocedural analysis and optimization. Not to be confused with *context-free grammar*.

**Chunk**
A per-function unit of bytecode in `Bytecode.java`: a byte array of encoded instructions plus a symbol table mapping slot/global names to indices. `IrToBytecodeCompiler` emits one chunk per IR function.

**`CommonSubexpressionEliminationPass` (CSE)**
Optimization pass that replaces a recomputed side-effect-free RHS expression with a reference to the temporary that already holds the result, within the same basic block.

**Condition Flags**
Status-register bits set by FRISC ALU operations: Z (zero), N (negative), C (carry/borrow), V (signed overflow). Conditional branch instructions (`JP_EQ`, `JP_NE`, `JP_SLT`, etc.) test these flags.

**`ControlFlowSimplificationPass`**
Optimization pass that removes constant-condition branches (replacing `br #1:bool, L0, L1` with `jmp L0`) and eliminates trivial jump-only blocks.

**`CopyPropagationPass`**
Replaces uses of a temporary `tN` that is a copy of another value `tM` with `tM` directly, enabling downstream dead-temp elimination.

**`CastSimplificationPass`**
Eliminates no-op casts (e.g., `int32 → int32`) and folds constant casts at compile time.

**Context-Free Grammar (CFG)**
A formal grammar in which every production rule has a single nonterminal on the left-hand side. FRISCcc's source language grammar is specified as a context-free grammar in `config/grammar_definition.txt`.

**`DeadSlotStoreEliminationPass`**
Removes stores to slot addresses whose written value is never subsequently loaded before the slot is overwritten or the function returns.

**`DeadTempEliminationPass`**
Removes IR assignment instructions whose result temporary is never used by any downstream instruction or terminator.

**DFA (Deterministic Finite Automaton)**
A finite automaton with exactly one transition per (state, input-symbol) pair. The lexer is implemented as a DFA (`DFA.java`) constructed from the lexer specification via Thompson's construction followed by subset construction (`NFAToDFAConverter`). DFAs guarantee O(n) scanning.

**Dispatch Loop**
The inner loop of `BytecodeVm` that reads the next opcode byte and switches on it to execute the corresponding operation. Each iteration is one VM step.

**Epilogue**
The instruction sequence at the end of every FRISC function: deallocate the frame (`ADD R7, frameSize, R7`), restore the caller's FP (`POP R5`), and return (`RET`). Emitted by `FunctionEmitter` at the exit label.

**ε-Closure**
The set of NFA states reachable from a given state (or set of states) by following epsilon (empty-string) transitions only. Used during subset construction in `NFAToDFAConverter`.

**F_DIV**
FRISC helper routine that implements signed 32-bit integer division using a restoring-division algorithm (32-bit shift-subtract loop). Called via `CALL F_DIV`; operands passed on the stack; result returned in R6.

**F_F2I**
FRISC helper routine that converts a Q16.16 fixed-point value to a 32-bit integer by arithmetic right-shifting 16 bits.

**F_FDIV**
FRISC helper routine for Q16.16 fixed-point division: shifts the dividend left by 16 bits before calling `F_DIV`, then applies scaling correction.

**F_FMUL**
FRISC helper routine for Q16.16 fixed-point multiplication: computes the 64-bit product of the two 32-bit operands and right-shifts the result by 16 bits.

**F_I2F**
FRISC helper routine that converts a 32-bit integer to Q16.16 fixed-point by left-shifting 16 bits.

**F_MOD**
FRISC helper routine that implements signed 32-bit modulo using the restoring-division algorithm, returning the remainder.

**F_MUL**
FRISC helper routine that implements signed 32-bit integer multiplication using a shift-and-add algorithm (iterate over bits of multiplier, accumulate shifted multiplicand). Called via `CALL F_MUL`.

**FIRST Set**
For a grammar symbol X, FIRST(X) is the set of terminals that can begin any string derivable from X. Computed by `FirstSetComputer`; used in building the LR(1) ACTION/GOTO tables.

**Fixpoint**
The termination condition of the `PassPipeline`: the pipeline runs all 16 passes in order repeatedly until a full sweep produces no change (`PassResult.changed() == false`). Convergence is typically reached in 2–3 sweeps.

**Fixed-Point Arithmetic** — see *Q16.16*.

**FOLLOW Set**
For a nonterminal A, FOLLOW(A) is the set of terminals that can appear immediately after A in some sentential form. Used alongside FIRST sets in LR table construction.

**Frame (Stack Frame, Activation Record)**
The region of the call stack allocated for one function invocation. Contains the saved FP, local variable storage, spill slots for temporaries, and argument scratch area. The IR `.frame locals = N bytes align = M` directive records the local area size; `.slots` lists every named entry. Accessed via R5 (FP).

**Frame Pointer (FP)**
Register R5. Points to the base of the current stack frame (the saved caller FP is at `(R7)` before `MOVE R7, R5`). All local and param accesses use R5-relative addressing (`R5`, `R5+offset`, `R5-offset`).

**`FriscCodeGenerator`**
Top-level FRISC back-end class (`compiler-codegen-frisc`) that orchestrates `FunctionEmitter`, `GlobalsEmitter`, `ProgramEmitter`, and `HelperEmitter` to produce a complete FRISC assembly listing from an `IrProgram`.

**`FriscEmitter`**
Low-level assembly text emitter in `compiler-codegen-frisc/emitter/FriscEmitter.java`. Accumulates instruction and label lines, tracks which helper routines are needed (`needsMul`, `needsFmul`, etc.), and applies `FriscPeepholeOptimizer` before writing output.

**FRISCjs**
The JavaScript-based FRISC simulator at `node_modules/friscjs/`. Assembles and executes FRISC assembly in a Node.js process. Managed by `FriscRunner`, which spawns a Node.js subprocess, feeds it the assembled program, runs a synchronous `performCycle()` loop up to `DEFAULT_STEP_LIMIT` (200,000,000), and extracts the final register state.

**`GlobalValuePropagationPass` (GVP)**
Inter-block constant propagation: tracks constants stored to slots and propagates them across control flow where the value is unambiguously known on all predecessors.

**GOTO Table**
The part of an LR(1) parse table that maps (parser-state, nonterminal) pairs to successor states after a reduce action. Built alongside the ACTION table by `LRTableBuilder`.

**Helper Routine**
A FRISC subroutine emitted by `HelperEmitter` that implements an operation absent from the native instruction set. Routines: `F_MUL` (int32 multiply), `F_DIV` (int32 divide), `F_MOD` (int32 modulo), `F_FMUL` (Q16.16 multiply), `F_FDIV` (Q16.16 divide), `F_I2F` (int32 → Q16.16), `F_F2I` (Q16.16 → int32). Only referenced helpers are emitted (conditional helper emission via `FriscEmitter` usage flags). `L_BOUNDS_ERROR` is a trap target, not a called subroutine.

**`InductionStrengthReductionPass`**
Loop optimization that detects simple induction variables (temporaries incremented/decremented by a loop-invariant step) and replaces multiplications of the induction variable by a constant with an accumulator that adds the scaled step each iteration.

**`Int32ArithmeticPass`**
Constant-strength-reduction pass that replaces `mul t, #2` with `add t, t`, division or modulo by a power-of-two constant with shifts, and similar integer arithmetic rewrites.

**`Int32ShiftPass`**
Replaces explicit shift operations on constants with their computed results (constant shift folding) and folds shifts-of-zero.

**Intermediate Representation (IR)**
A typed, explicit three-address code representation between the front end and the back end. Defined in `config/ir_definition.txt`. Key structural elements: `.program` / `.globals` / `.type` struct definitions / `.func` functions, each with `.frame`, `.slots`, and `.blocks` sections. Modeled by `IrProgram`, `IrFunction`, `IrBlock`, `IrInstruction`, `IrRhs`, `IrTerminator`. See [`docs/reference/ir-grammar.md`](ir-grammar.md).

**`IrInterpreter`**
Tree-walking interpreter in `cli/.../ir/IrInterpreter.java`. Directly executes IR programs by recursively evaluating `IrRhs` and walking `IrTerminator` nodes, using the host JVM's call stack. Maintains a byte-addressable virtual memory array. Default step limit: 2,000,000 IR steps. Invoked via `./run.sh run-ir <file.ir>`.

**`IrPass`**
Interface in `compiler-opt` implemented by each of the 16 optimization passes. A pass receives an `IrProgram` and a `PassContext` and returns a `PassResult` (the (possibly modified) program plus a boolean indicating whether it changed anything).

**`IrPipeline`**
The compiler's front-to-IR pipeline class in `compiler-ir`. Runs lexing → parsing → semantic analysis → IR lowering in sequence, producing an `IrProgram`.

**`IrToBytecodeCompiler`**
Lowerer in `cli/.../vm/` that translates an `IrProgram` into a `Bytecode` object (a map from function name to `Chunk`). Each IR instruction and terminator maps to one or more `Opcode` sequences.

**L_BOUNDS_ERROR**
FRISC label emitted by `BoundsHelper`. When a bounds-checked array index is out of range (index < 0 or index >= size), a conditional jump branches here. The handler writes −6 into R6 and executes `HALT`, terminating the program with that exit code. Not a CALLable routine; reached only via conditional branch.

**Lattice**
A partially ordered set with a least upper bound (join) and greatest lower bound (meet) for every element pair. Provides the mathematical framework for dataflow analyses such as constant propagation and value-range analysis.

**Lexeme**
The raw character sequence in the source text matched by a lexer rule. The lexeme `while` produces token type `KR_WHILE`; the lexeme `42` produces token type `BROJ` with that text.

**Lexer (Scanner)**
The compilation phase implemented in `compiler-lexer`. Reads the source character stream and produces a sequence of `Token` objects using a DFA generated at startup from `config/lexer_definition.txt`. Applies maximal-munch disambiguation and rule-order tie-breaking.

**`LoadForwardingPass`**
Replaces `load addr_of_symbol local:x` with the previously stored value of slot `x` when no intervening store to that slot can reach the load, eliminating the memory round-trip.

**`LoopInvariantCodeMotionPass` (LICM)**
Moves pure IR instructions whose operands do not change within a loop to the top of the loop's enclosing block. Conservatively avoids cross-block motion to stay within current IR verifier constraints.

**LR(1) Parser**
A bottom-up parser that reads input left-to-right, builds a rightmost derivation in reverse, and uses one token of lookahead per decision. Implemented by `LRParser` using an ACTION/GOTO table built by `LRTableBuilder` and cached by `LRTableCache`. Can handle the full FRISCcc C-subset grammar.

**LR Item**
An augmented grammar production with a bullet (•) indicating how much of the right-hand side has been seen, plus a lookahead terminal. Represented by `LRItem`. Sets of items form parser states; the canonical LR(1) collection is built by `LRClosure` and `LRGoto`.

**Lvalue**
An expression that designates an addressable memory location (variable, array element, or struct field). In IR lowering, lvalues become `addr_of_symbol` / `addr_index` / `addr_field` RHS operations followed by `store` instructions for writes or `load` operations for reads.

**Maximal Munch (Longest Match)**
The lexical disambiguation strategy implemented in `Lexer.java`: at each input position, select the longest possible token match. When two rules match at equal length, the earlier rule in the specification wins. Ensures `++` is one token, not two.

**NFA (Nondeterministic Finite Automaton)**
A finite automaton that may have multiple transitions from a state on the same symbol, or epsilon (ε) transitions. `NFA.java` is constructed by `LexerGenerator` via Thompson's construction from regular expressions in `config/lexer_definition.txt`. The NFA is then converted to a `DFA` by `NFAToDFAConverter`.

**Nonterminal Symbol**
A grammar symbol expanded by production rules; represents a syntactic category such as `expression` or `statement`. In the FRISCcc grammar file, nonterminals are prefixed with `<` / `>`.

**`Opcode`**
Enum in `cli/.../vm/Opcode.java` that defines the complete FRISCcc bytecode instruction set. Each constant declares its operand count (`operandCount()`); the encoded size is `1 + 4 * operandCount` bytes. The enum is the single source of truth for the lowerer, VM, and disassembler.

**Optimization Pass** — see *`IrPass`*.

**`PassPipeline`**
Fixpoint driver in `compiler-opt/pipeline/PassPipeline.java`. Runs the ordered list of 16 `IrPass` instances in a loop until a complete sweep produces no change (`changedInIteration == false`) or `maxIterations` is reached.

**Parse Tree (Concrete Syntax Tree)**
A tree representing the full syntactic derivation of the source program according to the grammar, including all terminals and nonterminals. Produced by `LRParser`, modeled by `ParseTree`, and simplified into the typed AST by semantic analysis.

**Peephole Optimization**
A local, pattern-based rewrite applied by `FriscPeepholeOptimizer` to the emitted FRISC text. Current rules: eliminate self-moves (`MOVE Rx, Rx`), remove identity no-ops (`ADD Rx, 0, Rx`), cancel adjacent push/pop pairs (`PUSH Rx` / `POP Rx`), and remove unconditional jumps to the immediately following label.

**Phi Function**
An SSA construct placed at control-flow merge points that selects a value based on which predecessor block was executed. Not present in the current FRISCcc IR (temporaries are spilled to slots instead); discussed as a future extension.

**Pipeline**
The ordered sequence of compilation stages: LEX → PARSE → SEMANTIC → IR → OPT → FRISC → (optionally) RUN. Orchestrated by `PipelineRunner`; the active subset is determined by `PipelinePlan`.

**`PipelineRunner`**
Orchestrator class in `cli/.../pipeline/PipelineRunner.java`. Executes each `PipelineStage` in order, measures elapsed time, writes artifact files, and reports progress via `ConsoleReporter`.

**Prologue**
The instruction sequence at the beginning of every FRISC function: save the caller's FP (`PUSH R5`), set the new FP (`MOVE R7, R5`), allocate the frame (`SUB R7, frameSize, R7`), and zero-initialize the local variable area. Emitted by `FunctionEmitter`.

**Q16.16 (Fixed-Point Format)**
A signed 32-bit integer used to represent fractional values: the high 16 bits are the integer part and the low 16 bits are the fractional part. Multiplication requires a post-multiply right-shift by 16; division requires a pre-divide left-shift. Avoids hardware floating-point. Arithmetic uses `F_FMUL`, `F_FDIV`, `F_I2F`, `F_F2I`. The IR type is `float`; the VM uses `MUL_Q16` / `DIV_Q16` opcodes.

**R0**
FRISC general-purpose register. Primary expression result register in the FRISCcc ABI; caller-saved. Used as the main scratch register during code generation.

**R1–R4**
FRISC general-purpose registers. Caller-saved scratch registers. Used for secondary scratch, helper-routine argument passing, and temporary computation in the code generator.

**R5**
FRISC general-purpose register. Serves as the **frame pointer (FP)** in the FRISCcc ABI. Callee-saved; pushed in the prologue and popped in the epilogue.

**R6**
FRISC general-purpose register. Serves as the **return value register** in the FRISCcc ABI. A returning function stores its result in R6; the caller reads R6 after `CALL`.

**R7**
FRISC general-purpose register. Serves as the **stack pointer (SP)** in the FRISCcc ABI. Initialized to `40000`; grows downward (decremented on push, incremented on pop).

**Register Allocation**
The process of mapping IR temporaries to physical registers. FRISCcc does not perform register allocation: every temporary is spilled to a dedicated stack slot in the frame (the *temp area*), and loaded/stored around each use. The `TempUsageAnalyzer` determines the number of required temp slots.

**Rhs (Right-Hand Side)**
The value-producing part of an IR assignment instruction (`tN = <rhs>`). Represented by the sealed interface `IrRhs`. Variants: `AddrOfSymbol`, `AddrIndex`, `AddrField`, `Load`, `BinOp`, `CmpOp`, `Call`, `UnaryOp`, `IncDecOp`, `CastOp`, and literal `IrConst`.

**Semantic Analysis**
The compiler phase in `compiler-semantics` that checks program well-formedness beyond syntax: scope resolution, type checking, implicit conversion insertion, function-signature validation, and constraint checks (e.g., `break`/`continue` inside loops, `return` type compatibility).

**Slot**
A named, typed, byte-offset entry in the IR `.slots` table. Three kinds: `param` (function parameter), `local` (declared variable), `spill` (compiler-generated temporary storage). Modeled by `IrSlot`. The code generator maps each slot to an FP-relative memory address.

**Spill**
Storing a value from a register to a stack slot when register pressure would otherwise exceed available registers. In FRISCcc, every IR temporary is unconditionally spilled because no register allocator is implemented. The temp area in the frame holds one 4-byte word per temporary index.

**SSA (Static Single Assignment)**
An IR property in which every variable is assigned at most once. FRISCcc temporaries (`tN`) satisfy SSA: each is defined by exactly one assignment instruction. Memory slots (locals, params) are not in SSA form.

**Stack Pointer (SP)**
Register R7. Points to the current top of the call stack. The stack grows downward; `PUSH Rx` decrements R7 by 4 and stores Rx; `POP Rx` loads from `(R7)` and increments R7 by 4.

**Step Limit**
The maximum number of instructions a back end will execute before aborting with a timeout error. FRISC simulator (`FriscRunner`): 200,000,000 cycles. IR interpreter (`IrInterpreter`): 2,000,000 IR steps (default). Bytecode VM (`BytecodeVm`): configurable via `VmExecutionOptions`.

**`StrengthReductionPass`** — see *`Int32ArithmeticPass`* and *`InductionStrengthReductionPass`*.

**Subset Construction (Powerset Construction)**
The algorithm in `NFAToDFAConverter` that converts an ε-NFA to an equivalent DFA. Each DFA state is a set of NFA states reachable by the same input sequence; the start state is the ε-closure of the NFA start state.

**Symbol Table**
A data structure mapping identifiers to their attributes (type, scope, storage class, slot offset). Maintained during semantic analysis using a scope stack; inner scopes shadow outer ones. Consumed by IR lowering to resolve variable references to `addr_of_symbol` instructions.

**Temporary (`tN`)**
A compiler-generated, SSA-style value in the IR, written `t0`, `t1`, `t2`, …. Each temporary is defined by exactly one `AssignInstr` and may be used any number of times. Modeled by `IrTemp`. At FRISC code generation, each temporary is assigned a dedicated 4-byte spill slot in the frame's temp area.

**Terminal Symbol**
A grammar symbol that appears literally in the token stream and cannot be expanded further. Corresponds directly to a token type emitted by the lexer.

**Terminator**
The final instruction of a basic block that determines control flow. FRISCcc IR terminators (sealed interface `IrTerminator`): `IrBrTerm` (`br cond, trueLabel, falseLabel`), `IrJmpTerm` (`jmp label`), and `IrRetTerm` (`ret` or `ret value`). Every block must end with exactly one terminator.

**Thompson's Construction**
The algorithm used by `LexerGenerator` to convert a regular expression into an ε-NFA. Each operator (concatenation, alternation `|`, Kleene star `*`) corresponds to a specific NFA fragment with epsilon-transition wiring.

**Three-Address Code**
An IR format in which each instruction has at most one destination and two source operands, as in `t2 = add t0, t1 : int32`. The FRISCcc IR is a typed three-address code.

**`TinyFunctionInliningPass`**
Inlines small pure leaf functions (at most `MAX_INLINE_ASSIGNMENTS = 8` assignment instructions, no calls, `int32` return type) at their call sites, eliminating call overhead.

**Token**
A classified lexical unit consisting of a type (`KR_INT`, `IDN`, `PLUS`, etc.) and the matched lexeme string. Output of the lexer; input to the parser. Modeled by `Token.java` in `compiler-lexer`.

**Translation Unit**
The top-level syntactic unit of a FRISCcc C-subset program: a sequence of external declarations (function definitions and global variable declarations). The IR equivalent is `.program … .endprogram`.

**Type System**
The rules governing how types are assigned to expressions and which operations are legal on which types. FRISCcc supports `int` (32-bit), `char` (8-bit), `float` (Q16.16 32-bit), `void`, pointers (`ptr<T>`), arrays (`array<T,N>`), and structs. Implicit arithmetic conversions are inserted by the semantic analyzer.

**`TypedConstantFoldingPass` (TCF)**
Evaluates constant expressions entirely at compile time and replaces them with a typed literal constant. Handles all IR `BinOp` and `UnaryOp` variants; type-aware (Q16.16 folding differs from int32 folding).

**`UnreachableBlockEliminationPass`**
Removes basic blocks that have no path from the function entry block. Runs a forward reachability DFS and drops any block not in the reachable set.

**`ValueRangeSimplificationPass`**
Uses conservative int32 range analysis to simplify comparisons whose result is statically determinable (e.g., `cmp_lt #0:int32, #5:int32 : bool` → `#1:bool`) and to eliminate branches that are always taken or never taken.

**Von Neumann Architecture**
A computer architecture in which instructions and data share a single memory space. FRISC follows this model; the FRISCjs simulator represents the full address space as a single flat byte array.

**Zero-Initialization**
The practice, enforced in the FRISC function prologue, of writing zero to every word of the local variable area before execution of the function body begins. Implemented as a word-loop (`R1 = wordCount; do { STORE R2, (R0); ADD R0, 4, R0; SUB R1, 1, R1; } while (R1 != 0);`). Ensures deterministic behavior for uninitialized variables at the cost of additional executed instructions proportional to frame size.
