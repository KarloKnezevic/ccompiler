# Canonical Lowering Rules (C Subset → IR)

This document defines **authoritative, canonical lowering rules** for translating the supported **C language subset** into the target **IR language**.

It is written explicitly to guide **Cursor AI** so that generated IR:
- Is **deterministic** and **stable** (suitable for golden tests)
- Conforms **strictly** to the IR grammar
- Matches the semantics defined by the provided lexer, parser, and semantic specifications
- Avoids premature or implicit optimizations

The IR grammar referenced here is considered **final** for the AST → IR phase.

---

## 0. Scope and Assumptions

These rules assume:
- The input program is already **lexically**, **syntactically**, and **semantically valid** according to the provided C subset definitions.
- All identifiers are resolved.
- Types are known and validated.
- Struct layouts (field offsets and sizes) are known.

Cursor AI must **only** perform lowering. It must **not**:
- Perform optimizations
- Change evaluation order
- Remove or merge instructions
- Invent new IR patterns

---

## 1. General Principles (MANDATORY)

### 1.1 Determinism

- Expressions are evaluated **strictly left-to-right**.
- Each subexpression produces exactly one temporary (`t0`, `t1`, ...).
- Temporaries are numbered **in order of creation**.

### 1.2 No Optimizations (O0 IR)

Do **not** perform:
- Constant folding
- Dead code elimination
- Common subexpression elimination
- Load/store reordering
- Block merging or branch inversion

Golden tests depend on **structural stability**, not efficiency.

### 1.3 Naming Rules

- Entry block is always `L0`.
- New basic blocks are named `L1`, `L2`, ... in creation order.
- Temporaries are named `t0`, `t1`, ... in creation order.

---

## 2. Program Structure

### 2.1 Globals

#### Scalar globals

```c
int x = 5;
```

```ir
global x:int32 = #5:int32
```

#### Pointer globals

```c
int *p = 0;
```

```ir
global p:ptr<int32> = null:ptr<int32>
```

#### Array globals (REQUIRED pattern)

```c
char a[5] = {'a','b','c','d','e'};
```

```ir
global a:array<char,5> = { #'a':char, #'b':char, #'c':char, #'d':char, #'e':char }:array<char,5>
```

Rules:
- Element count **must match array size exactly**
- If C initializer is shorter, explicitly pad with `#0:<elemType>`
- Nested arrays use nested aggregate constants

#### String literals

```c
char s[4] = "abc";
```

```ir
global s:array<char,4> = { #'a':char, #'b':char, #'c':char, #0:char }:array<char,4>
```

---

## 3. Functions

### 3.1 Function Definition

```c
int f(int x)
```

```ir
.func f(x:int32):int32
```

```c
void g(void)
```

```ir
.func g():void
```

### 3.2 Return Statements

```c
return expr;
```

- Lower `expr` to a value
- Cast to return type if required
- Emit `ret <value>`

```c
return;
```

- Only valid in `void` functions
- Emit `ret`

---

## 4. Lvalues and Memory Access

### 4.1 Variables

```c
x
```

```ir
t0 = addr_of_symbol local:x
t1 = load t0:int32
```

Assignment:

```c
x = expr;
```

```ir
t0 = addr_of_symbol local:x
store t0, tExpr:int32
```

### 4.2 Pointer Dereference

```c
*p
```

- `p` already evaluates to `ptr<T>`

```ir
t1 = load p:T
```

Assignment:

```c
*p = v;
```

```ir
store p, v:T
```

### 4.3 Array Indexing

```c
a[i]
```

```ir
t0 = addr_of_symbol local:a
t1 = <lower i>
t2 = addr_index t0, t1, <elemSize>
t3 = load t2:<elemType>
```

Element sizes:
- `char`, `uchar`, `bool` → 1
- `int32`, `float`, `ptr<T>` → 4
- `struct` → sizeof(struct)

### 4.4 Struct Field Access

```c
s.f
```

```ir
t0 = addr_of_symbol local:s
t1 = addr_field t0, S.f
t2 = load t1:<fieldType>
```

```c
p->f
```

```ir
t1 = addr_field p, S.f
t2 = load t1:<fieldType>
```

---

## 5. Expressions

### 5.1 Arithmetic Operators

For `+ - * / %`:

```c
a + b
```

```ir
t0 = <lower a>
t1 = <lower b>
t2 = add t0, t1 : int32
```

Float uses `:float`.

### 5.2 Comparisons

Always lower to `bool`:

```c
a < b
```

```ir
t2 = cmp_lt t0, t1 : bool
```

### 5.3 Unary Operators

- `-x` → `neg`
- `!x` → see **truthiness**, then `not`
- `~x` → bitwise NOT via `xor x, -1`

### 5.4 Casts

Always explicit:

- `char → int` → `sext` or `zext`
- `int → char` → `trunc`
- `int ↔ float` → `itof`, `ftoi`
- `ptr ↔ ptr` → `ptrcast`

---

## 6. Truthiness (CRITICAL RULE)

Whenever an expression is used as a condition:

| Expression type | Rule |
|-----------------|------|
| `bool` | use directly |
| `int/char/uchar` | `cmp_ne x, #0:T : bool` |
| `ptr<T>` | `cmp_ne p, null:ptr<T> : bool` |

`br` **always** consumes a `bool`.

---

## 7. Control Flow

### 7.1 if / else

Canonical shape:

- Evaluate condition
- `br cond, L_then, L_else`
- Both branches end in `jmp L_join` (unless `ret`)

### 7.2 while

```
L0: jmp L_cond
L_cond: eval cond → br cond, L_body, L_after
L_body: body → jmp L_cond
L_after:
```

### 7.3 for

```
init
jmp L_cond
L_cond
L_body
L_inc
L_after
```

### 7.4 Logical AND / OR (SHORT-CIRCUIT ONLY)

Do **not** use `and/or`.

#### `A && B`

- eval A
- if false → false
- else eval B

#### `A || B`

- eval A
- if true → true
- else eval B

If result is only used for branching, do not materialize a value.

---

## 8. Assignments and Side Effects

### 8.1 Assignment

```c
x = expr
```

- lower `expr`
- store to lvalue address

### 8.2 Increment / Decrement (MANDATORY RULE)

**Do NOT use `preinc/postinc` IR instructions.**

Always lower explicitly:

```c
x++
```

```ir
addr = addr_of_symbol local:x
old = load addr:T
new = add old, #1:T
store addr, new:T
```

Expression value:
- `x++` → `old`
- `++x` → `new`

This rule avoids ambiguity and ensures semantic correctness.

---

## 9. Final Notes for Cursor AI

- Follow these rules **exactly**.
- Do not invent shortcuts.
- Prefer clarity over compactness.
- If multiple IR encodings are possible, choose the one described here.

This document is the **single source of truth** for C → IR lowering.

