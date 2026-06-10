# Notation and conventions

Symbols, identifiers, and typographic rules used consistently across the FRISCcc documentation and compiler output.

---

## IR temporaries

Temporaries are named `t` followed by a non-negative integer. They are numbered sequentially per function by `TempFactory` and carry an explicit type.

| Form | Example | Scope |
|------|---------|-------|
| `t0`, `t1`, … `tN` | `t0`, `t42` | Per-function; sequential from 0 |

Temporaries are **block-scoped**: a temporary defined in one basic block cannot be referenced in another. Values that must cross a block boundary are spilled to a named slot (`store`/`load`).

`IrValue` is a sealed interface permitting only `IrTemp` and `IrConst`. Symbol references (`local:x`, `param:n`, `global:buf`) appear exclusively inside `addr_of_symbol` and are not values.

---

## IR slots

Named addressable locations in a function's frame. Each slot has a `Kind`, a name, a byte offset, and a type.

| Kind | Syntax in `.slots` | Address from FP |
|------|-------------------|----------------|
| `param` | `param name@offset : Type` | `FP + 8 + offset` |
| `local` | `local name@offset : Type` | `FP - 4 - offset` |
| `spill` | `spill name@offset : Type` | `FP - 4 - offset` |

When a block-scoped C variable shadows an outer variable of the same name, the slot name is disambiguated by appending `_1`, `_2`, etc.

---

## Basic-block labels

Block labels are generated deterministically per function by `LabelFactory`.

| Form | Example | Notes |
|------|---------|-------|
| `L0`, `L1`, … `LN` | `L0`, `L3` | `L0` is always the function entry block |
| Descriptive name | `loop_body`, `exit` | Allowed by the grammar; not used in generated IR |

Labels appear on their own line followed by `:` and terminate a block with exactly one of `br`, `jmp`, or `ret`. Labels are unique within a function but may be reused across functions.

---

## IR types

| IR keyword | Enum variant | Size (bytes) | Notes |
|------------|-------------|--------------|-------|
| `int32` | `INT32` | 4 | Signed 32-bit two's complement |
| `char` | `CHAR` | 1 | Signed 8-bit |
| `uchar` | `UCHAR` | 1 | Unsigned 8-bit |
| `float` | `FLOAT` | 4 | Semantic float; Q16.16 fixed-point in the FRISC backend |
| `bool` | `BOOL` | 1 | Produced only by comparison ops; consumed only by `br` |
| `ptr<T>` | `IrPointerType` | 4 | Typed pointer |
| `array<T,N>` | `IrArrayType` | N × sizeof(T) | Fixed size; N is a compile-time constant |
| `struct Name` | `IrStructType` | Declared in `.type` block | Nominal; field layout in `.type` |
| `void` | — | 0 | Function return type only |

All type conversions are explicit (`sext`, `zext`, `trunc`, `ptrcast`, `itof`, `ftoi`). There are no implicit promotions in the IR.

---

## IR constants

| Form | Examples | Notes |
|------|----------|-------|
| `#N:Type` | `#0:int32`, `#-1:int32` | Typed integer or boolean literal |
| `#'c':char` | `#'a':char` | Character literal |
| `#f:float` | `#3.14:float` | Fixed-point float literal |
| `null:ptr<T>` | `null:ptr<int32>` | Typed null pointer |
| `{...}:array<T,N>` | `{1,2,3}:array<int32,3>` | Array literal initializer |

---

## IR instruction format

```
tN = opcode operand [, operand ...] [: ResultType]
```

Void-result instructions (`store`, `jmp`, `br`, `ret`, `call … : void`) omit the destination and type suffix.

See [../pipeline/ir.md](../pipeline/ir.md) for the full instruction reference and grammar.

---

## FRISC registers and ABI roles

| Register | Role | Saved by | Notes |
|----------|------|----------|-------|
| `R0` | Primary expression result / scratch | Caller | General-purpose accumulator; clobbered across any `CALL` |
| `R1` | Scratch / second operand | Caller | Used in binary ops; clobbered across any `CALL` |
| `R2` | Scratch | Caller | Used inside helpers (sign tracker, remainder, prod-lo) |
| `R3` | Scratch | Caller | Used inside helpers (result accumulator, quotient, prod-hi) |
| `R4` | Scratch | Caller | Used inside helpers (loop counter, bit-test, zero constant) |
| `R5` | Frame pointer (FP) | Callee | Points to saved-FP slot of current activation record |
| `R6` | Return value | Caller | Every function writes its result here before `RET` |
| `R7` | Stack pointer (SP) | Callee (implicit) | Grows downward; 4-byte aligned; initialized to `40000` (hex) |

`40000` hex = 0x40000 = 262,144 decimal. The stack starts here and grows toward lower addresses.

See [../pipeline/runtime-abi.md](../pipeline/runtime-abi.md) for the full calling convention, frame layout, and entry sequence.

---

## FRISC generated-label naming

| Pattern | Meaning | Example |
|---------|---------|---------|
| `F_name` | User function entry point | `F_MAIN`, `F_GCD` |
| `L_FUNCNAME_Ln` | Basic block within a function | `L_MAIN_L0`, `L_GCD_L3` |
| `G_name` | Global variable data region | `G_BUFFER`, `G_COUNT` |
| `F_MUL` | Integer multiply helper | — |
| `F_DIV` | Integer divide helper | — |
| `F_MOD` | Integer modulo helper | — |
| `F_FMUL` | Q16.16 fixed-point multiply helper | — |
| `F_FDIV` | Q16.16 fixed-point divide helper | — |
| `F_I2F` | Integer → Q16.16 conversion helper | — |
| `F_F2I` | Q16.16 → integer conversion helper | — |
| `L_BOUNDS_ERROR` | Array bounds-check failure trap | — |

