# FRISC Architecture Reference

The FRISC (Faculty RISC) processor is a 32-bit RISC architecture designed for educational purposes, featuring a clean instruction set, simple addressing modes, and comprehensive interrupt support. This document provides a complete technical reference for the FRISC architecture as implemented in the PPJ compiler's code generation phase.

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Register Set](#register-set)
- [Memory Model](#memory-model)
- [Instruction Set](#instruction-set)
- [Instruction Formats](#instruction-formats)
- [Addressing Modes](#addressing-modes)
- [Status Register and Condition Codes](#status-register-and-condition-codes)
- [Stack and Subroutines](#stack-and-subroutines)
- [Interrupt System](#interrupt-system)
- [Assembly Directives](#assembly-directives)
- [I/O and Memory Mapping](#io-and-memory-mapping)

## Architecture Overview

FRISC is a 32-bit RISC processor with the following key characteristics:

### Core Features
- **Word Size**: 32 bits (4 bytes)
- **Instruction Width**: Fixed 32-bit instruction format
- **Address Space**: 32-bit addresses supporting up to 4 GB of memory
- **Endianness**: Little-endian byte ordering
- **Architecture Type**: Load/store RISC with simple addressing modes
- **Memory Organization**: Byte-addressable with word-aligned instruction access

### Design Philosophy
The FRISC architecture follows classic RISC principles:
- **Simple Instructions**: Fixed-format instructions with regular encoding
- **Load/Store Architecture**: Memory access only through dedicated instructions
- **Register-Rich**: 8 general-purpose registers for efficient computation
- **Orthogonal Design**: Consistent instruction behavior across operations
- **Minimal Addressing Modes**: Simple, predictable memory access patterns

## Register Set

### General-Purpose Registers

FRISC provides 8 general-purpose registers, each 32 bits wide:

```
R0 - R7: General-purpose registers (32 bits each)
```

**Register Conventions**:
- **R0-R6**: Available for general computation and data storage
- **R7**: Conventionally used as Stack Pointer (SP)
- **R6**: Conventionally used for function return values

### Special-Purpose Registers

#### Program Counter (PC)
- **Width**: 32 bits
- **Function**: Contains address of the next instruction to execute
- **Behavior**: Automatically incremented by 4 after instruction fetch
- **Access**: Implicitly modified by control flow instructions

#### Status Register (SR)
- **Width**: 32 bits
- **Function**: Contains processor status flags and control bits

**Status Register Layout**:
```
Bit 31-5: Reserved (0)
Bit 4: GIE (Global Interrupt Enable)
Bit 3: N (Negative flag)
Bit 2: Z (Zero flag)
Bit 1: V (Overflow flag)
Bit 0: C (Carry flag)
```

**Flag Descriptions**:
- **C (Carry)**: Set on arithmetic carry/borrow operations
- **V (Overflow)**: Set on signed arithmetic overflow
- **Z (Zero)**: Set when result equals zero
- **N (Negative)**: Set when result is negative (MSB = 1)
- **GIE (Global Interrupt Enable)**: Controls maskable interrupt acceptance

### Internal Registers

These registers are not directly accessible but are important for understanding instruction execution:

- **IR (Instruction Register)**: Holds currently executing instruction
- **DR (Data Register)**: Temporary storage for memory operations
- **AR (Address Register)**: Temporary address calculation storage
- **EXT (Extension Register)**: Sign-extends 20-bit immediate values to 32 bits

## Memory Model

### Address Space Organization

```
0x00000000 ┌─────────────────────┐
           │   Program Memory    │ ← Instructions and initialized data
           │                     │
0x00001000 ├─────────────────────┤
           │   Data Memory       │ ← Global variables and constants
           │                     │
0x00010000 ├─────────────────────┤
           │   Heap (unused)     │ ← Available for dynamic allocation
           │                     │
0x3FFF0000 ├─────────────────────┤
           │   Stack Memory      │ ← Growing downward from 0x40000
           │                     │
0x40000000 ├─────────────────────┤
           │   I/O Mapped        │ ← Memory-mapped I/O devices
           │   Devices           │
0xFFFFFFFF └─────────────────────┘
```

### Memory Access Properties

**Word Alignment**:
- Instructions must be word-aligned (addresses divisible by 4)
- LOAD/STORE operations work with 32-bit words
- Automatic alignment for word operations (lower 2 bits masked to 0)

**Data Types**:
- **Word**: 32 bits (4 bytes) - primary data unit
- **Halfword**: 16 bits (2 bytes) - supported by LOADH/STOREH
- **Byte**: 8 bits (1 byte) - supported by LOADB/STOREB

**Addressing Range**:
- **Direct Addressing**: ±512 KB range (20-bit signed immediate)
- **Full Range Access**: Via register indirect addressing
- **Stack Recommendation**: Initialize at 0x40000 (262,144₁₀)

## Instruction Set

### Arithmetic and Logic Instructions

#### Basic Arithmetic
```assembly
ADD  src1, src2, dest    ; dest = src1 + src2
SUB  src1, src2, dest    ; dest = src1 - src2
ADC  src1, src2, dest    ; dest = src1 + src2 + C
CMP  src1, src2          ; Compare (src1 - src2), set flags only
```

#### Bitwise Operations
```assembly
AND  src1, src2, dest    ; dest = src1 & src2
OR   src1, src2, dest    ; dest = src1 | src2
XOR  src1, src2, dest    ; dest = src1 ^ src2
```

#### Shift and Rotate
```assembly
SHL  src1, src2, dest    ; dest = src1 << src2 (logical left)
SHR  src1, src2, dest    ; dest = src1 >> src2 (logical right)
ASHR src1, src2, dest    ; dest = src1 >> src2 (arithmetic right)
ROTL src1, src2, dest    ; dest = rotate left
ROTR src1, src2, dest    ; dest = rotate right
```

### Data Movement Instructions

#### Register Operations
```assembly
MOVE src, dest           ; dest = src
MOVE SR, dest           ; dest = SR (read status register)
MOVE src, SR            ; SR = src (write status register)
```

#### Memory Operations
```assembly
LOAD  dest, (addr)      ; dest = memory[addr] (32-bit word)
STORE src, (addr)       ; memory[addr] = src (32-bit word)
LOADH dest, (addr)      ; dest = memory[addr] (16-bit halfword)
STOREH src, (addr)      ; memory[addr] = src (16-bit halfword)
LOADB dest, (addr)      ; dest = memory[addr] (8-bit byte)
STOREB src, (addr)      ; memory[addr] = src (8-bit byte)
```

#### Stack Operations
```assembly
PUSH src                ; SP = SP - 4; memory[SP] = src
POP  dest               ; dest = memory[SP]; SP = SP + 4
```

### Control Flow Instructions

#### Unconditional Jumps
```assembly
JP   addr               ; PC = addr (absolute jump)
JR   offset             ; PC = PC + offset (relative jump)
CALL addr               ; Push PC; PC = addr (subroutine call)
RET                     ; PC = Pop() (return from subroutine)
```

#### Conditional Jumps
```assembly
JP_EQ  addr             ; Jump if Z = 1 (equal)
JP_NE  addr             ; Jump if Z = 0 (not equal)
JP_LT  addr             ; Jump if N ≠ V (less than, signed)
JP_LE  addr             ; Jump if Z = 1 OR N ≠ V (less or equal, signed)
JP_GT  addr             ; Jump if Z = 0 AND N = V (greater than, signed)
JP_GE  addr             ; Jump if N = V (greater or equal, signed)
JP_ULT addr             ; Jump if C = 1 (unsigned less than)
JP_ULE addr             ; Jump if C = 1 OR Z = 1 (unsigned less or equal)
JP_UGT addr             ; Jump if C = 0 AND Z = 0 (unsigned greater than)
JP_UGE addr             ; Jump if C = 0 (unsigned greater or equal)
```

#### System Control
```assembly
HALT                    ; Stop processor execution
HALT_cond               ; Conditional halt based on flags
RETI                    ; Return from maskable interrupt
RETN                    ; Return from non-maskable interrupt
```

## Instruction Formats

### ALU/Register Instructions

**Format without immediate**:
```
31    27 26 25    23 22    20 19    17 16                    0
┌───────┬──┬───────┬───────┬───────┬─────────────────────────┐
│ opcode│ad│ dest  │ src1  │ src2  │        unused (0)       │
└───────┴──┴───────┴───────┴───────┴─────────────────────────┘
```

**Format with immediate**:
```
31    27 26 25    23 22    20 19                            0
┌───────┬──┬───────┬───────┬─────────────────────────────────┐
│ opcode│1 │ dest  │ src1  │        20-bit immediate         │
└───────┴──┴───────┴───────┴─────────────────────────────────┘
```

**Fields**:
- **opcode** (5 bits): Instruction operation code
- **adr** (1 bit): Addressing mode (0=register, 1=immediate)
- **dest** (3 bits): Destination register (0-7)
- **src1** (3 bits): First source register (0-7)
- **src2** (3 bits): Second source register (0-7, when adr=0)
- **immediate** (20 bits): Signed immediate value (when adr=1)

### Memory Instructions

```
31    27 26 25    23 22    20 19                            0
┌───────┬──┬───────┬───────┬─────────────────────────────────┐
│ opcode│0 │  reg  │ base  │        20-bit offset/addr       │
└───────┴──┴───────┴───────┴─────────────────────────────────┘
```

**Fields**:
- **opcode** (5 bits): Memory operation code
- **reg** (3 bits): Data register (source for STORE, dest for LOAD)
- **base** (3 bits): Base register for indirect addressing
- **offset/addr** (20 bits): Address offset or absolute address

### Control Flow Instructions

```
31    27 26 25    22 21 20 19                            0
┌───────┬──┬───────┬──┬──┬─────────────────────────────────┐
│ opcode│0 │ cond  │ 0│0 │        20-bit address           │
└───────┴──┴───────┴──┴──┴─────────────────────────────────┘
```

**Fields**:
- **opcode** (5 bits): Control flow operation code
- **cond** (4 bits): Condition code for conditional operations
- **address** (20 bits): Target address or relative offset

## Addressing Modes

### Register Addressing
Direct register access for operands:
```assembly
ADD R0, R1, R2          ; R2 = R0 + R1
```

### Immediate Addressing
Constant values embedded in instruction:
```assembly
MOVE 100, R0            ; R0 = 100
ADD R0, 50, R1          ; R1 = R0 + 50
```
- **Range**: -524,288 to +524,287 (20-bit signed)
- **Extension**: Automatically sign-extended to 32 bits

### Absolute Addressing
Direct memory address specification:
```assembly
LOAD R0, (1000)         ; R0 = memory[1000]
STORE R1, (2000)        ; memory[2000] = R1
```
- **Range**: ±512 KB from address 0

### Register Indirect with Offset
Base register plus constant offset:
```assembly
LOAD R0, (R1+4)         ; R0 = memory[R1 + 4]
STORE R2, (R7-8)        ; memory[R7 - 8] = R2
```
- **Offset Range**: -524,288 to +524,287 bytes

### Register Indirect
Address contained in register (control flow only):
```assembly
JP (R0)                 ; PC = R0
CALL (R1)               ; Call subroutine at address in R1
```

### Relative Addressing
PC-relative addressing (JR instructions only):
```assembly
JR LOOP                 ; PC = PC + offset_to_LOOP
JR_Z END                ; Conditional relative jump
```

### Implicit Addressing
Built-in operand assumptions:
```assembly
PUSH R0                 ; Uses R7 (SP) implicitly
POP R1                  ; Uses R7 (SP) implicitly
RET                     ; Uses stack and PC implicitly
```

## Status Register and Condition Codes

### Flag Setting Rules

**Arithmetic Instructions** (ADD, SUB, ADC, CMP):
- **C**: Set on unsigned overflow/underflow
- **V**: Set on signed overflow/underflow
- **Z**: Set if result is zero
- **N**: Set if result is negative (bit 31 = 1)

**Logical Instructions** (AND, OR, XOR):
- **C**: Cleared (0)
- **V**: Cleared (0)
- **Z**: Set if result is zero
- **N**: Set if result is negative

**Shift Instructions** (SHL, SHR, ASHR):
- **C**: Last bit shifted out
- **V**: Undefined for shifts
- **Z**: Set if result is zero
- **N**: Set if result is negative

### Condition Code Mapping

| Condition | Code | Flags Test | Description |
|-----------|------|------------|-------------|
| Always    | 0000 | -          | Unconditional |
| EQ        | 0001 | Z = 1      | Equal |
| NE        | 0010 | Z = 0      | Not Equal |
| LT        | 0011 | N ≠ V      | Less Than (signed) |
| LE        | 0100 | Z=1 OR N≠V | Less or Equal (signed) |
| GT        | 0101 | Z=0 AND N=V| Greater Than (signed) |
| GE        | 0110 | N = V      | Greater or Equal (signed) |
| ULT       | 0111 | C = 1      | Unsigned Less Than |
| ULE       | 1000 | C=1 OR Z=1 | Unsigned Less or Equal |
| UGT       | 1001 | C=0 AND Z=0| Unsigned Greater Than |
| UGE       | 1010 | C = 0      | Unsigned Greater or Equal |
| N         | 1011 | N = 1      | Negative |
| NN        | 1100 | N = 0      | Not Negative |
| V         | 1101 | V = 1      | Overflow |
| NV        | 1110 | V = 0      | No Overflow |
| Reserved  | 1111 | -          | Reserved |

## Stack and Subroutines

### Stack Organization

FRISC uses a **full descending stack** model:
- Stack grows toward lower addresses
- R7 serves as Stack Pointer (SP)
- Stack operations work with 32-bit words only

### Stack Operations

#### PUSH Operation
```
1. SP = SP - 4
2. memory[SP] = data
```

#### POP Operation
```
1. data = memory[SP]
2. SP = SP + 4
```

### Subroutine Calling Convention

#### CALL Instruction Sequence
```
1. SP = SP - 4          ; Allocate stack space
2. memory[SP] = PC      ; Save return address
3. PC = target_address  ; Jump to subroutine
```

#### RET Instruction Sequence
```
1. PC = memory[SP]      ; Restore return address
2. SP = SP + 4          ; Deallocate stack space
```

### Function Call Example

**Caller Code**:
```assembly
; Prepare arguments
MOVE arg1, R0
PUSH R0                 ; Push argument 1
MOVE arg2, R0
PUSH R0                 ; Push argument 2

; Call function
CALL FUNCTION

; Clean up stack (caller responsibility)
ADD R7, 8, R7          ; Remove 2 arguments (8 bytes)

; Result is in R6
MOVE R6, result_var
```

**Callee Code**:
```assembly
FUNCTION:
    ; Save registers (callee responsibility)
    PUSH R1
    PUSH R2
    
    ; Access parameters
    LOAD R1, (R7+12)    ; arg1 at SP+12 (saved regs + return addr)
    LOAD R2, (R7+16)    ; arg2 at SP+16
    
    ; Function body
    ADD R1, R2, R6      ; Compute result in R6
    
    ; Restore registers
    POP R2
    POP R1
    
    ; Return
    RET
```

## Interrupt System

### Interrupt Types

FRISC supports two interrupt levels:

#### Maskable Interrupts (INT0)
- **Control**: GIE flag in Status Register
- **Vector**: Address stored at memory location 8
- **Priority**: Lower priority, can be disabled

#### Non-Maskable Interrupts (NMI/INT1)
- **Control**: Internal IIF flag (not in SR)
- **Vector**: Fixed address 0x0C16
- **Priority**: Higher priority, cannot be disabled

### Interrupt Processing

#### Maskable Interrupt (INT0)
```
1. Check: GIE = 1? (if not, ignore interrupt)
2. GIE = 0 (disable further maskable interrupts)
3. SP = SP - 4; memory[SP] = PC (save return address)
4. PC = memory[8] (jump to interrupt handler)
```

#### Non-Maskable Interrupt (NMI)
```
1. Check: IIF = 1? (if not, ignore interrupt)
2. IIF = 0 (disable further NMI interrupts)
3. SP = SP - 4; memory[SP] = PC (save return address)
4. PC = 0x0C16 (jump to fixed NMI handler)
```

### Interrupt Return Instructions

#### RETI (Return from Interrupt)
```
1. PC = memory[SP]; SP = SP + 4 (restore return address)
2. GIE = 1 (re-enable maskable interrupts)
```

#### RETN (Return from Non-maskable)
```
1. PC = memory[SP]; SP = SP + 4 (restore return address)
2. IIF = 1 (re-enable non-maskable interrupts)
```

### Interrupt Handler Template

```assembly
; Interrupt handler entry point
INT_HANDLER:
    ; Save processor context
    PUSH R0
    PUSH R1
    PUSH R2
    PUSH R3
    PUSH R4
    PUSH R5
    PUSH R6
    MOVE SR, R0
    PUSH R0             ; Save status register
    
    ; Identify interrupt source
    LOAD R0, (DEVICE_STATUS)
    
    ; Service the interrupt
    ; ... interrupt-specific code ...
    
    ; Signal interrupt acknowledgment
    MOVE 1, R0
    STORE R0, (DEVICE_IACK)
    
    ; Restore processor context
    POP R0
    MOVE R0, SR         ; Restore status register
    POP R6
    POP R5
    POP R4
    POP R3
    POP R2
    POP R1
    POP R0
    
    ; Return from interrupt
    RETI                ; or RETN for NMI
```

## Assembly Directives

### Memory Organization Directives

#### ORG (Origin)
Sets the current location counter for code/data placement:
```assembly
ORG 0                   ; Start at address 0
    MOVE 40000, R7      ; Initialize stack pointer
    JP MAIN             ; Jump to main program

ORG 8                   ; Interrupt vector location
    DW INT_HANDLER      ; Address of interrupt handler

ORG 1000               ; Program code starts at 0x1000
MAIN:
    ; Main program code
```

### Data Definition Directives

#### DW (Define Word)
Defines 32-bit word constants:
```assembly
CONSTANT1   DW %D 100      ; Decimal constant
CONSTANT2   DW %H 0x1234   ; Hexadecimal constant
ARRAY       DW %D 1, 2, 3, 4, 5  ; Array of words
```

#### DH (Define Halfword)
Defines 16-bit halfword constants:
```assembly
SHORT_VAL   DH %D 1000     ; 16-bit value
```

#### DB (Define Byte)
Defines 8-bit byte constants:
```assembly
BYTE_VAL    DB %D 255      ; 8-bit value
STRING      DB "Hello", 0  ; Null-terminated string
```

#### DS (Define Space)
Reserves uninitialized memory space:
```assembly
BUFFER      DS 100         ; Reserve 100 bytes
ARRAY_SPACE DS 40          ; Reserve 40 bytes (10 words)
```

### Symbol Definition Directives

#### EQU (Equate)
Defines symbolic constants:
```assembly
STACK_SIZE  EQU 1000       ; Stack size constant
DEVICE_ADDR EQU 0xFFFF0000 ; Device base address
MAX_COUNT   EQU 255        ; Maximum counter value

; Usage in code
MOVE STACK_SIZE, R0
LOAD R1, (DEVICE_ADDR)
```

### Program Structure Template

```assembly
; Program entry point
ORG 0
    MOVE 40000, R7          ; Initialize stack pointer
    CALL F_MAIN             ; Call main function
    HALT                    ; End program

; Interrupt vectors
ORG 8
    DW INTERRUPT_HANDLER    ; Maskable interrupt vector

; Main program
ORG 100
F_MAIN:
    ; Function prolog
    SUB R7, 8, R7          ; Allocate local variables
    
    ; Function body
    MOVE 42, R0            ; Load constant
    STORE R0, (R7+4)       ; Store to local variable
    
    ; Function epilog
    LOAD R6, (R7+4)        ; Load return value
    ADD R7, 8, R7          ; Deallocate locals
    RET                    ; Return

; Global variables
ORG 1000
GLOBAL_VAR  DW %D 0        ; Global integer variable
ARRAY_VAR   DW %D 1, 2, 3, 4, 5  ; Global array

; Constants
PI_APPROX   EQU 314        ; π × 100
BUFFER_SIZE EQU 256        ; Buffer size constant
```

## I/O and Memory Mapping

### Memory-Mapped I/O

FRISC uses memory-mapped I/O for peripheral device access. Devices are typically mapped to high memory addresses (0xFFFF0000 and above).

### Common Device Mappings

#### GPIO (General Purpose I/O)
```
Base Address: 0xFFFF0000
Registers:
  PIOC (0xFFFF0000) - Control register
  PIOD (0xFFFF0004) - Data register  
  PIOIACK (0xFFFF0008) - Interrupt acknowledge
  PIOIEND (0xFFFF000C) - Interrupt end
```

#### Counter/Timer
```
Base Address: 0xFFFF1000
Registers:
  CTCR (0xFFFF1000) - Control register
  CTLR (0xFFFF1004) - Load register
  CTIACK (0xFFFF1008) - Interrupt acknowledge
  CTIEND (0xFFFF100C) - Interrupt end
```

### Device Access Example

```assembly
; GPIO device constants
PIOC    EQU 0xFFFF0000     ; GPIO control register
PIOD    EQU 0xFFFF0004     ; GPIO data register

; Configure GPIO
MOVE 0xFF, R0              ; Set all pins as output
STORE R0, (PIOC)           ; Write to control register

; Write data to GPIO
MOVE 0xAA, R0              ; Data pattern
STORE R0, (PIOD)           ; Write to data register

; Read data from GPIO
LOAD R1, (PIOD)            ; Read current GPIO state
```

### Interrupt-Driven I/O

```assembly
; Timer interrupt handler
TIMER_ISR:
    PUSH R0
    PUSH R1
    
    ; Acknowledge timer interrupt
    MOVE 1, R0
    STORE R0, (CTIACK)
    
    ; Service timer (toggle LED)
    LOAD R1, (PIOD)
    XOR R1, 0x01, R1       ; Toggle bit 0
    STORE R1, (PIOD)
    
    ; End interrupt service
    MOVE 1, R0
    STORE R0, (CTIEND)
    
    POP R1
    POP R0
    RETI
```

This comprehensive reference covers all aspects of the FRISC architecture necessary for understanding and implementing code generation for the PPJ compiler. The architecture's simplicity and regularity make it an excellent target for educational compiler construction while providing sufficient features for meaningful program execution.
