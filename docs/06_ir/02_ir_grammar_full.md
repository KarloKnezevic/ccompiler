> **📖 From the book.** This chapter accompanies *Building a C-Subset Compiler for the FRISC Architecture: From Formal Languages to Executable Code* by Dr. Karlo Knežević (Zenodo, 2026). For the complete treatment — formal development, proofs, and figures — read the book: [📄 PDF](../book/Building-a-C-Subset-Compiler-for-the-FRISC-Architecture.pdf) · DOI [10.5281/zenodo.20511073](https://doi.org/10.5281/zenodo.20511073) · ISBN 978-953-47198-0-0.

## Chapter 6 Appendix. Complete IR Grammar

\index{IR!grammar}

This chapter presents the complete BNF grammar that defines the syntax of the typed intermediate representation. The grammar is reproduced from `config/ir_definition.txt` and constitutes the authoritative contract between IR producers (`compiler-ir`), consumers (`compiler-opt`, `compiler-codegen`), and verification tools. Any textual IR that conforms to this grammar is structurally valid; any text that does not is rejected.

The grammar is context-free. Context-sensitive constraints -- such as type consistency between operands and result, resolution of symbol references, and slot offset validity -- are enforced by separate verification passes described in Section 6.17 of the preceding chapter.

## A.1 Notation Conventions

\index{BNF notation}

The grammar uses extended BNF with the following conventions:

| Notation | Meaning |
|----------|---------|
| `"keyword"` | Terminal string (literal keyword or punctuation) |
| `NonTerminal` | Non-terminal symbol (production name) |
| `A B` | Concatenation: A followed by B |
| `A \| B` | Alternation: A or B |
| `{ A }` | Repetition: zero or more occurrences of A |
| `[ A ]` | Optional: zero or one occurrence of A |
| `( A \| B )` | Grouping for alternation |
| `NL` | Newline character; significant at statement boundaries |
| `; text` | Comment within the grammar listing (not part of the syntax) |

### A.1.1 Reading the Grammar

Each production is written as:

```bnf
ProductionName
  ::= definition ;
```

The `::=` symbol means "is defined as." The semicolon terminates the production. Alternatives are separated by `|` and may span multiple lines for readability. Where terminal strings appear in double quotes (e.g., `".func"`, `"add"`), these are literal keywords that must appear verbatim in the IR text.

### A.1.2 Terminal Categories

The grammar references several classes of terminal symbols:

| Terminal | Description | Examples |
|----------|-------------|----------|
| `Ident` | Identifier (letter followed by letters, digits, underscores) | `main`, `counter`, `L0`, `Point` |
| `Int` | Decimal integer with optional leading minus | `0`, `42`, `-1`, `256` |
| `FloatLit` | Decimal float with mandatory decimal point | `3.14`, `0.0`, `1.5` |
| `CharLit` | Single-quoted character with escape support | `'A'`, `'\n'`, `'\\'` |
| `NL` | Newline character | (line break) |

### A.1.3 Whitespace and Comments

Within a line, whitespace (spaces and tabs) separates tokens but is otherwise insignificant. Newlines are syntactically meaningful as statement terminators: each instruction, slot entry, struct field, and global variable declaration must end with a newline. Comments in IR text begin with `;` and extend to the end of the line. Comments are ignored by the parser.

```ir
  t0 = addr_of_symbol local:x    ; this is a comment
  t1 = load t0 : int32           ; comments are discarded by the parser
```

## A.2 Program Structure

\index{IR!program structure}

A program is a sequence of top-level declarations enclosed by `.program` and `.endprogram` markers.

```bnf
Program
  ::= ".program" NL
      { TopLevel }
      ".endprogram" NL? ;
```

The trailing `NL?` permits the file to end with or without a final newline.

```bnf
TopLevel
  ::= GlobalDecl
   |  TypeDef
   |  FuncDef ;
```

Top-level elements may appear in any order. In practice, globals and type definitions precede function definitions, but the grammar does not enforce this ordering.

**Example -- minimal program:**
```ir
.program
.func main():int32
  .frame locals=0 bytes align=4
  .slots
  .blocks
  L0:
    ret #0:int32
.endfunc
.endprogram
```

**Example -- program with all top-level elements:**
```ir
.program
.type struct Point {
  x : int32 @0
  y : int32 @4
}
.globals
  global counter : int32 = #0:int32
  global origin : struct Point
.func add(a:int32, b:int32):int32
  .frame locals=0 bytes align=4
  .slots
    param a@0 : int32
    param b@4 : int32
  .blocks
  L0:
    t0 = addr_of_symbol param:a
    t1 = load t0 : int32
    t2 = addr_of_symbol param:b
    t3 = load t2 : int32
    t4 = add t1, t3 : int32
    ret t4
.endfunc
.endprogram
```

## A.3 Global Declarations

\index{global variable!declaration}

```bnf
GlobalDecl
  ::= ".globals" NL
      { GlobalVar } ;
```

The `.globals` section contains zero or more global variable declarations. A program may contain at most one `.globals` section.

```bnf
GlobalVar
  ::= "global" Ident ":" Type [ "=" Const ] NL ;
```

Each global variable has a name, a type, and an optional initializer constant. The initializer, when present, must be a compile-time constant whose type matches the declared type. Uninitialized globals are zero-initialized by convention.

**Examples:**
```ir
.globals
  global counter : int32 = #0:int32
  global buffer : array<char,256>
  global root : ptr<struct Node> = null:ptr<struct Node>
  global pi : float = #3.14:float
  global flags : int32 = #255:int32
```

| Declaration | Type | Initializer | Description |
|-------------|------|-------------|-------------|
| `global counter : int32 = #0:int32` | `int32` | `#0:int32` | Integer global with initializer |
| `global buffer : array<char,256>` | `array<char,256>` | (none, zero-filled) | Uninitialized character buffer |
| `global root : ptr<struct Node> = null:ptr<struct Node>` | `ptr<struct Node>` | `null:ptr<struct Node>` | Null-initialized pointer |
| `global pi : float = #3.14:float` | `float` | `#3.14:float` | Float constant |

## A.4 Struct Type Definitions

\index{struct!type definition grammar}

```bnf
TypeDef
  ::= ".type" "struct" Ident "{" NL
      { StructField }
      "}" NL ;
```

A type definition introduces a named struct type. The name is globally visible and must be unique among struct type definitions.

```bnf
StructField
  ::= Ident ":" Type "@" Int NL ;
```

Each field has a name, a type, and an explicit byte offset from the start of the struct. The byte offset is a non-negative decimal integer. Fields are listed in increasing offset order by convention. The compiler computes offsets during IR generation based on field sizes and alignment; by recording them explicitly, the backend avoids recomputing layout.

**Example -- simple struct:**
```ir
.type struct Point {
  x : int32 @0
  y : int32 @4
}
```

**Example -- struct with pointer field (linked list node):**
```ir
.type struct Node {
  value : int32 @0
  next : ptr<struct Node> @4
}
```

**Example -- struct with mixed types:**
```ir
.type struct Rect {
  x : int32 @0
  y : int32 @4
  width : int32 @8
  height : int32 @12
}
```

**Example -- struct containing a nested struct type:**
```ir
.type struct Line {
  start : struct Point @0
  end : struct Point @8
}
```

In this case, the `start` field occupies bytes 0--7 (the size of `struct Point` is 8 bytes), and `end` begins at byte 8.

## A.5 Function Definitions

\index{function!definition grammar}

```bnf
FuncDef
  ::= ".func" Ident "(" [ ParamList ] ")" ":" Type NL
      FrameDecl
      SlotsDecl
      BlocksDecl
      ".endfunc" NL ;
```

A function definition consists of a signature (name, parameters, return type) followed by frame metadata, slot declarations, and a control-flow graph. The return type may be `void`, in which case `ret` instructions in the body must not carry a value.

```bnf
ParamList
  ::= Param { "," Param } ;

Param
  ::= Ident ":" Type ;
```

Parameters are comma-separated. Each parameter has a name and a type. The parameter list may be empty (no parameters), represented by an empty pair of parentheses.

**Example -- void function with no parameters:**
```ir
.func init():void
  .frame locals=0 bytes align=4
  .slots
  .blocks
  L0:
    ret
.endfunc
```

**Example -- function with multiple parameters:**
```ir
.func clamp(value:int32, lo:int32, hi:int32):int32
  .frame locals=0 bytes align=4
  .slots
    param value@0 : int32
    param lo@4 : int32
    param hi@8 : int32
  .blocks
  L0:
    ; ... body ...
    ret t0
.endfunc
```

**Example -- function with pointer parameter:**
```ir
.func strlen(s:ptr<char>):int32
  .frame locals=4 bytes align=4
  .slots
    param s@0 : ptr<char>
    local count@0 : int32
  .blocks
  L0:
    ; ... body ...
    ret t0
.endfunc
```

## A.6 Frame and Slot Metadata

\index{frame!declaration grammar}
\index{slot!declaration grammar}

```bnf
FrameDecl
  ::= ".frame" "locals" "=" Int "bytes" "align" "=" Int NL ;
```

The frame declaration specifies the total byte size required for local variables and spills, and the alignment constraint (typically 4 for 32-bit targets). The `locals` value determines the stack pointer decrement in the function prologue.

```bnf
SlotsDecl
  ::= ".slots" NL
      { SlotEntry } ;

SlotEntry
  ::= SlotKind Ident "@" Int ":" Type NL ;

SlotKind
  ::= "param" | "local" | "spill" ;
```

Slots are the named, addressable storage locations within a function frame. Each slot has:

- A **kind**: `param` (function parameter), `local` (local variable), or `spill` (optimizer-generated temporary storage).
- A **name**: the identifier used in `addr_of_symbol` references.
- A **byte offset**: relative to the start of the respective frame zone.
- A **type**: the declared type of the stored value.

Parameter slots correspond to the calling convention's parameter area (FP+8 upward). Local and spill slots correspond to the local area (FP-4 downward).

**Example -- slot declarations:**
```ir
  .frame locals=16 bytes align=4
  .slots
    param arr@0 : ptr<int32>
    param n@4 : int32
    local total@0 : int32
    local i@4 : int32
    local tmp@8 : float
    local buf@12 : array<char,4>
```

| Slot | Kind | Offset | Type | FRISC Address |
|------|------|--------|------|---------------|
| `arr` | param | @0 | `ptr<int32>` | FP + 8 |
| `n` | param | @4 | `int32` | FP + 12 |
| `total` | local | @0 | `int32` | FP - 4 |
| `i` | local | @4 | `int32` | FP - 8 |
| `tmp` | local | @8 | `float` | FP - 12 |
| `buf` | local | @12 | `array<char,4>` | FP - 16 |

## A.7 Basic Blocks and Control Flow

\index{basic block!grammar}

```bnf
BlocksDecl
  ::= ".blocks" NL
      { Block } ;

Block
  ::= Label ":" NL
      { Instr NL }
      Terminator NL ;

Label
  ::= Ident ;
```

A basic block consists of a label, zero or more non-terminating instructions, and exactly one terminator. The label is an identifier (e.g., `L0`, `L1`, `loop_body`, `exit`). The first block in the `.blocks` section is the function entry point.

The single-terminator invariant ensures that control flow is explicit: every block transfers control to one or two successor blocks, and these successors are identified by label name. This enables direct construction of a control-flow graph from the block structure.

**Example -- block with instructions and terminator:**
```ir
  L0:
    t0 = addr_of_symbol param:a
    t1 = load t0 : int32
    t2 = addr_of_symbol param:b
    t3 = load t2 : int32
    t4 = cmp_gt t1, t3 : bool
    br t4, L1, L2
```

**Example -- empty block (only terminator):**
```ir
  L3:
    jmp L0
```

**Example -- block with void return:**
```ir
  L5:
    ret
```

## A.8 Instructions

\index{instruction!grammar}

```bnf
Instr
  ::= AssignInstr
   |  StoreInstr
   |  VoidCallInstr ;
```

The three instruction types form a sealed set. No other instruction forms exist in the IR.

### A.8.1 Assignment

```bnf
AssignInstr
  ::= Temp "=" Rhs ;
```

Assigns the result of evaluating a right-hand side expression to a temporary. Temporaries are of the form `tN` where `N` is a non-negative integer.

**Examples:**
```ir
  t0 = addr_of_symbol local:x              ; address computation
  t1 = load t0 : int32                     ; memory load
  t2 = add t1, #5:int32 : int32            ; binary operation
  t3 = cmp_gt t1, t2 : bool                ; comparison
  t4 = call func:foo(t1, t2) : int32       ; function call
  t5 = neg t1 : int32                      ; unary operation
  t6 = sext t7 : int32                     ; cast operation
  t8 = #42:int32                            ; constant assignment
  t9 = preinc t0 : int32                   ; increment/decrement
  t10 = addr_index t0, t1, 4               ; array index address
  t11 = addr_field t0, Point.x             ; struct field address
```

### A.8.2 Store

```bnf
StoreInstr
  ::= "store" Value "," Value ":" Type ;
```

Writes the second value to the memory address given by the first value. The type annotation specifies the width and type of the write. The first value is an address (typically produced by an `addr_of_symbol`, `addr_index`, or `addr_field` instruction); the second is the data value.

**Examples:**
```ir
  store t0, #42:int32 : int32               ; store constant to variable address
  store t0, t1 : int32                      ; store temporary to address
  store t3, #'A':char : char                ; store character constant
  store t5, null:ptr<int32> : ptr<int32>    ; store null pointer
  store t7, t8 : float                      ; store float value
```

### A.8.3 Void Call

```bnf
VoidCallInstr
  ::= "call" "func:" Ident "(" [ ArgList ] ")" ":" "void" ;
```

Invokes a function that returns `void`. No temporary is assigned.

**Examples:**
```ir
  call func:print_int(t0) : void            ; one argument
  call func:swap(t0, t1) : void             ; two arguments
  call func:init() : void                   ; no arguments
  call func:set_pixel(t0, t1, t2) : void    ; three arguments
```

## A.9 Terminators

\index{terminator!grammar}

```bnf
Terminator
  ::= BrTerm
   |  JmpTerm
   |  RetTerm ;
```

### A.9.1 Conditional Branch

```bnf
BrTerm
  ::= "br" Value "," Label "," Label ;
```

Evaluates the condition value (which must be of type `bool`). If true, control transfers to the first label; if false, to the second. Both labels must refer to blocks defined within the same function.

**Examples:**
```ir
  br t4, L1, L2                     ; branch on comparison result
  br t0, loop_body, loop_exit       ; loop condition branch
```

### A.9.2 Unconditional Jump

```bnf
JmpTerm
  ::= "jmp" Label ;
```

Unconditionally transfers control to the named block.

**Examples:**
```ir
  jmp L1                            ; jump to condition check
  jmp loop_body                     ; jump to loop body
  jmp after_if                      ; jump past if-else
```

### A.9.3 Return

```bnf
RetTerm
  ::= "ret" [ Value ] ;
```

Returns from the function. For non-void functions, a value must be provided. For void functions, the value must be absent. The value's type must match the function's declared return type.

**Examples:**
```ir
  ret t22                           ; return a computed value
  ret #0:int32                      ; return a constant
  ret                               ; void return (no value)
```

## A.10 Argument Lists

```bnf
ArgList
  ::= Value { "," Value } ;
```

Used in both value-returning `Call` and void `VoidCallInstr`. Arguments are values (temporaries or constants) passed to the callee.

**Examples:**
```ir
  call func:add(t0, t1) : int32                  ; two temp arguments
  call func:set(#0:int32, #1:int32) : void        ; two constant arguments
  call func:mixed(t0, #42:int32, t2) : int32      ; mixed arguments
```

## A.11 Right-Hand Side Expressions

\index{RHS expression!grammar}

```bnf
Rhs
  ::= AddrOfSymbol
   |  AddrIndex
   |  AddrField
   |  Load
   |  BinOp
   |  CmpOp
   |  Call
   |  UnaryOp
   |  IncDecOp
   |  CastOp
   |  Const ;
```

The `Rhs` production enumerates all expression forms that can appear on the right side of an assignment. Each alternative produces a typed value. The eleven alternatives form a closed set (sealed interface in the Java implementation).

### A.11.1 Address Computations

```bnf
AddrOfSymbol
  ::= "addr_of_symbol" SymbolRef ;

SymbolRef
  ::= ("local:" | "param:" | "global:") Ident ;
```

Computes the runtime address of a named storage location. The symbol reference prefix identifies the storage kind, which determines the addressing formula used during code generation.

**Examples:**
```ir
  t0 = addr_of_symbol local:x         ; address of local variable x
  t1 = addr_of_symbol param:arr       ; address of parameter arr
  t2 = addr_of_symbol global:counter  ; address of global variable counter
```

| Symbol kind | Addressing formula | Description |
|-------------|-------------------|-------------|
| `local:name` | FP - 4 - offset | Local variable in frame |
| `param:name` | FP + 8 + offset | Function parameter |
| `global:name` | Absolute label | Static data segment |

```bnf
AddrIndex
  ::= "addr_index" Value "," Value "," Int ;
```

Computes an array element address: `base + index * elemSizeBytes`. The first value is the array base address, the second is the index, and the integer is the element size in bytes.

**Examples:**
```ir
  t3 = addr_index t0, t1, 4          ; int32 array: base + i * 4
  t4 = addr_index t0, t2, 1          ; char array: base + i * 1
  t5 = addr_index t0, #3:int32, 8    ; struct array: base + 3 * 8
  t6 = addr_index t0, t1, 4          ; pointer array: base + i * 4
```

| Element type | Element size | Example |
|-------------|-------------|---------|
| `int32` | 4 | `addr_index base, idx, 4` |
| `char` | 1 | `addr_index base, idx, 1` |
| `float` | 4 | `addr_index base, idx, 4` |
| `ptr<T>` | 4 | `addr_index base, idx, 4` |
| `struct S` (8 bytes) | 8 | `addr_index base, idx, 8` |

```bnf
AddrField
  ::= "addr_field" Value "," Ident "." Ident ;
```

Computes a struct field address: `base + fieldOffset`. The first identifier is the struct type name, the second is the field name. The field's byte offset is looked up from the corresponding `.type struct` definition.

**Examples:**
```ir
  t7 = addr_field t0, Point.x         ; base + 0 (x is at offset 0)
  t8 = addr_field t0, Point.y         ; base + 4 (y is at offset 4)
  t9 = addr_field t1, Node.next       ; base + 4 (next is at offset 4)
  t10 = addr_field t0, Rect.height    ; base + 12 (height is at offset 12)
```

### A.11.2 Memory Load

```bnf
Load
  ::= "load" Value ":" Type ;
```

Reads a value from the memory address given by the operand. The type specifies both the read width and the resulting value's type.

**Examples:**
```ir
  t1 = load t0 : int32                ; load 4-byte integer
  t2 = load t0 : char                 ; load 1-byte character
  t3 = load t0 : float                ; load 4-byte float
  t4 = load t0 : ptr<int32>           ; load 4-byte pointer
  t5 = load t0 : ptr<struct Node>     ; load 4-byte struct pointer
```

### A.11.3 Binary Operations

```bnf
BinOp
  ::= BinOpName Value "," Value ":" Type ;

BinOpName
  ::= "add" | "sub" | "mul" | "div" | "mod"
   |  "and" | "or"  | "xor"
   |  "shl" | "shr" ;
```

Binary operations take two operands and produce a result, all of the declared type. The ten operations cover arithmetic (add, sub, mul, div, mod), bitwise logic (and, or, xor), and shifting (shl, shr).

**Examples for each binary operation:**

| Operation | Example | Semantics |
|-----------|---------|-----------|
| `add` | `t2 = add t0, t1 : int32` | `t2 = t0 + t1` |
| `sub` | `t2 = sub t0, t1 : int32` | `t2 = t0 - t1` |
| `mul` | `t2 = mul t0, t1 : int32` | `t2 = t0 * t1` |
| `div` | `t2 = div t0, t1 : int32` | `t2 = t0 / t1` (integer division) |
| `mod` | `t2 = mod t0, t1 : int32` | `t2 = t0 % t1` (remainder) |
| `and` | `t2 = and t0, t1 : int32` | `t2 = t0 & t1` (bitwise AND) |
| `or` | `t2 = or t0, t1 : int32` | `t2 = t0 \| t1` (bitwise OR) |
| `xor` | `t2 = xor t0, t1 : int32` | `t2 = t0 ^ t1` (bitwise XOR) |
| `shl` | `t2 = shl t0, t1 : int32` | `t2 = t0 << t1` (left shift) |
| `shr` | `t2 = shr t0, t1 : int32` | `t2 = t0 >> t1` (arithmetic right shift) |

**Examples with constants:**
```ir
  t2 = add t0, #1:int32 : int32      ; increment by 1
  t3 = mul t1, #4:int32 : int32      ; multiply by 4
  t4 = and t0, #0xFF:int32 : int32   ; mask to lower 8 bits
  t5 = shl t0, #2:int32 : int32      ; shift left by 2
```

**Float arithmetic:**
```ir
  t2 = add t0, t1 : float            ; float addition
  t3 = mul t0, t1 : float            ; float multiplication
```

### A.11.4 Comparisons

```bnf
CmpOp
  ::= CmpOpName Value "," Value ":" "bool" ;

CmpOpName
  ::= "cmp_eq" | "cmp_ne"
   |  "cmp_lt" | "cmp_le"
   |  "cmp_gt" | "cmp_ge" ;
```

Comparison operations always produce `bool`. The six comparison operators cover equality and all four ordering relations.

**Examples for each comparison:**

| Operation | Example | C equivalent |
|-----------|---------|--------------|
| `cmp_eq` | `t4 = cmp_eq t0, t1 : bool` | `t0 == t1` |
| `cmp_ne` | `t4 = cmp_ne t0, t1 : bool` | `t0 != t1` |
| `cmp_lt` | `t4 = cmp_lt t0, t1 : bool` | `t0 < t1` |
| `cmp_le` | `t4 = cmp_le t0, t1 : bool` | `t0 <= t1` |
| `cmp_gt` | `t4 = cmp_gt t0, t1 : bool` | `t0 > t1` |
| `cmp_ge` | `t4 = cmp_ge t0, t1 : bool` | `t0 >= t1` |

**Comparison with constant:**
```ir
  t2 = cmp_lt t0, #10:int32 : bool    ; i < 10
  t3 = cmp_eq t1, #0:int32 : bool     ; x == 0
  t4 = cmp_ne t0, null:ptr<int32> : bool  ; ptr != NULL
```

### A.11.5 Unary Operations

```bnf
UnaryOp
  ::= UnaryOpName Value ":" Type ;

UnaryOpName
  ::= "neg" | "not" | "bitnot" ;
```

**Examples:**

| Operation | Example | Semantics |
|-----------|---------|-----------|
| `neg` | `t1 = neg t0 : int32` | Arithmetic negation: `t1 = -t0` |
| `not` | `t1 = not t0 : int32` | Logical NOT: `t1 = !t0` |
| `bitnot` | `t1 = bitnot t0 : int32` | Bitwise complement: `t1 = ~t0` |

### A.11.6 Increment and Decrement

```bnf
IncDecOp
  ::= IncDecName Value ":" Type ;

IncDecName
  ::= "preinc" | "postinc" | "predec" | "postdec" ;
```

These value-producing operations model C's `++x`, `x++`, `--x`, and `x--`. The "pre" variants return the updated value; the "post" variants return the original value. The operand is the address of the variable being modified.

**Examples:**

| C expression | IR instruction | Return value |
|-------------|----------------|--------------|
| `++x` | `t1 = preinc t0 : int32` | New value (x + 1) |
| `x++` | `t1 = postinc t0 : int32` | Old value (x before increment) |
| `--x` | `t1 = predec t0 : int32` | New value (x - 1) |
| `x--` | `t1 = postdec t0 : int32` | Old value (x before decrement) |

In all cases, `t0` is the address of variable `x` (produced by `addr_of_symbol`), and the variable is both read and written by the operation.

### A.11.7 Function Calls

```bnf
Call
  ::= "call" "func:" Ident "(" [ ArgList ] ")" ":" Type ;
```

Calls a function and produces a value of the declared return type. The `func:` prefix disambiguates function names from other identifiers in the grammar.

**Examples:**
```ir
  t4 = call func:add(t0, t1) : int32         ; two arguments, returns int32
  t5 = call func:strlen(t2) : int32          ; one argument
  t6 = call func:alloc(#100:int32) : ptr<char>  ; constant argument, returns pointer
  t7 = call func:getchar() : char             ; no arguments, returns char
```

### A.11.8 Casts

```bnf
CastOp
  ::= CastName Value ":" Type ;

CastName
  ::= "trunc" | "sext" | "zext"
   |  "ptrcast"
   |  "itof" | "ftoi" ;
```

Cast operations perform explicit type conversions. The result type is the conversion target:

| Cast | Source type | Target type | Semantics |
|------|-----------|-------------|-----------|
| `trunc` | Wider integer | Narrower integer | Truncate high bits (e.g., `int32` to `char`) |
| `sext` | Narrower signed integer | Wider integer | Sign-extend (e.g., `char` to `int32`) |
| `zext` | Narrower unsigned integer | Wider integer | Zero-extend (e.g., `uchar` to `int32`) |
| `ptrcast` | `ptr<A>` | `ptr<B>` | Reinterpret pointer base type, same address |
| `itof` | Integer | `float` | Convert integer to floating-point |
| `ftoi` | `float` | Integer | Convert floating-point to integer (truncate toward zero) |

**Examples:**
```ir
  t1 = sext t0 : int32              ; char -> int32 (sign-extend)
  t2 = zext t0 : int32              ; uchar -> int32 (zero-extend)
  t3 = trunc t0 : char              ; int32 -> char (truncate)
  t4 = ptrcast t0 : ptr<char>       ; ptr<int32> -> ptr<char>
  t5 = itof t0 : float              ; int32 -> float
  t6 = ftoi t0 : int32              ; float -> int32
```

## A.12 Values, Temporaries, and Constants

\index{temporary!grammar}
\index{constant!grammar}

```bnf
Temp
  ::= "t" Int ;

Value
  ::= Temp | Const ;
```

A value is either a temporary or a constant. Temporaries are named `t0`, `t1`, `t2`, etc. They are defined by assignment instructions and consumed by subsequent instructions.

```bnf
Const
  ::= ScalarConst | AggregateConst ;

ScalarConst
  ::= "#" Int ":" Type
   |  "#" CharLit ":" "char"
   |  "#" FloatLit ":" "float"
   |  "null" ":" Type ;

AggregateConst
  ::= ArrayConst ;

ArrayConst
  ::= "{" [ Const { "," Const } ] "}" ":" ArrayType ;

ArrayType
  ::= "array" "<" Type "," Int ">" ;
```

### A.12.1 Scalar Constants

Scalar constants use the `#` prefix followed by a literal value and an explicit type annotation.

**Integer constants:**
```ir
  #0:int32          ; zero
  #42:int32         ; positive integer
  #-1:int32         ; negative integer
  #255:int32        ; hex-range value in decimal
```

**Character constants:**
```ir
  #'A':char         ; uppercase A
  #'0':char         ; digit zero character
  #'\n':char        ; newline escape
  #'\t':char        ; tab escape
  #'\\':char        ; backslash escape
  #'\'':char        ; single-quote escape
```

**Float constants:**
```ir
  #3.14:float       ; pi approximation
  #0.0:float        ; zero
  #1.5:float        ; one and a half
```

**Null pointer constants:**
```ir
  null:ptr<int32>         ; null pointer to int
  null:ptr<char>          ; null pointer to char
  null:ptr<struct Node>   ; null pointer to struct
```

### A.12.2 Aggregate Constants

**Array constants:**
```ir
  { #1:int32, #2:int32, #3:int32 } : array<int32,3>
  { #'h':char, #'i':char, #'\n':char } : array<char,3>
  { } : array<int32,0>   ; empty array (edge case)
```

The element count in the type must match the number of provided constants.

## A.13 Type Productions

\index{type!grammar}

```bnf
Type
  ::= "void"
   |  "int32" | "char" | "uchar" | "float" | "bool"
   |  "ptr" "<" Type ">"
   |  "array" "<" Type "," Int ">"
   |  "struct" Ident ;
```

Types are fully explicit. Pointer and array types are parametric: `ptr<T>` contains a nested type, and `array<T,N>` contains a type and an integer size. Struct types are nominal, identified by the name that appears in the corresponding `.type struct` definition.

The type grammar is unambiguous: the leading keyword uniquely determines which alternative applies. This makes parsing straightforward with a single token of lookahead.

**Complete type examples:**

| Type expression | Description |
|----------------|-------------|
| `void` | Void (function return type only) |
| `int32` | 32-bit signed integer |
| `char` | 8-bit signed character |
| `uchar` | 8-bit unsigned character |
| `float` | Floating-point number |
| `bool` | Boolean (comparison result) |
| `ptr<int32>` | Pointer to int32 |
| `ptr<char>` | Pointer to char |
| `ptr<ptr<int32>>` | Pointer to pointer to int32 |
| `ptr<struct Node>` | Pointer to struct Node |
| `array<int32,10>` | Array of 10 int32 elements |
| `array<char,256>` | Array of 256 chars |
| `array<ptr<int32>,5>` | Array of 5 pointers to int32 |
| `array<struct Point,3>` | Array of 3 Point structs |
| `struct Point` | Named struct type |
| `struct Node` | Named struct type |

## A.14 Lexical Rules

\index{lexical rules}

```bnf
Ident
  ::= Letter { Letter | Digit | "_" } ;

Int
  ::= ["-"] Digit { Digit } ;

FloatLit
  ::= Digit { Digit } "." Digit { Digit } ;

CharLit
  ::= "'" ( Escape | AnyCharExceptQuote ) "'" ;

Escape
  ::= "\n" | "\t" | "\\'" | "\\\\" ;

NL
  ::= "\n" ;

Letter
  ::= "A" | ... | "Z" | "a" | ... | "z" ;

Digit
  ::= "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" ;

AnyCharExceptQuote
  ::= ? any character except ' ? ;
```

Identifiers begin with a letter and may contain letters, digits, and underscores. Integers are decimal with an optional leading minus sign. Float literals require at least one digit on each side of the decimal point. Character literals follow C conventions with a limited escape set.

Newlines (`NL`) are syntactically significant as statement terminators. Each instruction, slot entry, struct field, and global variable declaration is terminated by a newline. This design permits line-oriented parsing, which simplifies error reporting (every error can be located by line number) and human readability.

## A.15 Complete Instruction Quick Reference

The following table provides a one-line summary of every instruction form in the IR, with its syntax and the production rule it derives from:

| Category | Instruction | Syntax | Result type |
|----------|------------|--------|-------------|
| Address | `addr_of_symbol` | `t = addr_of_symbol (local\|param\|global):name` | `ptr<T>` |
| Address | `addr_index` | `t = addr_index base, idx, elemSize` | `ptr<T>` |
| Address | `addr_field` | `t = addr_field base, Struct.field` | `ptr<T>` |
| Memory | `load` | `t = load addr : T` | `T` |
| Memory | `store` | `store addr, val : T` | (none) |
| Arithmetic | `add` | `t = add v1, v2 : T` | `T` |
| Arithmetic | `sub` | `t = sub v1, v2 : T` | `T` |
| Arithmetic | `mul` | `t = mul v1, v2 : T` | `T` |
| Arithmetic | `div` | `t = div v1, v2 : T` | `T` |
| Arithmetic | `mod` | `t = mod v1, v2 : T` | `T` |
| Bitwise | `and` | `t = and v1, v2 : T` | `T` |
| Bitwise | `or` | `t = or v1, v2 : T` | `T` |
| Bitwise | `xor` | `t = xor v1, v2 : T` | `T` |
| Bitwise | `shl` | `t = shl v1, v2 : T` | `T` |
| Bitwise | `shr` | `t = shr v1, v2 : T` | `T` |
| Comparison | `cmp_eq` | `t = cmp_eq v1, v2 : bool` | `bool` |
| Comparison | `cmp_ne` | `t = cmp_ne v1, v2 : bool` | `bool` |
| Comparison | `cmp_lt` | `t = cmp_lt v1, v2 : bool` | `bool` |
| Comparison | `cmp_le` | `t = cmp_le v1, v2 : bool` | `bool` |
| Comparison | `cmp_gt` | `t = cmp_gt v1, v2 : bool` | `bool` |
| Comparison | `cmp_ge` | `t = cmp_ge v1, v2 : bool` | `bool` |
| Unary | `neg` | `t = neg v : T` | `T` |
| Unary | `not` | `t = not v : T` | `T` |
| Unary | `bitnot` | `t = bitnot v : T` | `T` |
| Inc/Dec | `preinc` | `t = preinc addr : T` | `T` |
| Inc/Dec | `postinc` | `t = postinc addr : T` | `T` |
| Inc/Dec | `predec` | `t = predec addr : T` | `T` |
| Inc/Dec | `postdec` | `t = postdec addr : T` | `T` |
| Cast | `trunc` | `t = trunc v : T` | `T` (narrower) |
| Cast | `sext` | `t = sext v : T` | `T` (wider) |
| Cast | `zext` | `t = zext v : T` | `T` (wider) |
| Cast | `ptrcast` | `t = ptrcast v : ptr<T>` | `ptr<T>` |
| Cast | `itof` | `t = itof v : float` | `float` |
| Cast | `ftoi` | `t = ftoi v : T` | `T` (integer) |
| Call | `call` (value) | `t = call func:name(args) : T` | `T` |
| Call | `call` (void) | `call func:name(args) : void` | (none) |
| Terminator | `br` | `br cond, trueLabel, falseLabel` | (control flow) |
| Terminator | `jmp` | `jmp label` | (control flow) |
| Terminator | `ret` | `ret [value]` | (control flow) |

## A.16 Grammar Summary Statistics

| Category | Count | Productions |
|----------|-------|-------------|
| Program structure | 5 | Program, TopLevel, GlobalDecl, GlobalVar, TypeDef |
| Struct fields | 1 | StructField |
| Function structure | 6 | FuncDef, ParamList, Param, FrameDecl, SlotsDecl, SlotEntry |
| Basic blocks | 3 | BlocksDecl, Block, Label |
| Instructions | 3 | Instr, AssignInstr, StoreInstr, VoidCallInstr |
| Terminators | 4 | Terminator, BrTerm, JmpTerm, RetTerm |
| RHS expressions | 12 | Rhs, AddrOfSymbol, SymbolRef, AddrIndex, AddrField, Load, BinOp, CmpOp, UnaryOp, IncDecOp, CastOp, Call |
| Operator names | 5 | BinOpName, CmpOpName, UnaryOpName, IncDecName, CastName |
| Values and constants | 7 | Temp, Value, Const, ScalarConst, AggregateConst, ArrayConst, ArrayType |
| Types | 1 | Type |
| Lexical | 8 | Ident, Int, FloatLit, CharLit, Escape, NL, Letter, Digit, AnyCharExceptQuote |
| **Total** | **~55** | |

The grammar is deliberately flat: most productions are one level deep, with no deeply nested recursion except in the `Type` production (for nested pointer and array types). This flatness makes the grammar easy to implement with a hand-written recursive-descent parser, which is the approach used in this compiler's IR reader.

## A.17 Complete Example Program

The following is a complete, valid IR program that demonstrates all major grammar features. It defines a struct type, declares global variables, and implements two functions:

```ir
.program
.type struct Point {
  x : int32 @0
  y : int32 @4
}
.globals
  global origin : struct Point
  global count : int32 = #0:int32
.func distance_sq(p:ptr<struct Point>, q:ptr<struct Point>):int32
  .frame locals=8 bytes align=4
  .slots
    param p@0 : ptr<struct Point>
    param q@4 : ptr<struct Point>
    local dx@0 : int32
    local dy@4 : int32
  .blocks
  L0:
    ; dx = p->x - q->x
    t0 = addr_of_symbol param:p
    t1 = load t0 : ptr<struct Point>
    t2 = addr_field t1, Point.x
    t3 = load t2 : int32
    t4 = addr_of_symbol param:q
    t5 = load t4 : ptr<struct Point>
    t6 = addr_field t5, Point.x
    t7 = load t6 : int32
    t8 = sub t3, t7 : int32
    t9 = addr_of_symbol local:dx
    store t9, t8 : int32
    ; dy = p->y - q->y
    t10 = addr_field t1, Point.y
    t11 = load t10 : int32
    t12 = addr_field t5, Point.y
    t13 = load t12 : int32
    t14 = sub t11, t13 : int32
    t15 = addr_of_symbol local:dy
    store t15, t14 : int32
    ; return dx*dx + dy*dy
    t16 = addr_of_symbol local:dx
    t17 = load t16 : int32
    t18 = mul t17, t17 : int32
    t19 = addr_of_symbol local:dy
    t20 = load t19 : int32
    t21 = mul t20, t20 : int32
    t22 = add t18, t21 : int32
    ret t22
.endfunc
.func main():int32
  .frame locals=16 bytes align=4
  .slots
    local a@0 : struct Point
    local b@8 : struct Point
  .blocks
  L0:
    ; a.x = 3
    t0 = addr_of_symbol local:a
    t1 = addr_field t0, Point.x
    store t1, #3:int32 : int32
    ; a.y = 4
    t2 = addr_field t0, Point.y
    store t2, #4:int32 : int32
    ; b.x = 0
    t3 = addr_of_symbol local:b
    t4 = addr_field t3, Point.x
    store t4, #0:int32 : int32
    ; b.y = 0
    t5 = addr_field t3, Point.y
    store t5, #0:int32 : int32
    ; result = distance_sq(&a, &b)
    t6 = addr_of_symbol local:a
    t7 = addr_of_symbol local:b
    t8 = call func:distance_sq(t6, t7) : int32
    ret t8
.endfunc
.endprogram
```

This example demonstrates:
- Struct type definition with explicit offsets
- Global variable declarations (with and without initializers)
- Function signatures with pointer parameters
- Frame and slot declarations for parameters and locals
- Address computation chains: `addr_of_symbol` followed by `addr_field` followed by `load`/`store`
- Binary arithmetic operations (`sub`, `mul`, `add`)
- Value-returning function calls
- Return statements with computed values