---

## Helper-routine labels (`F_*`)

The seven `F_*` routines are **callable subroutines** (`CALL`/`RET`). They are emitted by `HelperLibrary` only when the program requires them.

| Label | Operation | C operator mapped |
|-------|-----------|-------------------|
| `F_MUL` | Signed 32-bit integer multiplication | `*` (int) |
| `F_DIV` | Signed 32-bit integer division, truncates toward zero | `/` (int) |
| `F_MOD` | Signed 32-bit integer modulo, sign follows dividend | `%` (int) |
| `F_FMUL` | Q16.16 widening fixed-point multiplication | `*` (float) |
| `F_FDIV` | Q16.16 fixed-point division (calls `F_DIV` and `F_MOD` internally) | `/` (float) |
| `F_I2F` | Convert `int32` to Q16.16 (left-shift by 16) | `(float)` cast |
| `F_F2I` | Convert Q16.16 to `int32` (logical right-shift by 16) | `(int)` cast |

**Emission dependency**: when `F_FDIV` is needed, `F_DIV` and `F_MOD` are also emitted unconditionally.

---

## Bounds-check trap (`L_BOUNDS_ERROR`)

`L_BOUNDS_ERROR` is a **jump target**, not a callable routine. The lowering phase (`AddressLowerer`) emits an inline 4-instruction guard before each array index operation:

```
CMP  R_index, #0       ; check index >= 0
JP_SLT L_BOUNDS_ERROR  ; trap if negative
CMP  R_index, R_length ; check index < length
JP_SGE L_BOUNDS_ERROR  ; trap if out of range
```

The trap handler emits a `HALT` instruction. Control never returns from `L_BOUNDS_ERROR`.

---

## FRISC assembly numeric literals

| Form | Base | Example | Decimal value |
|------|------|---------|---------------|
| Bare integer | Hexadecimal (FRISCjs default) | `40000` | 262,144 |
| `%D N` prefix | Decimal | `%D 100` | 100 |

Generated FRISC assembly uses `%D` explicitly whenever a decimal value is intended. Hexadecimal values appear without a prefix.

---

## Grammar notation (IR and BNF)

| Notation | Meaning |
|----------|---------|
| `{ X }` | Zero or more repetitions of X |
| `[ X ]` | Optional: zero or one occurrence of X |
| `X \| Y` | Alternative: X or Y |
| `"keyword"` | Literal keyword string |
| `::=` | Production rule |
| `;` | End of production rule (in `ir_definition.txt`) |
| `<name>` | Nonterminal (C grammar, BNF style) |
| `KR_NAME` | Keyword token (abbreviates Croatian *kljucna rijec*) |
| `OP_NAME` | Operator token |

---

## Java codebase conventions

| Convention | Scope | Example |
|------------|-------|---------|
| Package prefix | All modules | `hr.fer.ppj` |
| Phase packages | Per compiler phase | `hr.fer.ppj.lexer`, `hr.fer.ppj.ir`, `hr.fer.ppj.opt` |
| Class names | PascalCase | `FriscCodeGenerator`, `IrInterpreter`, `PipelineRunner` |
| Method names | camelCase | `emitPrologue()`, `resolveType()`, `performCycle()` |
| Constants | `UPPER_SNAKE_CASE` | `DEFAULT_STEP_LIMIT`, `DEFAULT_TIMEOUT` |
| Test classes | Suffix `Test` | `LexerTest`, `BytecodeVmExecutionTest` |

---

## Mermaid diagram conventions (docs)

Diagrams in the `docs/pipeline/` reference pages use GFM fenced code blocks with the `mermaid` language tag.

| Diagram type | Mermaid keyword | Typical use |
|-------------|----------------|-------------|
| Flowchart | `flowchart LR` / `flowchart TD` | Pipeline stages, control flow |
| Class diagram | `classDiagram` | Type hierarchies, sealed interfaces |
| State diagram | `stateDiagram-v2` | Automata, lexer states |
| Sequence diagram | `sequenceDiagram` | Component interactions |

Node labels use concise identifiers; explanatory text appears in surrounding prose.

---

## Pseudocode conventions

| Element | Convention |
|---------|------------|
| Procedure header | `procedure name(params):` |
| Block structure | Indentation; no braces or `begin`/`end` |
| Assignment | `=` |
| Equality test | `==` |
| Comments | `//` |
| Array access | `a[i]` |
| Set operations | `∈`, `∪`, `∩` |

---

## Error output convention

All compiler phases write failures to `compiler-bin/errors.txt`. Each entry contains: phase identifier, failure reason, source location (line/column), relevant token or IR instruction, and a remediation hint when determinable.

---

## Performance metrics convention

| Metric | Unit | Notes |
|--------|------|-------|
| Instruction count | Integer | Total FRISC instructions executed in simulation |
| Optimization impact | Percentage reduction | Relative to O0 (unoptimized) baseline |
| Interpreter step limit | 2,000,000 | Hard limit per run |
| Simulator step limit | 200,000,000 | Hard limit per FRISC simulation run |
| Helper share | Percentage of total instruction count | Fraction spent in `F_*` routines |
