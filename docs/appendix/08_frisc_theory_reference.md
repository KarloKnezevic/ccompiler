# Appendix H. FRISC Architecture Reference

> **📖 From the book.** This chapter accompanies *Building a C-Subset Compiler for the FRISC Architecture: From Formal Languages to Executable Code* by Dr. Karlo Knežević (Zenodo, 2026). For the complete treatment — formal development, proofs, and figures — read the book: [📄 PDF](../book/Building-a-C-Subset-Compiler-for-the-FRISC-Architecture.pdf) · DOI [10.5281/zenodo.20511073](https://doi.org/10.5281/zenodo.20511073) · ISBN 978-953-47198-0-0.


This appendix provides a concise technical reference for the FRISC (FER Reduced
Instruction Set Computer) architecture, which serves as the compilation target
for FRISCcc. FRISC is a 32-bit RISC processor designed at the Faculty of
Electrical Engineering and Computing (FER), University of Zagreb, for teaching
computer architecture and systems programming.

## H.1 Architectural Overview

FRISC is a 32-bit load-store architecture following the RISC design philosophy:
a small, uniform instruction set with fixed-width encoding, a general-purpose
register file, and a von Neumann memory model. All instructions are 32 bits
wide, simplifying the fetch-decode pipeline.

### Register File

The processor provides eight 32-bit general-purpose registers, R0 through R7.
By convention:

| Register | Convention | Description |
|---|---|---|
| R0 | General purpose | Available for computation |
| R1--R4 | General purpose | Available for computation |
| R5 | Frame pointer (FP) | Points to a fixed location in the current stack frame |
| R6 | Return value (RETVAL) | Holds the function return value |
| R7 | Stack pointer (SP) | Points to the top of the runtime stack |

The eight-register file requires 3 bits per register identifier in the
instruction encoding, contributing to the compact 32-bit instruction format.

### Special-Purpose Registers

| Register | Width | Description |
|---|---|---|
| PC | 32 bits | Program counter; holds the address of the next instruction |
| SR | 32 bits | Status register; contains condition flags and interrupt control bits |

The PC is incremented by 4 after each instruction fetch under normal sequential
execution. Control-flow instructions modify the PC to effect branches and calls.

### Status Register Flags

The four condition flags in the SR are set by ALU operations:

| Flag | Name | Set When |
|---|---|---|
| Z | Zero | Result is zero |
| N | Negative | Most significant bit of result is 1 |
| C | Carry | Carry-out from addition, borrow from subtraction, or shifted-out bit |
| V | Overflow | Signed arithmetic overflow (two's complement) |

Additional bits in the SR control the interrupt system (INT0, INT1, INT2 and
their enable bits, plus GIE for global interrupt enable).

## H.2 Memory Model

FRISC uses a von Neumann architecture with a single address space for
instructions and data. Memory is byte-addressable with a 32-bit address bus,
providing a theoretical 4 GB address space. The data bus is 32 bits wide.

**Byte ordering**: little-endian. The least significant byte of a multi-byte
value is stored at the lowest address.

**Alignment**: instructions must reside at 4-byte-aligned addresses. The
`LOAD`/`STORE` instructions mask the lowest two address bits, and
`LOADH`/`STOREH` mask the lowest bit, silently rounding down to the nearest
aligned boundary.

## H.3 Instruction Encoding

All instructions occupy exactly 32 bits, divided into the following fields:

| Bits | Field | Description |
|---|---|---|
| 31--27 | Opcode | 5-bit operation code (up to 32 distinct instructions) |
| 26 | Format | Distinguishes register-register from register-immediate forms |
| 25--23 | Destination register | 3-bit register identifier |
| 22--20 | Source register 1 | 3-bit register identifier |
| 19--0 | Immediate / Source register 2 | 20-bit signed immediate or 3-bit register + unused bits |

The 20-bit immediate field is sign-extended to 32 bits, providing a range of
-524288 to +524287 for immediate values and direct addresses.

## H.4 Addressing Modes

| Mode | Syntax Example | Effective Address |
|---|---|---|
| Register | `ADD R1, R2, R3` | Operand in register |
| Immediate | `MOVE 100, R1` | 20-bit sign-extended constant |
| Absolute | `LOAD R0, (1000)` | 20-bit address |
| Register indirect + displacement | `STORE R1, (R2+30)` | Register + 20-bit signed offset |
| PC-relative | `JR offset` | PC + 20-bit signed offset |

The 20-bit address field limits direct addressing to 1 MB. To access the full
32-bit address space, register-indirect addressing must be used.

## H.5 Instruction Set

### Data Movement

| Instruction | Operation | Cycles |
|---|---|---|
| `MOVE src, Rd` | Rd := src (register or 20-bit immediate) | 1 |
| `MOVE Rd, SR` | SR := Rd (write status register) | 1 |
| `MOVE SR, Rd` | Rd := SR (read status register) | 1 |

### Arithmetic

| Instruction | Operation | Cycles |
|---|---|---|
| `ADD R1, R2, R3` | R3 := R1 + R2 | 1 |
| `SUB R1, R2, R3` | R3 := R1 - R2 | 1 |
| `ADC R1, R2, R3` | R3 := R1 + R2 + C | 1 |
| `SBC R1, R2, R3` | R3 := R1 - R2 - C | 1 |
| `CMP R1, R2` | Compute R1 - R2, set flags, discard result | 1 |

All arithmetic instructions update the condition flags.

### Logic

| Instruction | Operation | Cycles |
|---|---|---|
| `AND R1, R2, R3` | R3 := R1 AND R2 | 1 |
| `OR R1, R2, R3` | R3 := R1 OR R2 | 1 |
| `XOR R1, R2, R3` | R3 := R1 XOR R2 | 1 |

### Shifts and Rotates

| Instruction | Operation | Cycles |
|---|---|---|
| `SHL R1, imm, R3` | R3 := R1 << imm (logical left shift) | 1 |
| `SHR R1, imm, R3` | R3 := R1 >> imm (logical right shift) | 1 |
| `ASHR R1, imm, R3` | R3 := R1 >> imm (arithmetic right shift, sign-extending) | 1 |
| `ROTL R1, imm, R3` | R3 := R1 rotated left by imm positions | 1 |
| `ROTR R1, imm, R3` | R3 := R1 rotated right by imm positions | 1 |

Shift amounts are masked to 5 bits (range 0--31). The shifted-out bit is placed
in the carry flag.

### Memory Access

| Instruction | Width | Operation | Cycles |
|---|---|---|---|
| `LOAD Rd, (addr)` | 32-bit | Rd := MEM[addr & ~3] | 2 |
| `STORE Rs, (addr)` | 32-bit | MEM[addr & ~3] := Rs | 2 |
| `LOADH Rd, (addr)` | 16-bit | Rd := zero-extend(MEM[addr & ~1]) | 2 |
| `STOREH Rs, (addr)` | 16-bit | MEM[addr & ~1] := Rs[15:0] | 2 |
| `LOADB Rd, (addr)` | 8-bit | Rd := zero-extend(MEM[addr]) | 2 |
| `STOREB Rs, (addr)` | 8-bit | MEM[addr] := Rs[7:0] | 2 |

Memory instructions are double-cycle due to the structural hazard: the von
Neumann memory bus is shared between instruction fetch and data access.

### Control Flow

| Instruction | Operation | Cycles |
|---|---|---|
| `JP cond, addr` | If condition holds, PC := addr | 2 |
| `JR cond, offset` | If condition holds, PC := PC + offset | 2 |
| `CALL cond, addr` | Push PC; PC := addr | 2 |
| `RET cond` | Pop PC from stack | 2 |

Condition codes are appended as suffixes to the mnemonic. The unconditional form
uses no suffix (always taken). Common conditions include `_Z` (zero), `_NZ`
(not zero), `_N` (negative), `_NN` (non-negative), `_C` (carry), `_NC` (no
carry), `_V` (overflow), `_NV` (no overflow), `_SLT` (signed less than),
`_SLE` (signed less or equal), `_SGT` (signed greater than), `_SGE` (signed
greater or equal), `_ULT` (unsigned less than), `_ULE` (unsigned less or equal),
`_UGT` (unsigned greater than), and `_UGE` (unsigned greater or equal).

### Stack Operations

| Instruction | Operation | Cycles |
|---|---|---|
| `PUSH Rs` | R7 := R7 - 4; MEM[R7] := Rs | 2 |
| `POP Rd` | Rd := MEM[R7]; R7 := R7 + 4 | 2 |

The stack grows downward (toward lower addresses).

## H.6 Pipeline and Hazards

FRISC uses a two-stage pipeline: fetch and execute. In the ideal case, the fetch
of instruction N+1 overlaps with the execution of instruction N, yielding one
instruction per cycle for single-cycle operations.

**Structural hazards** arise when a memory instruction (LOAD, STORE, PUSH, POP)
occupies the memory bus during its execute stage, preventing the simultaneous
fetch of the next instruction. The pipeline resolves this by inserting a bubble
(stall cycle), making memory instructions two-cycle operations.

**Control hazards** arise when a branch instruction redirects the PC. The
pipeline inserts a bubble to discard the incorrectly fetched instruction,
imposing a one-cycle branch penalty on all taken branches.

**Data hazards** do not occur in the FRISC pipeline. The shallow two-stage
design and register write timing (falling clock edge) ensure that results are
available before the next instruction reads them.

## H.7 Assembler Directives

The FRISCjs assembler supports the following pseudoinstructions:

| Directive | Syntax | Description |
|---|---|---|
| `` `ORG `` | `` `ORG addr `` | Set the assembly origin to the specified address |
| `` `DW `` | `` `DW value `` | Define a 32-bit word in memory |
| `` `DS `` | `` `DS count `` | Reserve `count` bytes of uninitialized storage |
| `` `EQU `` | `` `EQU value `` | Equate the current label to a constant value |
| `` `BASE `` | `` `BASE X `` | Set the default numeric base (B, O, D, or H) |

The default numeric base is hexadecimal. Bare numeric literals without an
explicit base prefix are interpreted in the current default base. The `%D`
prefix forces decimal interpretation for a single literal; `%H` forces
hexadecimal, `%O` forces octal, and `%B` forces binary.

## H.8 Calling Convention

The FRISCcc compiler uses the following calling convention:

**Caller responsibilities:**
1. Push arguments onto the stack in right-to-left order.
2. Execute `CALL` to transfer control to the callee.
3. After return, clean up argument space from the stack.

**Callee responsibilities:**
1. **Prologue**: push saved registers (including R5/FP), set up the frame
   pointer, and allocate local variable space by decrementing SP.
2. **Body**: execute the function logic, accessing parameters and locals via
   FP-relative offsets.
3. **Epilogue**: deallocate local space, restore saved registers, place the
   return value in R6, and execute `RET`.

### Stack Frame Layout

Growing from high to low addresses:

```
[higher addresses]
  argument N        (FP + 4*(N+1))
  ...
  argument 1        (FP + 8)
  return address    (FP + 4)
  saved FP          (FP + 0)   <-- FP points here
  saved registers
  local variables
  spill slots
[lower addresses]   <-- SP points here
```

## H.9 Implications for Code Generation

The FRISC architecture imposes several constraints on the FRISCcc code generator:

1. **No hardware multiply or divide.** These operations must be implemented as
   software helper routines using shift-and-add (multiplication) and restoring
   or non-restoring algorithms (division).

2. **Limited register file.** Eight general-purpose registers, with R5, R6, and
   R7 reserved by convention, leave only five registers for general computation.
   Register pressure is high, requiring careful allocation and frequent spilling.

3. **20-bit immediate limit.** Constants and addresses exceeding 20 bits must
   be loaded via multi-instruction sequences or memory-resident constants.

4. **Branch penalty.** Every taken branch costs one extra cycle. The code
   generator should minimize unnecessary branches and prefer fall-through paths
   for common cases.

5. **Memory alignment.** All word-sized loads and stores must target 4-byte
   aligned addresses. The code generator must ensure that stack frame layouts
   and global variable placements respect this constraint.
