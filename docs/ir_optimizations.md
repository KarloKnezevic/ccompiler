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

Float folding rules use the same Q16.16 arithmetic model as FRISC helpers and the IR interpreter:

- `add/sub` on raw Q16 values.
- `mul`: `(left * right) >> 16`.
- `div`: `((left << 16) / right)`, with `right == 0 -> 0`.

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

3. `TypedConstantFoldingPass`
- Typed folding for `binop/cmp/unary/cast` with constant operands.
- Supports `int32`, `char/uchar`, `bool`, and `float` (Q16.16-safe folding).

4. `CastSimplificationPass`
- Removes redundant casts when operand/result types already match.
- Rewrites downstream uses through aliasing, then drops redundant cast assignments.

5. `CommonSubexpressionEliminationPass`
- Local (block-level) CSE for side-effect-free RHS expressions.
- Replaces repeated expressions with alias reuse, then relies on cleanup passes.

6. `LoopInvariantCodeMotionPass`
- Conservative LICM for loop blocks: reorders loop-invariant pure assignments to the top of the loop block.
- Does not move values across block boundaries (keeps current verifier-compatible temp scoping).

7. `GlobalValuePropagationPass`
- Function-level propagation of known slot constants/copies (locals/params).
- Rewrites loads/uses when the incoming fact is provably stable across CFG predecessors.

8. `TinyFunctionInliningPass`
- Inlines tiny pure leaf `int32` functions (single block, no local frame, no side effects).
- Targets call sites `t = call f(...)` and rewrites to equivalent local SSA assignments.

9. `LoadForwardingPass`
- Redundant load elimination for tracked local/param slots.
- Forwards slot loads to last known value when no aliasing write/call invalidates the fact.

10. `DeadSlotStoreEliminationPass`
- Removes tracked slot stores when the stored value is never read before overwrite/exit.
- Uses conservative backward liveness over CFG; calls/unknown loads keep slots live.

11. `ValueRangeSimplificationPass`
- Conservative int32 interval analysis over tracked local/param slots.
- Folds provably constant comparisons and simplifies `br` to `jmp` when condition outcome is known.

12. `CopyPropagationPass`
- Local temp alias propagation for identity expressions.

13. `DeadTempEliminationPass`
- Removes dead assignments for pure RHS expressions.

14. `ControlFlowSimplificationPass`
- `br #true/#false -> jmp`
- jump threading through passthrough blocks
- merge `jmp` to immediately-following block when predecessor is unique

15. `UnreachableBlockEliminationPass`
- Removes CFG blocks unreachable from function entry.

16. `InductionStrengthReductionPass`
- Detects simple local induction variables in natural loops (`i = i + c`).
- Rewrites selected multiplications in loop bodies (`i*3`, `i*5`, `i*9`) into `shl+add`.

17. `DeadTempEliminationPass` (again)
- Cleans newly dead temps after control-flow rewrites.

18. `ControlFlowSimplificationPass` + `UnreachableBlockEliminationPass` (final cleanup)
- Stabilizes block graph shape after previous transformations.

## Validation

- Input IR is verified before optimization.
- Output IR is always verified (`IrPipeline.verify`) before returning.
- Optional per-pass verification is supported via `OptimizationOptions.validateAfterEachPass`.

## Scope Notes

- O1 keeps `.frame` and `.slots` unchanged.
- O1 rewrites only operations and control flow in `.blocks`.
- Optimizations are currently focused on `int32` safety and deterministic output.
- FRISC codegen additionally performs conservative compile-time bounds-check skipping
  for `addr_index` with statically in-range constant indices.
- FRISC backend includes lightweight codegen-side speedups:
  - helper-call avoidance for common `mul/div/mod` constant cases,
  - power-of-two scaling for `addr_index`,
  - local-frame-only zeroing (instead of full temp/scratch frame),
  - peephole cleanup (`MOVE Rx,Rx`, `PUSH/POP` pairs, `JP` to next label).
