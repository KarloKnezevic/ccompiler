# Appendix G. IR Definition Reference

This appendix provides an annotated reference for the FRISCcc intermediate
representation grammar (`config/ir_definition.txt`). The IR is a typed,
explicit, three-address-code-style representation that serves as the interface
between the compiler frontend (parsing and semantic analysis) and the backend
(optimization and FRISC code generation). The grammar is intentionally strict:
every value carries an explicit type annotation, every instruction belongs to a
basic block, and every function declares its frame layout.

## G.1 Design Principles

The IR grammar embodies the following design decisions:

1. **Explicit types everywhere.** Every temporary, constant, operation, and
   memory access carries a type annotation. This eliminates the need for type
   inference during optimization and code generation.

2. **SSA-style temporaries.** Each temporary `tN` is defined exactly once. This
   property simplifies def-use analysis and enables efficient optimization
   passes.

3. **Newline-delimited statements.** Newlines are significant at statement
   boundaries, making the IR human-readable and line-oriented for diagnostics.

4. **Explicit frame metadata.** Each function declares its stack frame size,
   alignment, and a slot table mapping parameters, locals, and spill locations
   to byte offsets within the frame.

5. **Structured control flow.** The control-flow graph is represented as a
   sequence of labeled basic blocks, each terminated by exactly one terminator
   instruction (`br`, `jmp`, or `ret`).

## G.2 Program Structure

An IR program is enclosed in `.program` / `.endprogram` delimiters and contains
three kinds of top-level declarations:

- **Global declarations** (`.globals` section): zero or more global variables,
  each with a name, type, and optional initializer constant.
- **Type definitions** (`.type` directive): named struct types with explicitly
  offset fields.
- **Function definitions** (`.func` / `.endfunc`): the core compilation units.

## G.3 Function Structure

Each function definition contains four sections in order:

1. **Signature**: function name, parameter list with types, and return type.
2. **Frame declaration** (`.frame`): total local storage size in bytes and
   alignment requirement.
3. **Slot table** (`.slots`): a list of named storage entries, each tagged as
   `param`, `local`, or `spill`, with an explicit byte offset and type.
4. **Blocks** (`.blocks`): the control-flow graph as a sequence of labeled
   basic blocks.

### Slot Kinds

| Kind | Description |
|---|---|
| `param` | A function parameter, passed by the caller |
| `local` | A local variable declared in the source program |
| `spill` | A compiler-generated temporary that could not be held in a register |

## G.4 Instruction Categories

### Assignment Instructions

The form `tN = <rhs>` assigns the result of a right-hand-side expression to a
temporary. The RHS may be any of the following:

| RHS Form | Syntax | Description |
|---|---|---|
| Address of symbol | `addr_of_symbol local:x` | Compute address of a named slot |
| Address of element | `addr_index base, idx, size` | Compute address of an array element |
| Address of field | `addr_field base, Type.field` | Compute address of a struct field |
| Load | `load addr : type` | Read a typed value from memory |
| Binary operation | `add v1, v2 : type` | Arithmetic or bitwise operation |
| Comparison | `cmp_lt v1, v2 : bool` | Relational or equality test |
| Unary operation | `neg v : type` | Negation or bitwise complement |
| Increment/decrement | `preinc v : type` | Pre/post increment or decrement |
| Cast | `itof v : float` | Type conversion |
| Function call | `call func:f(args) : type` | Call with return value |
| Constant | `#42:int32` | Literal value |

### Store Instruction

The form `store addr, value : type` writes a typed value to the memory location
designated by `addr`.

### Void Call Instruction

The form `call func:f(args) : void` invokes a function that returns no value.

### Terminator Instructions

Every basic block ends with exactly one terminator:

| Terminator | Syntax | Description |
|---|---|---|
| Conditional branch | `br cond, Ltrue, Lfalse` | Branch on boolean condition |
| Unconditional jump | `jmp label` | Transfer to target block |
| Return | `ret` or `ret value` | Return from function |

## G.5 Binary and Comparison Operations

### Binary Operations

| Operation | Meaning |
|---|---|
| `add` | Addition |
| `sub` | Subtraction |
| `mul` | Multiplication |
| `div` | Division |
| `mod` | Modulo (remainder) |
| `and` | Bitwise AND |
| `or` | Bitwise OR |
| `xor` | Bitwise XOR |
| `shl` | Shift left |
| `shr` | Shift right |

### Comparison Operations

| Operation | Meaning |
|---|---|
| `cmp_eq` | Equal |
| `cmp_ne` | Not equal |
| `cmp_lt` | Less than |
| `cmp_le` | Less than or equal |
| `cmp_gt` | Greater than |
| `cmp_ge` | Greater than or equal |

All comparisons produce a `bool` result.

## G.6 Cast Operations

| Cast | Meaning |
|---|---|
| `trunc` | Truncate to a narrower integer type |
| `sext` | Sign-extend to a wider integer type |
| `zext` | Zero-extend to a wider integer type |
| `ptrcast` | Reinterpret a pointer as a different pointer type |
| `itof` | Integer to floating-point conversion |
| `ftoi` | Floating-point to integer conversion |

