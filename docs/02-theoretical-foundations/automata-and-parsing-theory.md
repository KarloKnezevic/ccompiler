# Automata and Parsing Theory

## Overview

This document provides detailed coverage of automata theory and parsing algorithms used in the PPJ compiler. It complements the formal languages chapter with algorithmic details and implementation considerations.

## Finite Automata Algorithms

### ε-Closure Computation

The **ε-closure** of a state set S is the set of all states reachable from S using only ε-transitions.

**Algorithm**:
```text
function ε-closure(S):
    T = S
    stack = S (all states in S)
    
    while stack is not empty:
        t = pop(stack)
        for each state u with ε-transition from t:
            if u not in T:
                add u to T
                push u onto stack
    
    return T
```

**Time Complexity**: O(n + m) where n = states, m = ε-transitions

**Use**: Converting ε-NFA to NFA, subset construction

### Subset Construction Algorithm

**Algorithm**: Convert NFA to equivalent DFA

```text
function subset-construction(NFA):
    DFA_states = {}
    DFA_transitions = {}
    DFA_accepting = {}
    
    initial = ε-closure({NFA.start})
    DFA_states.add(initial)
    worklist = [initial]
    
    while worklist is not empty:
        current = pop(worklist)
        
        for each symbol a in alphabet:
            next = ε-closure(move(current, a))
            if next is not empty:
                if next not in DFA_states:
                    DFA_states.add(next)
                    worklist.add(next)
                DFA_transitions[(current, a)] = next
        
        if current contains NFA accepting state:
            DFA_accepting.add(current)
    
    return DFA(DFA_states, DFA_transitions, initial, DFA_accepting)
```

**Optimization**: Use state numbering to avoid set comparisons

**See Also**: [Lexer Implementation](../03-lexical-analysis/implementation-notes.md)

### DFA Minimization

**Algorithm**: Minimize DFA to smallest equivalent DFA

**Partition Refinement**:
1. Initial partition: {accepting states}, {non-accepting states}
2. Refine partition until stable:
   - States in same partition must have transitions to same partition
   - Split partitions that violate this property
3. Merge equivalent states

**Time Complexity**: O(n²) where n = number of states

## LR Parsing Algorithms

### CLOSURE Algorithm

**Purpose**: Compute closure of LR(1) item set

**Algorithm**:
```text
function CLOSURE(I):
    repeat
        for each item [A → α · Bβ, a] in I:
            for each production B → γ in grammar:
                for each terminal b in FIRST(βa):
                    add [B → · γ, b] to I
    until no changes
    return I
```

**Key Insight**: Lookahead propagation through nullable prefixes

**Implementation Note**: Use set data structure to avoid duplicates

### GOTO Algorithm

**Purpose**: Compute GOTO transition for item set

**Algorithm**:
```text
function GOTO(I, X):
    J = {}
    for each item [A → α · Xβ, a] in I:
        add [A → αX · β, a] to J
    return CLOSURE(J)
```

**Time Complexity**: O(|I| × |productions|)

### LR(1) Table Construction

**Algorithm**:
```text
function build-LR1-table(grammar):
    C = {CLOSURE({[S' → · S, #]})}
    worklist = C
    
    while worklist is not empty:
        I = pop(worklist)
        
        for each symbol X (terminal or non-terminal):
            J = GOTO(I, X)
            if J is not empty and J not in C:
                add J to C
                add J to worklist
            
            if X is terminal:
                ACTION[I, X] = shift(J)
            else:
                GOTO[I, X] = J
        
        for each item [A → α ·, a] in I:
            if A is not S':
                ACTION[I, a] = reduce(A → α)
            else:
                ACTION[I, #] = accept
    
    return (ACTION, GOTO)
```

**Conflict Resolution**:
- **Shift-Reduce Conflict**: Prefer shift (default)
- **Reduce-Reduce Conflict**: Grammar error (ambiguous)

