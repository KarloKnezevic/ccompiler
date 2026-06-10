# FRISC Instruction-Set Architecture Reference

FRISC (FER Reduced Instruction Set Computer) is a 32-bit educational RISC ISA designed at the Faculty of Electrical Engineering and Computing (FER), University of Zagreb. This page documents the subset the FRISCcc compiler targets and emits.

---

## Architecture Summary

| Property | Value |
|---|---|
| Word width | 32 bits |
| Register file | 8 general-purpose registers (R0–R7) |
| Special registers | PC (32-bit), SR (32-bit status) |
| Instruction width | Fixed 32 bits |
| Memory model | Von Neumann, byte-addressable, 32-bit address space |
| Byte order | Little-endian |
| Alignment | Word instructions and `LOAD`/`STORE` require 4-byte alignment; `LOADH`/`STOREH` require 2-byte alignment; `LOADB`/`STOREB` have no alignment constraint |
| Stack direction | Grows downward |
| Hardware multiply/divide | None — implemented in software (see [Software Helpers](#software-helpers)) |
| Hardware float | None — Q16.16 fixed-point, implemented in software |

---

## Register File

| Register | Compiler ABI Role | Saved by | Notes |
|---|---|---|---|
| `R0` | Primary expression result; accumulator | Caller | Clobbered by any `CALL` |
| `R1` | Second operand; scratch | Caller | Used in binary ops; clobbered by any `CALL` |
| `R2` | Scratch | Caller | Used inside helper routines |
| `R3` | Scratch | Caller | Used inside helper routines |
| `R4` | Scratch | Caller | Used inside helper routines |
| `R5` | Frame pointer (FP) | **Callee** | Every prologue saves it; every epilogue restores it |
| `R6` | Return value | Caller | Every function writes its result here before `RET` |
| `R7` | Stack pointer (SP) | Managed structurally | Grows downward; initialized to `0x40000` (262,144) at program start; always 4-byte aligned |

The ABI is fully specified in [../pipeline/runtime-abi.md](../pipeline/runtime-abi.md).

---

## Status Register Flags

The four condition flags in SR are updated by ALU operations (`ADD`, `SUB`, `AND`, `OR`, `XOR`, `SHL`, `SHR`, `ASHR`, `CMP`):

| Flag | Name | Set When |
|---|---|---|
| `Z` | Zero | Result is zero |
| `N` | Negative | Most-significant bit of result is 1 |
| `C` | Carry | Carry-out from addition, borrow from subtraction, or shifted-out bit |
| `V` | Overflow | Signed two's-complement overflow |

---

## Addressing Modes

| Mode | Syntax | Effective Address / Value |
|---|---|---|
| Register | `ADD R1, R2, R3` | Operand in register |
| Immediate | `MOVE 100, R1` | 20-bit value, sign-extended to 32 bits |
| Absolute | `LOAD R0, (1000)` | Literal 20-bit address |
| Register-indirect | `LOAD R0, (R5)` | Address in register |
| Register + displacement | `LOAD R0, (R5+8)` | Register plus 20-bit signed offset |
| Register − displacement | `LOAD R0, (R5-1C)` | Register minus 20-bit signed offset |
| PC-relative | `JR offset` | PC + signed offset (ISA-complete; **not emitted by FRISCcc**) |

The 20-bit immediate field is sign-extended to 32 bits, giving a range of −524,288 to +524,287 for constants and direct addresses. Values outside this range require a multi-instruction load sequence (see [Large Immediate Encoding](#large-immediate-encoding)).

---

## Instruction Groups

### Data Movement

| Mnemonic | Operands | Effect | Notes |
|---|---|---|---|
| `MOVE` | `src, Rd` | `Rd := src` | `src` is a register or 20-bit sign-extended immediate; also used as `MOVE Rx, SR` and `MOVE SR, Rx` (ISA-complete; SR forms **not emitted by FRISCcc**) |

### Arithmetic

| Mnemonic | Operands | Effect | Flags updated |
|---|---|---|---|
| `ADD` | `Rs1, Rs2/imm, Rd` | `Rd := Rs1 + operand` | Z, N, C, V |
| `SUB` | `Rs1, Rs2/imm, Rd` | `Rd := Rs1 − operand` | Z, N, C, V |
| `ADC` | `Rs1, Rs2/imm, Rd` | `Rd := Rs1 + operand + C` | Z, N, C, V — emitted only inside `F_FMUL` helper |
| `CMP` | `Rs1, Rs2/imm` | Compute `Rs1 − operand`, update flags, discard result | Z, N, C, V |

`ADC` (add-with-carry) is ISA-complete and is emitted by FRISCcc exclusively inside the `F_FMUL` helper to maintain a 64-bit accumulator across two registers.

`SBC` (subtract-with-carry) is ISA-complete but **not emitted** by FRISCcc.

### Logic

| Mnemonic | Operands | Effect | Flags updated |
|---|---|---|---|
| `AND` | `Rs1, Rs2/imm, Rd` | `Rd := Rs1 & operand` | Z, N; C, V cleared |
| `OR` | `Rs1, Rs2/imm, Rd` | `Rd := Rs1 \| operand` | Z, N; C, V cleared |
| `XOR` | `Rs1, Rs2/imm, Rd` | `Rd := Rs1 ^ operand` | Z, N; C, V cleared |

### Shifts

Shift amounts are given as 5-bit immediates (range 0–31). The shifted-out bit is placed in the carry flag.

| Mnemonic | Operands | Effect | Notes |
|---|---|---|---|
| `SHL` | `Rs, imm, Rd` | `Rd := Rs << imm` (logical left shift, zero-fill) | Used for multiplication by powers of two, index scaling, Q16.16 shifts, and large-immediate encoding |
| `SHR` | `Rs, imm, Rd` | `Rd := Rs >> imm` (logical right shift, zero-fill) | Used for Q16.16 fraction extraction and helper internals |
| `ASHR` | `Rs, imm, Rd` | `Rd := Rs >> imm` (arithmetic right shift, sign-extending) | Used for `char` sign extension: `SHL R0, 18, R0; ASHR R0, 18, R0` |

`ROTL` and `ROTR` are ISA-complete but **not emitted** by FRISCcc.

### Compare and Conditional Jump

FRISCcc emits `CMP` followed by a conditional `JP_cc` to materialize Boolean results and to implement all conditional branches. The following condition suffixes appear in emitted code:

| Suffix | Condition | Typical use |
|---|---|---|
| (none) | Unconditional | `JP label` — fall-through, loop back |
| `_EQ` | Z = 1 (equal / zero) | `==` comparison, zero-loop termination |
| `_NE` | Z = 0 (not equal / non-zero) | `!=` comparison, loop continuation |
| `_SLT` | N ≠ V (signed less than) | `<` comparison, bounds check lower |
| `_SLE` | Z = 1 or N ≠ V (signed ≤) | `<=` comparison |
| `_SGT` | Z = 0 and N = V (signed >) | `>` comparison |
| `_SGE` | N = V (signed ≥) | `>=` comparison, bounds check upper |
| `_NC` | C = 0 (no carry) | Used inside `F_DIV`/`F_MOD` shift loop to test carry from `SHL` |

Additional suffixes defined by the ISA (`_N`, `_NN`, `_C`, `_V`, `_NV`, `_ULT`, `_ULE`, `_UGT`, `_UGE`) are **not emitted** by FRISCcc.

### Memory Access

| Mnemonic | Width | Operands | Effect | Alignment |
|---|---|---|---|---|
| `LOAD` | 32-bit word | `Rd, (addr)` | `Rd := MEM[addr & ~3]` | 4-byte |
| `STORE` | 32-bit word | `Rs, (addr)` | `MEM[addr & ~3] := Rs` | 4-byte |
| `LOADB` | 8-bit byte | `Rd, (addr)` | `Rd := zero-extend(MEM[addr])` | None |
| `STOREB` | 8-bit byte | `Rs, (addr)` | `MEM[addr] := Rs[7:0]` | None |

`LOADB`/`STOREB` are used for `char`-typed slot accesses and for the struct-copy (`memcpy`) helper emitted by `AddressLowerer.emitMemCopy`.

`LOADH`/`STOREH` (16-bit) are ISA-complete but **not emitted** by FRISCcc (the language has no 16-bit integer type).

### Control Flow

| Mnemonic | Operands | Effect | Cycles |
|---|---|---|---|
| `JP` | `label` / `JP_cc label` | Unconditional or conditional absolute jump; `PC := label` | 2 (branch penalty) |
| `CALL` | `label` | Push `PC+4` onto stack (`R7 -= 4; MEM[R7] := PC+4`); `PC := label` | 2 |
| `RET` | (none) | `PC := MEM[R7]; R7 += 4` | 2 |

`JR` (PC-relative) and conditional `CALL`/`RET` forms are ISA-complete but **not emitted** by FRISCcc (all calls are unconditional; all jumps use absolute labels).

### Stack Operations

| Mnemonic | Operands | Effect |
|---|---|---|
| `PUSH` | `Rs` | `R7 -= 4; MEM[R7] := Rs` |
| `POP` | `Rd` | `Rd := MEM[R7]; R7 += 4` |

`PUSH`/`POP` are used for saving/restoring `R5` in prologues/epilogues and for passing arguments when layout-based passing is unavailable.

### System

| Mnemonic | Operands | Effect |
|---|---|---|
| `HALT` | (none) | Terminate execution; simulator inspects `R6` for exit value |

`HALT` appears twice in every program: once in the startup stub (normal termination) and once in `L_BOUNDS_ERROR` (array-out-of-bounds trap, sets `R6 = -6`).

---

## Assembler Directives

The FRISCjs assembler recognizes the following directives, all of which FRISCcc emits:

| Directive | Usage by FRISCcc | Description |
|---|---|---|
| `DW value` | Global `int32`, `float`, `bool`, pointer scalars; word arrays | Define one 32-bit word |
| `DB value` | Global `char` scalars; `char` arrays | Define one byte |
| `` `DS n `` | Uninitialized arrays, struct storage, pointer-scratch buffers | Reserve `n` bytes |

Directives `` `ORG ``, `` `EQU ``, and `` `BASE `` are recognized by the assembler but **not emitted** by FRISCcc.

The assembler's default numeric base is hexadecimal. FRISCcc emits all immediate values in hex (via `LoweringSupport.formatImmediate`, which returns an uppercase hexadecimal string). The only exception is `0`, emitted as the bare literal `0`.

---

## Large Immediate Encoding

The 20-bit signed immediate field (`fitsSigned20`: range −524,288 to +524,287) is insufficient for all 32-bit constants. `ImmediateEmitter` splits values that fall outside this range into two instructions:

```
MOVE  <high16>, Rd       ; Load bits 31–16
SHL   Rd, 10, Rd         ; Shift left by 16 (hex 0x10)
OR    Rd, <low16>, Rd    ; Merge bits 15–0
```

This sequence is emitted for large stack-frame offsets, large array sizes, and the `INT_MIN` constant (0x80000000) used in the `F_DIV` helper.

---

## Software Helpers

FRISC has no hardware multiply, divide, or floating-point instructions. FRISCcc emits software helper routines on demand; programs that use only addition, subtraction, comparison, and bitwise operations incur no helper overhead.

| Label | Purpose | Triggered by |
|---|---|---|
| `F_MUL` | Signed 32-bit integer multiplication (shift-and-add) | Integer `*` |
| `F_DIV` | Signed 32-bit integer division (truncate toward zero) | Integer `/`; also called by `F_FDIV` |
| `F_MOD` | Signed 32-bit integer modulo (sign follows dividend) | Integer `%`; also called by `F_FDIV` |
| `F_FMUL` | Q16.16 fixed-point multiplication | Float `*` |
| `F_FDIV` | Q16.16 fixed-point division | Float `/` |
| `F_I2F` | `int32` → Q16.16 (logical left shift by 16) | `(float)` cast |
| `F_F2I` | Q16.16 → `int32` (logical right shift by 16) | `(int)` cast |
| `L_BOUNDS_ERROR` | Array-bounds trap; `MOVE -6, R6; HALT` | Jump target emitted inline at checked array accesses |

`L_BOUNDS_ERROR` is a jump target reached by conditional `JP_SLT`/`JP_SGE` pairs emitted inline before each array index computation; it is not a `CALL`-able routine.

All callable helpers (`F_*`) use the same calling convention as user functions: arguments pushed right-to-left before `CALL`, return value in `R6`. Full helper specifications are in [../pipeline/runtime-abi.md](../pipeline/runtime-abi.md).

---

## Stack Frame Layout

```
[higher addresses]
  arg N           (FP + 4*(N+1) + 4)   ; rightmost argument
  ...
  arg 1           (FP + 8)             ; leftmost argument
  return address  (FP + 4)             ; pushed by CALL hardware
  saved FP        (FP + 0)             ; PUSH R5 in prologue; FP points here
  local variables (FP - 4 downward)    ; zero-initialized; localsAreaSize bytes
  IR temporaries  (below locals)       ; 4 bytes each; spill slots for all RHS results
  arg scratch     (below temps)        ; scratch for outgoing argument evaluation
[lower addresses]  <- SP after prologue
```

Frame size is computed as `alignTo(localsAreaSize + tempAreaSize + argScratchSize, 4)`. The locals area is padded to a 4-byte boundary when temporaries or arg scratch are present, preventing byte stores into trailing `char` locals from corrupting adjacent word-aligned temp slots.

---

## Code Sample

The following excerpt is from `examples/real_world/math_fibonacci_iter/a.frisc`, showing the program entry, a function prologue with local-variable zeroing, and a comparison/branch sequence from the loop body:

```
        MOVE 40000, R7          ; Initialize stack pointer (SP)
        CALL F_MAIN             ; Call main
        HALT                    ; Program end
F_MAIN                          ; Function: main
        PUSH R5                 ; Save old FP
        MOVE R7, R5             ; Set FP
        SUB R7, 6C, R7          ; Allocate locals/temps
        MOVE 1B, R1             ; Zero local words (27 words)
        MOVE R7, R0             ; Zero ptr
        MOVE 0, R2              ; Zero
L_ZERO_2
        STORE R2, (R0)          ; Clear
        ADD R0, 4, R0
        SUB R1, 1, R1
        JP_NE L_ZERO_2
        ...
        LOAD R0, (R5-2C)        ; Load temp t5 (loop counter)
        PUSH R0                 ; Save left
        LOAD R0, (R5-34)        ; Load temp t7 (limit)
        MOVE R0, R1             ; Right
        POP R0                  ; Left
        CMP R0, R1
        JP_SLT L_CMP_TRUE_3
        MOVE 0, R0              ; False (0)
        JP L_CMP_END_4
L_CMP_TRUE_3
        MOVE 1, R0              ; True (1)
L_CMP_END_4
        ...
L_EXIT_F_MAIN_1                 ; Function epilogue
        ADD R7, 6C, R7          ; Deallocate locals/temps
        POP R5                  ; Restore FP
        RET                     ; Return
```

---

## ISA-Complete but Unused by FRISCcc

The following instructions are defined by the FRISC ISA and handled by the FRISCjs simulator, but FRISCcc never emits them:

| Instruction(s) | Reason not emitted |
|---|---|
| `SBC` | No signed-borrow idiom in the code generator |
| `ROTL`, `ROTR` | No rotation operators in the source language |
| `JR` (PC-relative jump) | All jumps use absolute labels |
| `LOADH`, `STOREH` | No 16-bit integer type |
| Conditional `CALL`/`RET` | All calls and returns are unconditional |
| `MOVE Rd, SR` / `MOVE SR, Rd` | No direct SR manipulation in the language |
| `JP_N`, `JP_NN`, `JP_C`, `JP_V`, `JP_NV`, `JP_ULT`, `JP_ULE`, `JP_UGT`, `JP_UGE` | Signed comparisons cover all generated comparison patterns |
| `` `ORG ``, `` `EQU ``, `` `BASE `` | No origin/equate/base directives needed |

---

## See Also

- [../pipeline/runtime-abi.md](../pipeline/runtime-abi.md) — full ABI specification, calling convention, helper-routine internals, Q16.16 encoding, `L_BOUNDS_ERROR` protocol
- [../pipeline/codegen.md](../pipeline/codegen.md) — IR-to-FRISC lowering pipeline, instruction selection, peephole optimizer
- [simulator.md](simulator.md) — FRISCjs simulator configuration and register inspection API