## G.7 Type System

The IR uses a fully explicit type system with the following type constructors:

| Type | Description |
|---|---|
| `void` | No value (function return type only) |
| `int32` | Signed 32-bit integer |
| `char` | Signed 8-bit character |
| `uchar` | Unsigned 8-bit character |
| `float` | Floating-point (implemented as Q16.16 fixed-point) |
| `bool` | Boolean (result of comparisons) |
| `ptr<T>` | Pointer to type T |
| `array<T,N>` | Array of N elements of type T |
| `struct Name` | Named struct type (defined by `.type` directive) |

## G.8 Constants

Constants in the IR carry explicit type annotations:

| Form | Example | Description |
|---|---|---|
| Integer | `#42:int32` | Signed integer constant |
| Character | `#'a':char` | Character constant (1 byte) |
| Float | `#3.14:float` | Floating-point constant |
| Null | `null:ptr<int32>` | Typed null pointer |
| Array | `{#1:int32, #2:int32} : array<int32,2>` | Aggregate array constant |

## G.9 Complete IR Grammar Listing

The following is the verbatim content of `config/ir_definition.txt`.

```text
; ============================================================
; IR Grammar (BNF) -- single-file, copy/paste ready
; Notes:
; - This grammar is intentionally strict and fully typed.
; - Newlines (NL) are significant at statement boundaries.
; - Comments here are part of this listing (for readability).
; ============================================================

Program
  ::= ".program" NL
      { TopLevel }
      ".endprogram" NL? ;

TopLevel
  ::= GlobalDecl
   |  TypeDef
   |  FuncDef ;

GlobalDecl
  ::= ".globals" NL
      { GlobalVar } ;

GlobalVar
  ::= "global" Ident ":" Type [ "=" Const ] NL ;

TypeDef
  ::= ".type" "struct" Ident "{" NL
      { StructField }
      "}" NL ;

StructField
  ::= Ident ":" Type "@" Int NL ;

FuncDef
  ::= ".func" Ident "(" [ ParamList ] ")" ":" Type NL
      FrameDecl
      SlotsDecl
      BlocksDecl
      ".endfunc" NL ;

ParamList
  ::= Param { "," Param } ;

Param
  ::= Ident ":" Type ;

FrameDecl
  ::= ".frame" "locals" "=" Int "bytes" "align" "=" Int NL ;

SlotsDecl
  ::= ".slots" NL
      { SlotEntry } ;

SlotEntry
  ::= SlotKind Ident "@" Int ":" Type NL ;

SlotKind
  ::= "param" | "local" | "spill" ;

BlocksDecl
  ::= ".blocks" NL
      { Block } ;

Block
  ::= Label ":" NL
      { Instr NL }
      Terminator NL ;

Label
  ::= Ident ;

Instr
  ::= AssignInstr
   |  StoreInstr
   |  VoidCallInstr ;

AssignInstr
  ::= Temp "=" Rhs ;

StoreInstr
  ::= "store" Value "," Value ":" Type ;

VoidCallInstr
  ::= "call" "func:" Ident "(" [ ArgList ] ")" ":" "void" ;

Terminator
  ::= BrTerm
   |  JmpTerm
   |  RetTerm ;

BrTerm
  ::= "br" Value "," Label "," Label ;

JmpTerm
  ::= "jmp" Label ;

RetTerm
  ::= "ret" [ Value ] ;

ArgList
  ::= Value { "," Value } ;

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

AddrOfSymbol
  ::= "addr_of_symbol" SymbolRef ;

SymbolRef
  ::= ("local:" | "param:" | "global:") Ident ;

AddrIndex
  ::= "addr_index" Value "," Value "," Int ;

AddrField
  ::= "addr_field" Value "," Ident "." Ident ;

Load
  ::= "load" Value ":" Type ;

BinOp
  ::= BinOpName Value "," Value ":" Type ;

BinOpName
  ::= "add" | "sub" | "mul" | "div" | "mod"
   |  "and" | "or"  | "xor"
   |  "shl" | "shr" ;

CmpOp
  ::= CmpOpName Value "," Value ":" "bool" ;

CmpOpName
  ::= "cmp_eq" | "cmp_ne"
   |  "cmp_lt" | "cmp_le"
   |  "cmp_gt" | "cmp_ge" ;

UnaryOp
  ::= UnaryOpName Value ":" Type ;

UnaryOpName
  ::= "neg" | "not" ;

IncDecOp
  ::= IncDecName Value ":" Type ;

IncDecName
  ::= "preinc" | "postinc" | "predec" | "postdec" ;

Call
  ::= "call" "func:" Ident "(" [ ArgList ] ")" ":" Type ;

CastOp
  ::= CastName Value ":" Type ;

CastName
  ::= "trunc" | "sext" | "zext"
   |  "ptrcast"
   |  "itof" | "ftoi" ;

Temp
  ::= "t" Int ;

Value
  ::= Temp | Const ;

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

Type
  ::= "void"
   |  "int32" | "char" | "uchar" | "float" | "bool"
   |  "ptr" "<" Type ">"
   |  "array" "<" Type "," Int ">"
   |  "struct" Ident ;

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
