# FRISC Simulator Integration

FRISCcc executes generated FRISC assembly on the bundled JavaScript simulator `friscjs` (Node.js), driven from `FriscRunner` in `cli/src/main/java/hr/fer/ppj/cli/FriscRunner.java`.

---

## Prerequisites

- **Node.js** must be on `PATH`. The integration is tested against Node.js v18+; any LTS release from v18 onward is supported.
- **Java 21+** for the compiler itself.
- **friscjs is vendored** under `node_modules/friscjs/`. No `npm install` is required; the directory is committed to the repository. `FriscRunner` resolves it relative to the working directory as `node_modules/friscjs/lib/index.js`.

---

## Execution Flow

```mermaid
sequenceDiagram
    participant CLI as CLI / run.sh
    participant CG as FriscCodeGenerator
    participant FS as compiler-bin/a.out
    participant FR as FriscRunner (Java)
    participant Node as node (inline script)
    participant Sim as friscjs simulator
    participant Out as program output (R6)

    CLI->>CG: --all --run source.c
    CG->>FS: write FRISC assembly
    CLI->>FR: FriscRunner.run(a.out)
    FR->>Node: ProcessBuilder: node -e STEP_RUNNER_SCRIPT lib a.out 1000 200000000
    Node->>Sim: require(friscjs lib)
    Node->>Sim: asm.parse(source)
    Sim-->>Node: binary memory image
    Node->>Sim: new Simulator(); MEM._size = 1000*1024
    Node->>Sim: MEM.loadBinaryString(image)
    loop until HALT or step limit (200,000,000)
        Node->>Sim: CPU.performCycle()
    end
    Sim-->>Node: CPU.onStop() on HALT
    Node-->>FR: stdout: decimal R6 value, exit 0
    FR-->>CLI: Result.success(r6Value)
    CLI-->>Out: print R6
```

### Step-by-step description

1. The compiler (or `run.sh --all --run`) generates FRISC assembly into `compiler-bin/a.out` via `FriscCodeGenerator`.
2. `FriscRunner.run(Path)` is called. It validates that both `a.out` and `node_modules/friscjs/lib/index.js` exist relative to the working directory; if either is missing it returns `Result.failure` immediately.
3. A `ProcessBuilder` launches `node -e <inline-script> <lib-path> <asm-path> <memKb> <stepLimit>`. The inline script is the constant `STEP_RUNNER_SCRIPT` embedded in `FriscRunner.java`.
4. Inside Node.js, `friscjs` is loaded via `require`, the assembly file is read and assembled (`asm.parse`), and a fresh `Simulator` is created with `MEM._size = 1000 * 1024` bytes.
5. The CPU runs in a synchronous `while (!halted && steps < stepLimit)` loop calling `CPU.performCycle()` each iteration.
6. On `HALT`, `CPU.onStop` fires, the loop exits, and `console.log(String(simulator.CPU._r.r6))` emits the decimal value of R6 to stdout.
7. Java reads all stdout, scans lines from the end for a pattern matching `^-?\d+$`, and returns `Result.success(r6Value, fullOutput)`.

---

## Invocation

### Via run.sh

```bash
# Full pipeline: compile, optimize, codegen, simulate
./run.sh --all --run examples/real_world/math_fibonacci_iter/program.c

# With O1 optimization
./run.sh --all --O1 --run examples/real_world/math_fibonacci_iter/program.c
```

Must be run from the **repo root** so that `node_modules/friscjs/lib/index.js` resolves correctly relative to the working directory. `run.sh` `cd`s to `SCRIPT_DIR` (the repo root) unconditionally.

### Via JAR directly

```bash
java -jar cli/target/ccompiler.jar --all --run examples/real_world/math_fibonacci_iter/program.c
```

When invoking the JAR directly, the current working directory must be the repo root; `FriscRunner` defaults its working directory to `Paths.get("").toAbsolutePath()`.

### Example output

```
$ ./run.sh --all --run examples/real_world/math_fibonacci_iter/program.c 2>&1 | tail -6
Status: OK (347 ms)

Program output:
6765

✓ Command completed successfully
```