**See Also**: [LR Parser Technical Documentation](../04-syntax-analysis/parsing-tables-and-algorithms.md)

### LR Parser Runtime Algorithm

**Algorithm**:
```text
function LR-parse(tokens, ACTION, GOTO):
    stack = [0]  // Initial state
    tokens.append(EOF)
    ip = 0
    
    while true:
        state = top(stack)
        token = tokens[ip]
        action = ACTION[state, token]
        
        if action is shift(s):
            push(token, stack)
            push(s, stack)
            ip++
        
        else if action is reduce(A → β):
            pop(2*|β|) from stack  // Remove |β| symbols and states
            state = top(stack)
            push(A, stack)
            push(GOTO[state, A], stack)
            // Build parse tree node
        
        else if action is accept:
            return success
        
        else:
            error("Syntax error")
```

**Time Complexity**: O(n) where n = input length

## Error Recovery

### Panic Mode Recovery

**Strategy**: Skip tokens until synchronization point

**Algorithm**:
```text
function panic-recovery(state, tokens, ip):
    // Find synchronization tokens
    sync_tokens = {TOCKAZAREZ, D_VIT_ZAGRADA, ...}
    
    // Skip tokens until sync token found
    while ip < tokens.length:
        if tokens[ip] in sync_tokens:
            return ip
        ip++
    
    return tokens.length  // End of input
```

**Limitations**: May skip valid code, multiple error messages

### Error Productions

**Strategy**: Add error productions to grammar

**Example**:
```
statement → error ';'
```

**Advantages**: More precise recovery
**Disadvantages**: Increases grammar complexity

## Lookahead Computation

### FIRST Set Computation

**Algorithm**:
```text
function compute-FIRST(grammar):
    FIRST = {}  // Map from symbol to set of terminals
    
    // Initialize terminals
    for each terminal t:
        FIRST[t] = {t}
    
    // Initialize non-terminals
    for each non-terminal A:
        FIRST[A] = {}
    
    // Iterate until fixed point
    changed = true
    while changed:
        changed = false
        for each production A → X₁X₂...Xₖ:
            for i = 1 to k:
                old_size = |FIRST[A]|
                FIRST[A] ∪= FIRST[Xᵢ] - {ε}
                if ε not in FIRST[Xᵢ]:
                    break
                if i == k:
                    FIRST[A] ∪= {ε}
                if |FIRST[A]| > old_size:
                    changed = true
    
    return FIRST
```

**Time Complexity**: O(n³) worst case, typically O(n²)

### FIRST_k Sets

**FIRST_k(α)** = first k terminals of strings derivable from α

**Uses**: LR(k) parsing, better conflict resolution

**Computation**: Similar to FIRST but track k-length prefixes

## Parse Tree Construction

### Bottom-Up Tree Building

**Algorithm**: Build tree during reduce actions

```text
function reduce(A → β):
    // Pop children from parse stack
    children = []
    for i = 1 to |β|:
        children.append(pop(parse_stack))
    
    // Create parent node
    node = new Node(A, children)
    push(node, parse_stack)
```

**Tree Structure**: Mirrors derivation structure

### Abstract Syntax Tree (AST)

**Purpose**: Simplify parse tree by removing non-semantic nodes

**Simplification Rules**:
- Remove chain productions: `E → T`, `T → F`
- Flatten lists: `List → List Item` → `List → Item Item ...`
- Remove parentheses nodes

**See Also**: [Intermediate Representation](../06-intermediate-representation/ast-structure-and-walkers.md)

## Further Reading

- **[Formal Languages and Grammars](formal-languages-and-grammars.md)**: Formal language theory
- **[Lexical Analysis](../03-lexical-analysis/lexer-design.md)**: Application to lexer
- **[Syntax Analysis](../04-syntax-analysis/parser-construction.md)**: Application to parser

---

*These algorithms form the computational foundation of the PPJ compiler. Understanding them is essential for comprehending the implementation details.*
