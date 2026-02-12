# IR Optimizations (`compiler-opt`)

## Pipeline Position

The optimization phase sits between IR generation and FRISC code generation:

`lexer -> parser -> semantics -> compiler-ir -> compiler-opt -> compiler-codegen-frisc`

In CLI:
- `--O0` bypasses optimization (default behavior).
- `--O1` enables peephole/cfg optimizations.
- `--dump-ir` writes pre/post optimization snapshots to:
  - `compiler-bin/ir-dumps/<program>/before_optimization.ir`
  - `compiler-bin/ir-dumps/<program>/after_optimization.ir`

## int32 Semantics Used by Optimizer

Optimizer rules for `int32` assume fixed-width two's-complement semantics:

- `add/sub/mul/neg/shl/shr` wrap modulo `2^32`.
- `div/mod` are signed and truncate toward zero.
- Edge case is explicit:
  - `INT_MIN / -1 = INT_MIN`
  - `INT_MIN % -1 = 0`

These semantics are implemented in `hr.fer.ppj.opt.rules.arith.Int32Semantics` and mirrored in FRISC helper fast-paths.

## O1 Passes

`IrOptimizer` runs deterministic passes with fixpoint iteration (max 5):

1. `Int32ArithmeticPass`
- `add x, 0 -> x`
- `sub x, 0 -> x`
- `sub x, x -> 0`
- `mul x, 1 -> x`
- `mul x, 0 -> 0`
- `div x, 1 -> x`
- `mod x, 1 -> 0`
- `mul/div/mod` rules for `-1`
- int32 constant folding for `add/sub/mul/div/mod`
- `neg(neg x) -> x`

2. `Int32ShiftPass`
- `shl x, 0 -> x`
- `shr x, 0 -> x`
- `mul x, 2^k -> shl x, k`

3. `GlobalValuePropagationPass`
- Function-level propagation of known slot constants/copies (locals/params).
- Rewrites loads/uses when the incoming fact is provably stable across CFG predecessors.

4. `ValueRangeSimplificationPass`
- Conservative int32 interval analysis over tracked local/param slots.
- Folds provably constant comparisons and simplifies `br` to `jmp` when condition outcome is known.

5. `CopyPropagationPass`
- Local temp alias propagation for identity expressions.

6. `DeadTempEliminationPass`
- Removes dead assignments for pure RHS expressions.

7. `ControlFlowSimplificationPass`
- `br #true/#false -> jmp`
- jump threading through passthrough blocks
- merge `jmp` to immediately-following block when predecessor is unique

8. `UnreachableBlockEliminationPass`
- Removes CFG blocks unreachable from function entry.

9. `InductionStrengthReductionPass`
- Detects simple local induction variables in natural loops (`i = i + c`).
- Rewrites selected multiplications in loop bodies (`i*3`, `i*5`, `i*9`) into `shl+add`.

10. `DeadTempEliminationPass` (again)
- Cleans newly dead temps after control-flow rewrites.

11. `ControlFlowSimplificationPass` + `UnreachableBlockEliminationPass` (final cleanup)
- Stabilizes block graph shape after previous transformations.

## Validation

- Input IR is verified before optimization.
- Output IR is always verified (`IrPipeline.verify`) before returning.
- Optional per-pass verification is supported via `OptimizationOptions.validateAfterEachPass`.

## Scope Notes

- O1 keeps `.frame` and `.slots` unchanged.
- O1 rewrites only operations and control flow in `.blocks`.
- Optimizations are currently focused on `int32` safety and deterministic output.