The program output line (`6765`) is the decimal value of register R6 after the simulated program halts. For `math_fibonacci_iter`, F(20) = 6765.

---

## Memory and Register Initialization

All simulator registers start at zero. The FRISCcc-generated entry stub immediately executes:

```
MOVE %D 40000, R7
```

This sets R7 (stack pointer) to decimal 40000 (hex `0x9C40`). With the 1000 KB memory configured by `FriscRunner`, this leaves approximately 40 KB for the stack growing downward from `0x9C40` toward address 0, while the code section occupies low memory growing upward.

**ABI register roles at simulation start:**

| Register | ABI role | Initial value |
|----------|----------|---------------|
| R7 (SP)  | Stack pointer | 40000 (decimal) via entry stub |
| R5 (FP)  | Frame pointer | Set by `F_MAIN` prologue |
| R6       | Return value | 0; written by callee before `RET` |
| R0–R4    | Caller-saved scratch | 0 |

> **Numeric literal note.** The FRISC assembler defaults to hexadecimal. The entry stub uses `%D 40000` (the `%D` prefix forces decimal), placing SP at decimal 40000, not hex `0x40000` (262144). FRISCcc emits `%D` on all decimal literals to avoid ambiguity.

---

## Simulator Defaults

| Parameter | Value | Source |
|-----------|-------|--------|
| Memory size | 1000 KB | `DEFAULT_MEM_SIZE_KB = "1000"` |
| Step limit | 200,000,000 cycles | `DEFAULT_STEP_LIMIT = "200000000"` |
| Java-side timeout | 120 seconds | `DEFAULT_TIMEOUT = Duration.ofSeconds(120)` |
| Simulator library path | `node_modules/friscjs/lib/index.js` | `SIMULATOR_LIB_PATH` constant |

The step limit guards against non-terminating programs; exit code 124 is returned if hit. The Java timeout destroys the Node.js process forcibly if it exceeds 120 s regardless of step count.

---

## Program Output Capture

The Node.js script writes exactly one line to stdout: the decimal string representation of `simulator.CPU._r.r6` (the R6 register). Stderr carries any error diagnostics.

`FriscRunner` collects both streams with `redirectErrorStream(true)` and scans lines in reverse for the first match of `^-?\d+$`. This is the program's return value.

**Integer programs:** R6 is a signed 32-bit integer. Use `Result.r6ValueAsInt()`.

**Float programs:** FRISCcc represents `float` return values in Q16.16 fixed-point. R6 contains the raw integer. Use `Result.r6ValueAsFloat()` (divides by 65536.0) or `Result.r6ValueAsFloatString()` for human-readable output.

**Failure cases:**

| Condition | Node exit code | Java result |
|-----------|---------------|-------------|
| Normal `HALT` | 0 | `Result.success(r6, output)` |
| Step limit exceeded | 124 | `Result.failure(...)` |
| Parse/runtime error | 1 | `Result.failure(...)` |
| Java timeout (120 s) | — (killed) | `Result.failure("timed out")` |
| FRISC file not found | — (pre-check) | `Result.failure(...)` |
| Simulator lib not found | — (pre-check) | `Result.failure(...)` |

---

## friscjs Vendored Dependency

`package.json` declares `"friscjs": "^0.0.1"` as the sole dependency. The resolved copy is committed under `node_modules/friscjs/`. The library exposes two objects via `require`:

- `friscLib.assembler` — the FRISC assembler (`asm.parse(source)` → `{ mem: binaryString }`)
- `friscLib.simulator` — the `Simulator` constructor

The simulator is a synchronous, single-threaded JavaScript execution engine: no workers, no timers in the `FriscRunner` usage path. The upstream `consoleapp/frisc-console.js` uses `setInterval`-based pacing, which is not used here.

---

## Using friscjs directly (standalone)

