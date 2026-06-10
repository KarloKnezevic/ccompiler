# Appendix I. FRISC Simulator Reference

> **📖 From the book.** This chapter accompanies *Building a C-Subset Compiler for the FRISC Architecture: From Formal Languages to Executable Code* by Dr. Karlo Knežević (Zenodo, 2026). For the complete treatment — formal development, proofs, and figures — read the book: [📄 PDF](../book/Building-a-C-Subset-Compiler-for-the-FRISC-Architecture.pdf) · DOI [10.5281/zenodo.20511073](https://doi.org/10.5281/zenodo.20511073) · ISBN 978-953-47198-0-0.


This appendix documents the FRISCjs simulator used by the FRISCcc project to
execute generated FRISC assembly programs. The simulator is an interpretive
execution engine implemented in JavaScript, consisting of an assembler frontend,
a memory subsystem, and a CPU emulation core. Two execution modes are available:
the upstream FRISCjs console application and the project-specific Java-hosted
runner (`FriscRunner`).

## I.1 Architecture Overview

The simulator is organized into three layers:

1. **Assembler frontend** (`asm.parse`): converts FRISC assembly source text
   into a binary memory image, resolving labels and pseudoinstructions.
2. **Memory and CPU runtime** (`MEM` and `CPU` objects): provides the byte-
   addressable memory model and the instruction execution engine.
3. **Host runner**: either the upstream `frisc-console.js` (timer-driven) or the
   project-specific `FriscRunner.java` (synchronous step loop).

The execution pipeline is:

```
Assembly text -> asm.parse -> Binary image -> MEM.loadBinaryString -> CPU.performCycle loop -> Final state
```

## I.2 CPU State Model

### Registers

The CPU register file (`CPU._r`) contains:

| Register | Description |
|---|---|
| `r0`--`r7` | General-purpose 32-bit registers |
| `pc` | Program counter |
| `sr` | Status register |
| `iif` | Interrupt-in-flight control flag |

By the FRISCcc calling convention, R7 is the stack pointer, R5 is the frame
pointer, and R6 holds the return value.

### Status Flags

The status register contains the condition flags Z, N, C, and V, plus
interrupt-related bits (INT0, INT1, INT2, EINT0, EINT1, EINT2, GIE). ALU
operations update the condition flags directly after each result computation.
Shift operations set the carry flag to the shifted-out bit and mask the shift
amount with 0x1F.

Conditional control-flow instructions (`JP_*`, `JR_*`, `CALL_*`, `RET_*`)
evaluate their conditions through the internal `_testCond(cond)` function using
these flags.

## I.3 Memory Model

### Address Space

The default simulator memory size is 256 KB (256 * 1024 bytes), configurable
before loading the program. The project's `FriscRunner` uses 1000 KB by default.
Memory is byte-addressable and little-endian.

### Access Widths

| Function | Width | Description |
|---|---|---|
| `readb` / `writeb` | 8-bit | Byte access |
| `readw` / `writew` | 16-bit | Halfword access |
| `read` / `write` | 32-bit | Word access |

All multi-byte accesses pack and unpack bytes in little-endian order.

### Alignment Behavior

The simulator applies alignment masks to memory addresses:

- `LOAD`/`STORE`: address masked with `& ~0x03` (4-byte aligned).
- `LOADH`/`STOREH`: address masked with `& ~0x01` (2-byte aligned).
- `LOADB`/`STOREB`: no alignment masking.

Unaligned word and halfword accesses are silently rounded down to the nearest
aligned boundary.

### Bounds Checking

The `loadBinaryString` function checks that the program image fits in memory.
Runtime read and write operations do not perform strict per-access bounds
checking. Out-of-bounds addressing produces silent corruption or undefined
host-level behavior.

## I.4 Execution Cycle

The core execution engine is the `CPU.performCycle()` method, which performs the
following steps in order:

1. **Fetch**: read the 32-bit instruction word from `MEM[PC]`.
2. **Decode**: extract opcode, registers, immediate, and condition fields.
3. **Execute**: perform the operation (ALU computation, memory access, or
   control-flow transfer).
4. **PC advance**: unconditionally increment PC by 4.
5. **Interrupt check**: evaluate pending interrupts.

Control-transfer instructions (JP, JR, CALL, RET) compensate for the
unconditional `PC += 4` by writing `target_aligned - 4` to the PC during the
execute step. The post-execute increment then brings PC to the correct target
address.

### CALL Execution

1. Decrement R7 by 4.
2. Store the current aligned PC at `MEM[R7]`.
3. Set `PC = aligned(destination) - 4`.
4. Post-execute: PC += 4 (yielding the correct target).

### RET Execution

1. Load the return address from `MEM[R7]` and align it.
2. Increment R7 by 4.
3. Post-execute: PC += 4 (yielding the instruction after the call site).

## I.5 Execution Modes

### FRISCjs Console Application

The upstream `frisc-console.js` runs execution using `CPU.run()`, which
schedules `performCycle()` via `setInterval` at a configurable frequency. This
timer-driven approach throttles execution to the specified CPU frequency
(default: 1000 Hz) and is suitable for interactive use and I/O-driven programs.

### Project FriscRunner

The project's `FriscRunner.java` executes synchronously in a tight loop:

```
while (!halted && steps < stepLimit) {
    simulator.CPU.performCycle();
    steps += 1;
}
```

This mode bypasses timer pacing and is significantly faster than the console
application for batch compilation testing. It enforces three safeguards:

| Parameter | Default | Description |
|---|---|---|
| Process timeout | 120 seconds | Java-side wall-clock timeout |
| Step limit | 200,000,000 | Maximum `performCycle()` invocations |
| Memory size | 1000 KB | Simulator memory capacity |

## I.6 Numeric Literal Conventions

The FRISCjs assembler initializes the default numeric base to hexadecimal (base
16). This has important implications for the code generator:

- `MOVE 40000, R7` means hex 0x40000 (262144 decimal), not decimal 40000.
- Decimal intent requires the `%D` prefix: `MOVE %D 40000, R7`.

The `` `BASE D `` directive changes the default base to decimal for all
subsequent unprefixed literals. The FRISCcc code generator emits `%D` prefixes
on all decimal literals to avoid ambiguity.

## I.7 Timing Model

The simulator does not model pipeline hazards, forwarding, bubbles, or cache
timing. Each call to `performCycle()` corresponds to one decoded instruction
dispatch. The two-cycle cost of memory and branch instructions in the real FRISC
architecture is not reflected in the simulator's step count; each instruction
consumes exactly one step regardless of type.

Instruction hooks (`onBeforeCycle`, `onBeforeExecute`, `onAfterCycle`) provide
tracing points but do not alter execution semantics.

## I.8 CLI Parameters

### FRISCjs Console Application

| Flag | Type | Default | Description |
|---|---|---|---|
| `-v` | Boolean | false | Enable verbose per-cycle trace output |
| `-cpufreq N` | Integer | 1000 | Timer frequency in Hz for `CPU.run()` |
| `-memsize N` | Integer | 256 | Memory size in KB |

Example invocations:

```bash
node frisc-console.js program.frisc
node frisc-console.js -memsize 1000 program.frisc
node frisc-console.js -v -cpufreq 2000 program.frisc
```

### Project FriscRunner

The `FriscRunner` uses internal defaults (120s timeout, 200M step limit, 1000 KB
memory) and does not expose CLI flags for these parameters. The `-cpufreq` flag
has no effect when using `FriscRunner`, because execution bypasses the
timer-driven `CPU.run()` method.

## I.9 Debugging and Diagnostics

### Common Failure Modes

| Symptom | Likely Cause | Resolution |
|---|---|---|
| Timeout with no output | Infinite loop in generated code | Inspect PC histogram for hot addresses; check loop induction variables |
| Step limit exceeded | Same as above, or workload too large | Verify loop termination; increase limit only after confirming convergence |
| Wrong return value in R6 | Literal base mismatch, branch inversion, or stack corruption | Compare IR interpreter result with FRISC result; inspect literal formatting |
| Immediate values appear wrong | Decimal constant parsed as hexadecimal | Emit `%D` prefix for all decimal literals |
| Memory too small error | Program image exceeds memory capacity | Increase `-memsize` or reduce static data |

### Dual-Execution Validation

The most reliable diagnostic approach is dual execution:

1. Run the program through the IR interpreter (`run-ir` CLI mode) to validate
   semantics independently of the FRISC backend.
2. Run the program through FRISC code generation and simulation.
3. Compare the return values. If they disagree, the bug is in code generation.
   If the IR interpreter also produces incorrect results, the bug is in the
   frontend or optimization passes.

### Instrumentation

For timeout investigation, the following metrics can be collected by
instrumenting the step loop:

- **Step count**: total instructions executed.
- **PC histogram**: execution frequency by program counter value.
- **Edge histogram**: control-flow edge frequency (previous PC to current PC).
- **Same-PC streak**: consecutive cycles at the same PC (stuck detector).
- **Stack pointer trace**: periodic SP snapshots to detect frame imbalance.

These metrics localize the root cause to a specific loop, branch, or helper
routine without requiring modifications to the FRISCjs source code.
