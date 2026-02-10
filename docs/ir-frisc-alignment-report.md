# IR/FRISC Alignment and Validation Report

## Scope
This report summarizes the current progress on IR/FRISC alignment, numeric literal handling, IR validation, and IR interpreter support.

## Mismatches Found
1. Source numeric literals and IR lowering were inconsistent.
- Lexer/semantics accepted hex and octal integer literals.
- `compiler-ir` lowering still used decimal-only parsing at several points.

2. IR verifier missed important structural checks.
- No verification that `addr_index` element stride matched the addressed element type.
- No verification that `.frame locals=...` was consistent with local slot usage.

3. No production IR interpreter path in CLI.
- Interpreter existed only as test helper code.
- No `run-ir` command in CLI entrypoint.

## Fixes Applied

### 1) Canonical integer representation policy
Decision: keep canonical IR integer constants in **decimal** form.

Changes:
- Kept IR grammar `Int` production decimal-only and documented this explicitly in `config/ir_definition.txt`.
- Added integer literal parser in `compiler-ir` utility layer that accepts source-literal forms (decimal/octal/hex) and normalizes to integer values.
- Replaced direct `Integer.parseInt(...)` usage in IR-lowering paths with shared literal parsing:
  - `compiler-ir/src/main/java/hr/fer/ppj/ir/lowering/expr/PrimaryExpressionGenerator.java`
  - `compiler-ir/src/main/java/hr/fer/ppj/ir/util/ConstantEvaluator.java`
  - `compiler-ir/src/main/java/hr/fer/ppj/ir/lowering/stmt/IfStatementGenerator.java`

Result:
- C source hex literals lower correctly.
- Printed IR remains canonical decimal (e.g. `0x2A` -> `#42:int32`).

### 2) IR verifier hardening
Changes:
- Added `addr_index` stride verification in `RhsVerifier`:
  - validates pointer base type;
  - validates stride against expected size;
  - supports both direct pointer stride and decayed-array element stride.
- Added frame/local consistency checks in `IrVerifier`:
  - frame local size alignment check;
  - local slot extent must fit inside `localsBytes`.

Files:
- `compiler-ir/src/main/java/hr/fer/ppj/ir/verify/RhsVerifier.java`
- `compiler-ir/src/main/java/hr/fer/ppj/ir/verify/IrVerifier.java`

### 3) CLI IR interpreter and command
Implemented production interpreter in CLI with watchdog and optional tracing.

New classes:
- `cli/src/main/java/hr/fer/ppj/cli/ir/IrInterpreter.java`
- `cli/src/main/java/hr/fer/ppj/cli/ir/IrInterpreterOptions.java`
- `cli/src/main/java/hr/fer/ppj/cli/ir/IrExecutionResult.java`
- `cli/src/main/java/hr/fer/ppj/cli/ir/IrCommandRunner.java`

CLI integration:
- Added `run-ir` command mode:
  - `java -jar ccompiler run-ir [--trace-ir] [--ir-step-limit N] <program.ir>`
- Added reporting methods for IR execution success/failure.
- Updated help output.

Files:
- `cli/src/main/java/hr/fer/ppj/cli/args/CliOptions.java`
- `cli/src/main/java/hr/fer/ppj/cli/args/ArgumentParser.java`
- `cli/src/main/java/hr/fer/ppj/cli/CCompilerMain.java`
- `cli/src/main/java/hr/fer/ppj/cli/reporting/ConsoleReporter.java`
- `cli/src/main/java/hr/fer/ppj/cli/reporting/HelpPrinter.java`

### 4) Tests added/updated
- Added CLI argument parser tests for `run-ir` and regular compilation flags.
- Added interpreter execution tests on selected real-world IR programs.
- Added hex-literal integration test that checks:
  - IR canonical decimal literal form,
  - IR verification,
  - FRISC execution return value.
- Switched FRISC basics float expected computation to use production interpreter.

Files:
- `cli/src/test/java/hr/fer/ppj/cli/ArgumentParserTest.java`
- `cli/src/test/java/hr/fer/ppj/cli/IrInterpreterExecutionTest.java`
- `cli/src/test/java/hr/fer/ppj/cli/FriscBasicsTest.java`

Removed obsolete test helper interpreter:
- `cli/src/test/java/hr/fer/ppj/cli/IrInterpreter.java`

## Validation Run Notes
- Full `compiler-ir` example validation passed (`220/220`) after stride-check fixes.
- Reactor tests covering `compiler-ir` + `cli` with new interpreter and parser tests passed.

## Open Items
1. FRISC runtime performance analysis for heavy real-world programs remains open.
- Some programs can be slow in FRISC simulator; root cause analysis and safe optimization pass are still pending.

2. Interpreter numeric model for float-heavy workloads can be expanded further.
- Current model follows project conventions and supports existing tests, but full semantic-float parity and deeper tracing ergonomics can be improved.