`friscjs` is an independent open-source project by Ivan Žužak — the FRISC
assembler and simulator written in JavaScript
([izuzak/FRISCjs](https://github.com/izuzak/FRISCjs)). FRISCcc bundles a copy
under `node_modules/friscjs/` so the compiler works out of the box, but the
package can also be installed and used on its own — for example to assemble and
run FRISC programs from your own Node.js scripts, or to inspect registers and
memory after execution.

### Installation

```bash
npm install friscjs
```

```javascript
var friscjs = require("friscjs");
var asm = friscjs.assembler;   // FRISC assembler
var sim = friscjs.simulator;   // Simulator constructor
```

In the browser, include `lib/friscjs-browser.js` and use the same
`friscjs.assembler` / `friscjs.simulator` objects; the API is identical.

### Assembler

`asm.parse(friscSource)` assembles FRISC source text and returns an object with:

- `result.ast` — the parsed instructions (useful for debugging);
- `result.mem` — the binary image as an array of 8-character byte strings,
  ready to load into simulator memory.

On a syntax error it throws an exception carrying `line` and `column`
properties pointing at the offending location.

### Simulator

`new sim()` creates a simulator exposing two components, `MEM` and `CPU`:

| Component | Selected members |
|-----------|------------------|
| `MEM` | `_size` (capacity, default 256K), `_memory` (byte array), `reset()`, `loadBinaryString(image)`, `readb/readw/read(addr)`, `writeb/writew/write(addr, val)` |
| `CPU` | `_r` (register map: `r0`–`r7`, `pc`, `sr`, `iif`), `_frequency` (Hz, default 1), `run()`, `pause()`, `stop()`, `performCycle()`, `reset()`, `_decode(instruction)` |

The CPU exposes lifecycle callbacks you can assign to observe or step
execution: `onBeforeRun()`, `onBeforeCycle()`, `onBeforeExecute(instruction)`,
`onAfterCycle()`, and `onStop()`.

### Assemble-and-run example

```javascript
var friscjs = require("friscjs");
var asm = friscjs.assembler;
var sim = friscjs.simulator;

var program = [
  "    MOVE %D 40000, R7",   // set the stack pointer
  "    MOVE %D 7, R1",
  "    MOVE %D 35, R2",
  "    ADD  R1, R2, R6",     // R6 = 42 (FRISCcc returns results in R6)
  "    HALT"
].join("\n");

var result = asm.parse(program);

var simulator = new sim();
simulator.MEM.loadBinaryString(result.mem);
simulator.CPU.onStop = function () {
  console.log("R6 =", simulator.CPU._r.r6);   // → R6 = 42
};
simulator.CPU.run();
```

This mirrors what FRISCcc's `FriscRunner` does internally, except `FriscRunner`
drives `CPU.performCycle()` in a bounded loop (rather than `CPU.run()`) and
reads `CPU._r.r6` as the program's return value. For the full upstream API see
[`API.markdown`](https://github.com/izuzak/FRISCjs/blob/master/API.markdown) in
the FRISCjs repository.

> FRISCcc pins `friscjs@^0.0.1`. A direct `npm install friscjs` fetches the same
> package; the vendored copy under `node_modules/friscjs/` exists only so the
> compiler runs without a separate install step.

---

## Working Directory Requirement

`FriscRunner` resolves `node_modules/friscjs/lib/index.js` relative to its configured working directory (default: `Paths.get("").toAbsolutePath()`). If the process working directory is not the repo root, the library path check will fail and `run()` returns:

```
Result.failure("FRISC simulator library not found at <path>")
```

`run.sh` prevents this by executing `cd "$SCRIPT_DIR"` (the repo root) before invoking the JAR.

---

## See also

- [`../pipeline/codegen.md`](../pipeline/codegen.md) — how FRISC assembly is generated from IR
- [`../pipeline/runtime-abi.md`](../pipeline/runtime-abi.md) — calling convention, register roles, helper routines
- [`../pipeline/interpreter-vm.md`](../pipeline/interpreter-vm.md) — IR interpreter and bytecode VM as alternative back ends
- [`frisc-isa.md`](frisc-isa.md) — the FRISC instruction subset the simulator executes
