# Float on FRISC: Comprehensive Technical Documentation

This document provides an in-depth explanation of how floating-point operations are implemented on the FRISC architecture, which has no native hardware support for floating-point arithmetic.

---

## Table of Contents

1. [Representation: Q16.16 Fixed-Point Format](#1-representation-q1616-fixed-point-format)
2. [Addition and Subtraction (F_FADD, F_FSUB)](#2-addition-and-subtraction-f_fadd-f_fsub)
3. [Multiplication (F_FMUL)](#3-multiplication-f_fmul)
4. [Division (F_FDIV)](#4-division-f_fdiv)
5. [Comparison (F_FCMP)](#5-comparison-f_fcmp)
6. [Type Conversions (F_I2F, F_F2I)](#6-type-conversions-f_i2f-f_f2i)
7. [Worked Examples](#7-worked-examples)
8. [Limitations and Design Trade-offs](#8-limitations-and-design-trade-offs)

---

## 1. Representation: Q16.16 Fixed-Point Format

### 1.1. Why Q16.16?

The compiler implements C's `float` type using **Q16.16 fixed-point representation** instead of IEEE-754 for several reasons:

- **Simplicity**: Fixed-point arithmetic is straightforward to implement in software using integer operations
- **Determinism**: No special cases (infinity, NaN, denormals) to handle
- **Performance**: Integer operations are faster than floating-point emulation
- **Precision**: 16 bits of fractional precision (≈ 0.00001526) is sufficient for most applications
- **Range**: Supports values from approximately -32768.0 to 32767.99998
- **Educational Value**: Demonstrates how floating-point can be implemented without hardware support

### 1.2. Format Definition

A `float` value is stored as a **32-bit signed integer** where:

- **Bits 31-16**: Integer part (signed 16-bit, range -32768 to 32767)
- **Bits 15-0**: Fractional part (unsigned 16-bit, represents value/65536)

The actual float value is computed as:

```
actual_float = stored_integer / 65536.0
```

Or equivalently:

```
actual_float = stored_integer / 2^16
```

### 1.3. Representable Range

- **Minimum**: `raw = -2^31` → `value = -2^31 / 2^16 = -32768.0`
- **Maximum**: `raw = 2^31 - 1` → `value ≈ (2^31 - 1) / 2^16 ≈ 32767.9999847`

So in C terms:

> **Any `float` between approximately -32768.0 and +32767.9999 is representable in Q16.16.**

### 1.4. Resolution

The **step** (distance between two adjacent representable values) is:

```
1 / 65536 ≈ 0.0000152587890625
```

This means Q16.16 can distinguish values that differ by approximately 0.00001526.

### 1.5. Examples

| Float Value | Q16.16 Integer | Hex | Calculation |
|-------------|----------------|-----|-------------|
| 1.0         | 65536          | 0x00010000 | 1.0 × 65536 = 65536 |
| 1.5         | 98304          | 0x00018000 | 1.5 × 65536 = 98304 |
| 2.0         | 131072         | 0x00020000 | 2.0 × 65536 = 131072 |
| 2.5         | 163840         | 0x00028000 | 2.5 × 65536 = 163840 |
| 3.75        | 245760         | 0x0003C000 | 3.75 × 65536 = 245760 |
| 0.5         | 32768          | 0x00008000 | 0.5 × 65536 = 32768 |
| -1.0        | -65536         | 0xFFFF0000 | -1.0 × 65536 = -65536 |
| -1.5        | -98304         | 0xFFFE8000 | -1.5 × 65536 = -98304 |
| 0.0         | 0              | 0x00000000 | 0.0 × 65536 = 0 |

### 1.6. How C Float Literals Map to Q16.16

When the compiler encounters a float literal like `1.5f` or `2.5`, it:

1. Parses the literal as a Java `float` value
2. Converts to Q16.16 using: `raw = Math.round(floatValue * 65536)`
3. Emits a `MOVE` instruction to load the Q16.16 integer value

For example, `float x = 1.5;` generates:

```frisc
MOVE %D 98304, R0    ; 1.5 in Q16.16 = 98304
```

---

## 2. Addition and Subtraction (F_FADD, F_FSUB)

### 2.1. Mathematical Basis

Float addition and subtraction are the simplest operations because they preserve the scaling factor:

**Addition:**
```
(a × 65536) + (b × 65536) = (a + b) × 65536
```

**Subtraction:**
```
(a × 65536) - (b × 65536) = (a - b) × 65536
```

Since both operands are already scaled by 65536, we can simply add or subtract them as signed integers. The result is automatically in Q16.16 format because the scaling factor is preserved.

### 2.2. Why This Works

In fixed-point arithmetic, addition and subtraction are straightforward because:

- Both operands have the same scaling factor (65536)
- Adding/subtracting scaled values preserves the scaling: `(a×s) ± (b×s) = (a±b)×s`
- No overflow handling needed (32-bit signed addition/subtraction handles it correctly via two's complement wrap-around)
- The result is automatically in the correct format

### 2.3. FRISC Implementation

**F_FADD (Float Addition):**

```frisc
F_FADD:
    PUSH R5                 ; save old frame pointer
    MOVE R7, R5             ; R5 = current SP
    LOAD R0, (R5+08)        ; a (left operand, Q16.16)
    LOAD R1, (R5+0C)        ; b (right operand, Q16.16)
    ADD R0, R1, R0          ; result = a + b (Q16.16)
    MOVE R0, R6             ; return result in R6
    POP R5                  ; restore frame pointer
    RET
```

**F_FSUB (Float Subtraction):**

```frisc
F_FSUB:
    PUSH R5                 ; save old frame pointer
    MOVE R7, R5             ; R5 = current SP
    LOAD R0, (R5+08)        ; a (left operand, Q16.16)
    LOAD R1, (R5+0C)        ; b (right operand, Q16.16)
    SUB R0, R1, R0          ; result = a - b (Q16.16)
    MOVE R0, R6             ; return result in R6
    POP R5                  ; restore frame pointer
    RET
```

### 2.4. FRISC Instructions Used

- **ADD**: Integer addition of two 32-bit Q16.16 values
- **SUB**: Integer subtraction of two 32-bit Q16.16 values
- **LOAD**: Load arguments from stack frame
- **MOVE**: Move result to return register R6

### 2.5. Edge Cases

- **Overflow/Underflow**: 32-bit signed addition/subtraction correctly handles overflow via two's complement wrap-around. This is acceptable for fixed-point arithmetic.
- **Negative Numbers**: Two's complement addition/subtraction correctly handles negative operands and negative results.
- **Zero**: Adding/subtracting zero works correctly (identity operation).

### 2.6. Complexity

- **Time Complexity**: O(1) - single ADD or SUB instruction
- **Space Complexity**: O(1) - uses only registers

---

## 3. Multiplication (F_FMUL)

### 3.1. Mathematical Basis

Float multiplication requires more care because the scaling factors multiply:

```
(a × 65536) × (b × 65536) = (a × b) × 65536²
```

To get the result in Q16.16 format, we need to divide by 65536:

```
result = ((a × b) × 65536²) / 65536 = (a × b) × 65536
```

Which is equivalent to:

```
raw_result = (raw_a * raw_b) >> 16
```

### 3.2. Critical Requirement: 64-bit Product

**The multiplication `raw_a * raw_b` MUST be a full 64-bit signed product**, implemented with two 32-bit FRISC registers `(HI : LO)`. This guarantees correct results for small decimal numbers like:

- `1.5 × 2.5 = 3.75` → raw = 3.75 × 65536 = 245760
- `1.5 × 2.5 × 3.5 = 13.125` → raw = 860160

**Why 64-bit Product is Necessary:**

A naive implementation using only 32-bit arithmetic would compute:

```c
raw_result = (int32)(raw_a * raw_b) >> 16  // WRONG - overflows!
```

This is incorrect because:

- The product `raw_a * raw_b` can exceed 32 bits even for small values
- For example: `1.5 × 2.5` in Q16.16 is `98304 × 163840 = 16,106,127,360`
- This value (0x3C0000000) requires 34 bits, so a 32-bit multiply would wrap around
- After wrapping, shifting right by 16 produces an incorrect result

By computing the **full 64-bit product** and then shifting right by 16 bits, we ensure that the correct middle 32 bits are extracted, producing accurate Q16.16 results.

### 3.3. Algorithm: 64-bit Russian Peasant Multiplication

The algorithm uses the **Russian peasant (shift-and-add) algorithm** extended to 64-bit operands:

#### Step 1: Sign Handling

1. Load raw `a` and `b` from the stack (Q16.16, signed 32-bit)
2. If either operand is zero, return 0 immediately
3. Initialize a sign register: `sign = +1`
4. If `a < 0`: negate `a` and flip sign: `sign = -sign`
5. If `b < 0`: negate `b` and flip sign: `sign = -sign`
6. After this: `a_abs = |a|`, `b_abs = |b|`, `sign = +1 or -1`

#### Step 2: 64-bit Russian Peasant Multiplication

We multiply two **non-negative 32-bit integers** using the Russian peasant algorithm with **64-bit accumulation** using HI:LO register pairs.

**Register Allocation:**
- `R0 = M_LO` (64-bit multiplicand low, initially `|a|`)
- `R3 = M_HI` (64-bit multiplicand high, initially 0)
- `R1 = B` (multiplier, initially `|b|`)
- `R2 = ACC_LO` (64-bit accumulator low)
- `R4 = ACC_HI` (64-bit accumulator high, sign saved on stack)
- `R6 = temp` (for bit checks and carry extraction)

**Initialization:**
```
M_HI = 0
M_LO = |a|
ACC_HI = 0
ACC_LO = 0
B = |b|
```

**Loop: while (B != 0)**

1. **If `(B & 1) != 0`**: Perform 64-bit addition `ACC += M`:
   - `ACC_LO = ACC_LO + M_LO` (using `ADD`)
   - `ACC_HI = ACC_HI + M_HI + carry` (using `ADC`)

2. **Perform 64-bit left shift: `M <<= 1`**:
   - Save old `M_LO`
   - `M_LO <<= 1`
   - Extract carry bit from old `M_LO` (bit 31): `carry = (old_M_LO >> 31) & 1`
   - `M_HI <<= 1`
   - `M_HI |= carry`

3. **`B >>= 1`** (logical right shift)

After the loop: `(ACC_HI, ACC_LO) = 64-bit product = |a| * |b|`

#### Step 3: Q16.16 Scaling (Extract Middle 32 Bits)

We have the 64-bit product in `(ACC_HI, ACC_LO)`. To convert to Q16.16, we extract the middle 32 bits:

```
raw_result_abs = (ACC_HI << 16) | (ACC_LO >> 16)
```

This is equivalent to shifting the 64-bit product right by 16 bits and taking the lower 32 bits of the result.

#### Step 4: Apply Sign

Restore the sign from the stack. If `sign < 0`, negate the result:

```
if (sign < 0) {
    result = -result_abs
} else {
    result = result_abs
}
```

Return the result in register `R6`.

### 3.4. FRISC Instructions Used

#### Basic Arithmetic: ADD / SUB

- Used for adding/subtracting 32-bit words
- Used for negating operands (two's complement: `0 - x`)

#### Shifts: SHL / SHR

- **Multiplicand doubling**: `M_LO <<= 1`, `M_HI <<= 1`
- **Multiplier halving**: `B >>= 1`
- **Carry extraction**: `(old_M_LO >> 31) & 1` to extract bit 31
- **Q16.16 scaling**: `ACC_LO >> 16` and `ACC_HI << 16`
- **Bit combination**: `OR` to combine shifted values

#### ADC (Add with Carry) - Critical for 64-bit Addition

**ADC** is a FRISC instruction that performs:

```
R = X + Y + carry
```

where `carry` comes from the carry flag (C) set by a previous `ADD` or `SUB` instruction. This is **essential** for correctly implementing 64-bit addition using two 32-bit registers.

**Why ADC is Necessary:**

When adding two 64-bit values represented as `(HI : LO)` pairs, we must:

1. Add the low 32 bits first: `ACC_LO = ACC_LO + M_LO`
2. This may produce a carry (overflow) if `ACC_LO + M_LO ≥ 2^32`
3. The carry flag (C) is automatically set by the `ADD` instruction
4. Add the high 32 bits with the carry: `ACC_HI = ACC_HI + M_HI + C`

**64-bit Addition Implementation:**

```frisc
; Add low 32 bits
ADD  ACC_LO, M_LO, ACC_LO    ; ACC_LO = ACC_LO + M_LO (sets carry flag C)

; Add high 32 bits with carry
ADC  ACC_HI, M_HI, ACC_HI    ; ACC_HI = ACC_HI + M_HI + C
```

This ensures correct propagation of the carry from the low word to the high word, exactly as required in 64-bit arithmetic. Without `ADC`, the carry would be lost, leading to incorrect results for products that exceed 32 bits.

**Example:**

```
ACC = 0xFFFFFFFF:FFFFFFFF  (high:low)
M   = 0x00000000:00000001

After ADD ACC_LO, M_LO, ACC_LO:
  ACC_LO = 0x00000000  (wraps around)
  Carry flag C = 1 (overflow occurred)

After ADC ACC_HI, M_HI, ACC_HI:
  ACC_HI = 0xFFFFFFFF + 0x00000000 + 1 = 0x00000000 (wraps, but carry was added)
  Result: 0x00000000:00000000 (correct 64-bit addition with carry propagation) ✓
```

### 3.5. Domain and Overflow

**General Representable Range:**
```
-32768.0 ≤ value ≤ +32767.9999847
```

**Safe Domain for Multiplication (no overflow in final 32-bit result):**

To avoid overflow in the final 32-bit Q16.16 result, we need:

```
|A * B| ≤ 32768
```

where `A` and `B` are the real C `float` values.

For a **symmetric safe interval** `[-M, +M]` for each operand:

```
M² ≤ 32768  ⇒  M ≈ 181.019...
```

Therefore:

> **Safe domain for Q16.16 multiplication without overflow for any pair of operands:**
> each operand in **[-181.0, +181.0]**.

Outside this range:
- Q16.16 can still represent the individual values
- But the product may overflow the 32-bit result and wrap around (two's complement)

**Note:** For typical operations (e.g., `1.5 * 2.5 * 3.5`), the algorithm is exact and overflow-free. The 64-bit product ensures correct results even when the intermediate product exceeds 32 bits.

### 3.6. Complexity

- **Time Complexity**: O(32) - the Russian peasant loop iterates at most 32 times (once per bit in the multiplier)
- **Space Complexity**: O(1) - uses only registers (no dynamic memory allocation)

---

## 4. Division (F_FDIV)

### 4.1. Mathematical Basis

Float division requires careful handling of the scaling factors:

```
C = A / B

where:
A = raw_a / 65536.0
B = raw_b / 65536.0

result = (raw_a / 65536.0) / (raw_b / 65536.0) = (raw_a / raw_b)

To get Q16.16 format:
raw_result = (raw_a << 16) / raw_b
```

Conceptually, we want to compute:

```
raw_result = ((int64) raw_a << 16) / raw_b
```

This is equivalent to:

```
raw_result = (raw_a * 65536) / raw_b
```

### 4.2. Algorithm: Specialized Q16.16 Division

The algorithm avoids the need for 64-bit division by:

1. First computing the integer part using 32-bit division
2. Then computing the fractional part using a 16-iteration loop
3. Combining both parts into the final Q16.16 result

#### Step 1: Sign Handling and Division by Zero

1. Load raw `a` and `b` from the stack (Q16.16, signed 32-bit)
2. If `b == 0`, return 0 (safe behavior, no trap)
3. If `a == 0`, return 0 immediately
4. Initialize a sign register: `sign = +1`
5. If `a < 0`: negate `a` and flip sign: `sign = -sign`
6. If `b < 0`: negate `b` and flip sign: `sign = -sign`
7. After this: `a_abs = |a|`, `b_abs = |b|`, `sign = +1 or -1`

#### Step 2: 32-bit Integer Part Division

Perform unsigned 32-bit division: `integer_part = a_abs / b_abs`

This uses the **binary long division (shift-subtract) algorithm**:

- Initialize: `integer_part = 0`, `remainder = 0`
- For `i = 31` down to `0`:
  - `remainder <<= 1`
  - Bring down the `i`-th bit of `a_abs` into `remainder`
  - If `remainder >= b_abs`:
    - `remainder -= b_abs`
    - `integer_part |= (1 << i)`

After this loop: `R0 = integer_part`, `R2 = remainder`

#### Step 3: 16-Step Fractional Refinement Loop

Compute the fractional part (lower 16 bits of Q16.16 result):

- Initialize: `frac = 0`
- For `i = 0` to `15` (16 iterations):
  - `remainder <<= 1`
  - `frac <<= 1`
  - If `remainder >= b_abs`:
    - `remainder -= b_abs`
    - `frac |= 1`

After this loop: `R3 = frac` (lower 16 bits)

#### Step 4: Combine Integer and Fractional Parts

```
result_abs = (integer_part << 16) | (frac & 0xFFFF)
```

#### Step 5: Apply Sign

If `sign < 0`, negate the result. Return in `R6`.

### 4.3. FRISC Instructions Used

- **SUB**: Used for iterative subtraction in division loops
- **CMP**: Compare remainder with divisor
- **JR_***: Conditional jumps (JR_SLT, JR_SGE, JR_EQ, etc.)
- **SHL / SHR**: Shifts for:
  - Bringing down bits from dividend
  - Shifting remainder and fractional accumulator
  - Combining integer and fractional parts
- **AND / OR**: Bit manipulation for setting quotient bits

### 4.4. Example: 5.0 / 2.0

**Input:**
- `a = 5.0` → `raw_a = 327680` (0x00050000)
- `b = 2.0` → `raw_b = 131072` (0x00020000)

**Computation:**
1. Both operands positive, so `sign = +1`, `a_abs = 327680`, `b_abs = 131072`
2. Integer division: `327680 / 131072 = 2`, remainder = `65536`
3. Fractional loop (16 iterations):
   - `i=0`: `remainder = 131072`, `remainder >= b_abs?` yes → `remainder = 0`, `frac = 1`
   - `i=1`: `remainder = 0`, `remainder < b_abs?` yes → `frac = 2`
   - ... (remaining iterations)
   - After 16 iterations: `frac = 0x8000` (0.5 in Q16.16 fractional part)
4. Combine: `result_abs = (2 << 16) | 0x8000 = 0x00028000 = 163840`
5. Apply sign: `result = +163840`

**Output:**
- `raw_result = 163840` (0x00028000)
- `value = 163840 / 65536 = 2.5` ✓

### 4.5. Complexity

- **Time Complexity**: O(32) for integer division + O(16) for fractional loop = O(48)
- **Space Complexity**: O(1) - uses only registers

---

## 5. Comparison (F_FCMP)

### 5.1. Mathematical Basis

Float comparison is straightforward because the Q16.16 scaling factor (65536) is positive, so the ordering of Q16.16 integers matches the ordering of the real float values:

```
If A < B, then raw_a < raw_b
If A == B, then raw_a == raw_b
If A > B, then raw_a > raw_b
```

Therefore, we can compare Q16.16 values as signed integers.

### 5.2. FRISC Implementation

**F_FCMP** returns a three-way comparison result:

```frisc
F_FCMP:
    PUSH R5
    MOVE R7, R5
    LOAD R0, (R5+08)        ; a
    LOAD R1, (R5+0C)        ; b
    CMP R0, R1              ; compare a and b
    JR_SLT L_LESS           ; if a < b, return -1
    JR_SGT L_GREATER        ; if a > b, return 1
    MOVE %D 0, R6           ; a == b, return 0
    JP L_DONE
L_LESS:
    MOVE %D -1, R6          ; return -1
    JP L_DONE
L_GREATER:
    MOVE %D 1, R6           ; return 1
L_DONE:
    POP R5
    RET
```

**Return Values:**
- `-1`: if `a < b`
- `0`: if `a == b`
- `1`: if `a > b`

### 5.3. FRISC Instructions Used

- **CMP**: Compare two Q16.16 values
- **JR_SLT / JR_SGT / JR_EQ**: Conditional jumps based on comparison result
- **MOVE**: Load return value into R6

### 5.4. Complexity

- **Time Complexity**: O(1) - single comparison
- **Space Complexity**: O(1) - uses only registers

---

## 6. Type Conversions (F_I2F, F_F2I)

### 6.1. Integer to Float (F_I2F)

Converts a 32-bit signed integer to Q16.16 format.

**Mathematical Basis:**

```
float_value = int_value
raw_result = int_value * 65536
```

**FRISC Implementation:**

```frisc
F_I2F:
    PUSH R5
    MOVE R7, R5
    LOAD R0, (R5+08)        ; int_value
    SHL R0, %D 16, R0       ; raw_result = int_value << 16
    MOVE R0, R6             ; return Q16.16 result
    POP R5
    RET
```

**Example:**
- Input: `int_value = 5`
- Output: `raw_result = 5 << 16 = 327680` (0x00050000)
- Float value: `327680 / 65536 = 5.0` ✓

### 6.2. Float to Integer (F_F2I)

Converts a Q16.16 value to a 32-bit signed integer (truncates fractional part).

**Mathematical Basis:**

```
int_result = (int)(float_value)
raw_result = raw_float >> 16
```

**FRISC Implementation:**

```frisc
F_F2I:
    PUSH R5
    MOVE R7, R5
    LOAD R0, (R5+08)        ; raw_float (Q16.16)
    SHR R0, %D 16, R0       ; int_result = raw_float >> 16 (truncate)
    MOVE R0, R6             ; return integer result
    POP R5
    RET
```

**Example:**
- Input: `raw_float = 163840` (2.5 in Q16.16)
- Output: `int_result = 163840 >> 16 = 2` (truncated) ✓

### 6.3. Complexity

- **Time Complexity**: O(1) - single shift instruction
- **Space Complexity**: O(1) - uses only registers

---

## 7. Worked Examples

### Example 1: 1.5 + 2.25

**Step-by-step computation:**

1. **Convert to Q16.16:**
   - `1.5` → `raw_a = 98304` (0x00018000)
   - `2.25` → `raw_b = 147456` (0x00024000)

2. **Integer addition:**
   - `raw_result = 98304 + 147456 = 245760` (0x0003C000)

3. **Convert back to float:**
   - `value = 245760 / 65536 = 3.75` ✓

**FRISC code:**
```frisc
; Load 1.5 (98304)
MOVE %D 98304, R0
PUSH R0

; Load 2.25 (147456)
MOVE %D 147456, R0
MOVE R0, R1
POP R0

; Call F_FADD
PUSH R1
PUSH R0
CALL F_FADD
ADD R7, %D 8, R7
MOVE R6, R0

; Result: R0 = 245760 (3.75 in Q16.16)
```

### Example 2: 1.5 × 2.5

**Step-by-step computation:**

1. **Convert to Q16.16:**
   - `1.5` → `raw_a = 98304` (0x00018000)
   - `2.5` → `raw_b = 163840` (0x00028000)

2. **64-bit multiplication:**
   - `98304 × 163840 = 16,106,127,360` (0x3C0000000)
   - 64-bit product: `(HI, LO) = (0x00000003, 0xC0000000)`

3. **Extract middle 32 bits:**
   - `raw_result_abs = (0x00000003 << 16) | (0xC0000000 >> 16)`
   - `raw_result_abs = 0x0003C000 = 245760`

4. **Convert back to float:**
   - `value = 245760 / 65536 = 3.75` ✓

**FRISC code:**
```frisc
; Load 1.5 (98304)
MOVE %D 98304, R0
PUSH R0

; Load 2.5 (163840)
MOVE %D 163840, R0
MOVE R0, R1
POP R0

; Call F_FMUL
PUSH R1
PUSH R0
CALL F_FMUL
ADD R7, %D 8, R7
MOVE R6, R0

; Result: R0 = 245760 (3.75 in Q16.16)
```

### Example 3: 5.0 / 2.0

**Step-by-step computation:**

1. **Convert to Q16.16:**
   - `5.0` → `raw_a = 327680` (0x00050000)
   - `2.0` → `raw_b = 131072` (0x00020000)

2. **Integer division:**
   - `integer_part = 327680 / 131072 = 2`
   - `remainder = 327680 % 131072 = 65536`

3. **Fractional loop (16 iterations):**
   - `i=0`: `remainder = 131072`, `remainder >= b_abs?` yes → `remainder = 0`, `frac = 1`
   - `i=1`: `remainder = 0`, `remainder < b_abs?` yes → `frac = 2`
   - ... (remaining iterations)
   - After 16 iterations: `frac = 0x8000` (0.5 in Q16.16 fractional part)

4. **Combine parts:**
   - `result_abs = (2 << 16) | 0x8000 = 0x00028000 = 163840`

5. **Convert back to float:**
   - `value = 163840 / 65536 = 2.5` ✓

**FRISC code:**
```frisc
; Load 5.0 (327680)
MOVE %D 327680, R0
PUSH R0

; Load 2.0 (131072)
MOVE %D 131072, R0
MOVE R0, R1
POP R0

; Call F_FDIV
PUSH R1
PUSH R0
CALL F_FDIV
ADD R7, %D 8, R7
MOVE R6, R0

; Result: R0 = 163840 (2.5 in Q16.16)
```

### Example 4: Chained Multiplication (1.5 × 2.5 × 3.5)

**Step-by-step computation:**

1. **First multiplication: 1.5 × 2.5**
   - `1.5` → `98304`, `2.5` → `163840`
   - `98304 × 163840 = 16,106,127,360` (64-bit)
   - Extract middle 32 bits: `245760` (3.75 in Q16.16)

2. **Second multiplication: 3.75 × 3.5**
   - `3.75` → `245760`, `3.5` → `229376`
   - `245760 × 229376 = 56,371,445,760` (64-bit)
   - Extract middle 32 bits: `860160` (13.125 in Q16.16)

3. **Final result:**
   - `value = 860160 / 65536 = 13.125` ✓

---

## 8. Limitations and Design Trade-offs

### 8.1. Range Limitations

- **Maximum value**: Approximately +32767.9999847
- **Minimum value**: -32768.0
- **No infinity or NaN**: Cannot represent special IEEE-754 values

### 8.2. Precision Limitations

- **Resolution**: Approximately 0.00001526 (1/65536)
- **Precision**: About 4-5 decimal digits
- **Rounding**: Truncation-based (no proper rounding to nearest)

### 8.3. Overflow Behavior

- **Addition/Subtraction**: Wraps around via two's complement (acceptable for fixed-point)
- **Multiplication**: For values outside safe domain `[-181.0, +181.0]`, the final 32-bit result may overflow and wrap
- **Division**: No overflow (result is always smaller than dividend)

### 8.4. Why These Limitations Are Acceptable

For an educational compiler targeting a subset of C:

- **Simplicity**: Fixed-point is much simpler to implement than IEEE-754
- **Determinism**: No special cases to handle (infinity, NaN, denormals)
- **Performance**: Integer operations are faster than floating-point emulation
- **Sufficient for most programs**: The range and precision are adequate for typical educational programs

### 8.5. Future Improvements

For production use, consider:

1. **IEEE-754 encoding**: More standard, handles special values
2. **Proper rounding**: Round-to-nearest instead of truncation
3. **Extended precision**: Use 64-bit Q32.32 for better range/precision
4. **Optimization**: Inline simple operations instead of function calls
5. **Saturation arithmetic**: Clamp results to representable range instead of wrapping

---

## Summary

This compiler implements C's `float` type using Q16.16 fixed-point representation on FRISC:

- **Addition/Subtraction**: Simple integer add/sub (O(1))
- **Multiplication**: 64-bit Russian peasant with HI:LO registers and ADC (O(32))
- **Division**: 32-bit integer division + 16-step fractional loop (O(48))
- **Comparison**: Integer comparison (O(1))
- **Type conversions**: Simple shifts (O(1))

All operations use only basic FRISC instructions (ADD, SUB, SHL, SHR, ADC, CMP, JR_*) and maintain Q16.16 format throughout, ensuring correct results for values in the safe domain.
