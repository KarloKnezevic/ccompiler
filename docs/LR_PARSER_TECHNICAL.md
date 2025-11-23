# LR(1) Parser - Technical Documentation

## Overview

This document describes the detailed implementation of a canonical LR(1) syntax analyzer for the PPJ language. The parser uses the LR(1) algorithm to generate ACTION and GOTO tables and a runtime parser to parse tokens into trees.

## 1. Input Grammar Definition

### 1.1. Grammar Definition File

The grammar definition is located in:

```
config/parser_definition.txt
```

**Important:** The parser must read exactly this format. There is no tolerance for deviations.

### 1.2. File Format

The input file defines:

#### `%V` - Non-terminal Set

The set of non-terminals. The first non-terminal is the start symbol.

**Format:**
```
%V <non_terminal1> <non_terminal2> ... <non_terminalN>
```

**Example:**
```
%V <prijevodna_jedinica> <vanjska_deklaracija> <deklaracija> ...
```

#### `%T` - Terminal Set

The set of terminals (tokens).

**Format:**
```
%T TOKEN1 TOKEN2 ... TOKENN
```

**Example:**
```
%T IDN BROJ ZNAK NIZ_ZNAKOVA KR_INT KR_CHAR ...
```

#### `%Syn` - Synchronization Tokens

Synchronization symbols for error recovery. These tokens are used to recover from parse errors.

**Format:**
```
%Syn TOKEN1 TOKEN2 ...
```

**Example:**
```
%Syn TOCKAZAREZ D_VIT_ZAGRADA
```

#### Production Rules

Productions are defined in the following format:

```
<non_terminal>
 <alternative1>
 <alternative2>
 <alternative3>
```

Where:
- The first line (without leading space) is the left-hand side (LHS) non-terminal
- Subsequent lines (with leading space) are alternatives (right-hand sides)
- Multiple alternatives can be specified, each on a separate line with leading space

**Alternative format (also supported):**
```
<non_terminal> ::= <alternative1> | <alternative2> | <alternative3>
```

**Epsilon Production:**

Epsilon (empty production) is denoted by `$`:

```
<non_terminal>
 $
```

**Example:**
```
<lista_naredbi>
 <naredba>
 <lista_naredbi> <naredba>
```

This defines:
- `<lista_naredbi> → <naredba>`
- `<lista_naredbi> → <lista_naredbi> <naredba>`

### 1.3. Grammar Parser Implementation

The `GrammarParser` class reads and parses this exact format:

1. **Reads `%V` line**: Extracts all non-terminals, first one is the start symbol
2. **Reads `%T` line**: Extracts all terminals
3. **Reads `%Syn` line**: Extracts synchronization tokens
4. **Reads productions**: 
   - Lines starting with `<` and ending with `>` (no leading space) are LHS
   - Lines with leading space are alternatives for the current LHS
   - `$` on a line (with leading space) is an epsilon production

**Key Implementation Details:**
- Leading space detection is used to distinguish LHS from alternatives
- Epsilon is represented as empty RHS list internally
- All productions are indexed for fast lookup

## 2. Canonical LR(1) Parser Implementation

### 2.1. Grammar Augmentation

If the start symbol is `S`, the grammar is augmented with:

```
S' → S
```

Where `S'` is a new start symbol (in our implementation: `<pocetni_nezavrsni_znak>`).

**Initial LR(1) Item:**

The initial item set contains:

```
[S' → • S, { # }]
```

Where:
- `•` is the dot position (before `S`)
- `#` is the end-of-input marker
- `{ # }` is the lookahead set

**Implementation:**
```java
Production augmentedStart = grammar.getAugmentedStartProduction();
LRItem initialItem = new LRItem(augmentedStart, 0, Set.of(END_MARKER));
```

### 2.2. FIRST Sets

FIRST sets are computed according to the following rules:

#### Rule 1: Terminal
```
FIRST(a) = { a }    // for terminal a
```

#### Rule 2: Epsilon
```
FIRST(ε) = { ε }    // epsilon (denoted as $)
```

#### Rule 3: Non-terminal X
For non-terminal `X` with productions `X → α₁ | α₂ | ... | αₙ`:

```
FIRST(X) = ⋃ FIRST(αᵢ)  for all productions X → αᵢ
```

Where:
- If `αᵢ` can derive epsilon, add `$` to `FIRST(X)`
- Otherwise, add all terminals from `FIRST(αᵢ)` (excluding `$`)

#### Rule 4: Sequence αβγ
For a sequence of symbols `αβγ`:

```
FIRST(αβγ) = 
    FIRST(α)                         if ε ∉ FIRST(α)
    FIRST(α) ∪ FIRST(βγ)            if ε ∈ FIRST(α)
```

And:
```
ε ∈ FIRST(αβγ)  if and only if  ε ∈ FIRST(α) ∧ ε ∈ FIRST(β) ∧ ε ∈ FIRST(γ)
```

**Implementation:**
```java
public Set<String> computeFirst(String symbol) {
    // Terminal: FIRST(a) = {a}
    if (grammar.isTerminal(symbol)) {
        return Set.of(symbol);
    }
    
    // Non-terminal: compute recursively
    Set<String> first = new HashSet<>();
    for (Production prod : grammar.getProductions(symbol)) {
        if (prod.rhs().isEmpty()) {
            // Epsilon production
            first.add(EPSILON);
        } else {
            boolean allCanDeriveEpsilon = true;
            for (String sym : prod.rhs()) {
                Set<String> symFirst = computeFirst(sym);
                // Add terminals (excluding epsilon)
                for (String term : symFirst) {
                    if (!term.equals(EPSILON)) {
                        first.add(term);
                    }
                }
                if (!symFirst.contains(EPSILON)) {
                    allCanDeriveEpsilon = false;
                    break;
                }
            }
            if (allCanDeriveEpsilon) {
                first.add(EPSILON);
            }
        }
    }
    return first;
}
```

FIRST sets are used to generate LR(1) lookahead sets during item set construction.

### 2.3. CLOSURE Algorithm

The CLOSURE algorithm expands an item set by adding all items that can be derived from existing items.

**Algorithm:**

For an item:
```
A → α • B β, L
```

Where:
- `A → αBβ` is a production
- `•` is the dot position (before `B`)
- `L` is the lookahead set

For each production `B → γ`:

1. Compute `T = FIRST(β)`
2. If `β` can derive epsilon (`β ⇒* ε`), add lookahead `L` to `T`
3. Add item: `[B → • γ, T]`

Repeat until no more items can be added (fixpoint).

**Formal Definition:**

```
CLOSURE(I):
    repeat
        for each item [A → α • B β, a] in I:
            for each production B → γ:
                T = FIRST(β)
                if ε ∈ FIRST(β):
                    T = T ∪ {a}
                add [B → • γ, T] to I
    until no more items can be added
    return I
```

**Implementation:**
```java
public LRItemSet closure(LRItemSet itemSet) {
    LRItemSet result = new LRItemSet(itemSet);
    boolean changed = true;
    int iterations = 0;
    
    while (changed && iterations < MAX_ITERATIONS) {
        changed = false;
        List<LRItem> itemsToProcess = new ArrayList<>(result.getItems());
        
        for (LRItem item : itemsToProcess) {
            if (!item.isReduceItem()) {
                String nextSymbol = item.getNextSymbol();
                
                if (grammar.isNonTerminal(nextSymbol)) {
                    List<Production> prods = grammar.getProductions(nextSymbol);
                    List<String> remaining = item.getRemainingSymbols();
                    
                    // Compute FIRST(remaining)
                    Set<String> firstOfRemaining = new HashSet<>(
                        firstComputer.computeFirst(remaining));
                    
                    // If remaining can derive epsilon, add lookahead from item
                    if (firstComputer.hasEpsilon(remaining)) {
                        firstOfRemaining.addAll(item.getLookahead());
                    }
                    
                    // Add items for each production
                    for (Production prod : prods) {
                        LRItem newItem = new LRItem(prod, 0, firstOfRemaining);
                        if (!result.contains(newItem)) {
                            result.addItem(newItem);
                            changed = true;
                        } else {
                            // Merge lookaheads if item already exists
                            LRItem existing = result.getItem(prod, 0);
                            if (existing != null) {
                                LRItem merged = existing.merge(newItem);
                                if (!merged.getLookahead().equals(existing.getLookahead())) {
                                    result.addItem(merged);
                                    changed = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        iterations++;
    }
    
    return result;
}
```

### 2.4. GOTO Algorithm

The GOTO algorithm computes the transition from one item set to another on a given symbol.

**Algorithm:**

```
goto(I, X) = CLOSURE({ [A → αX • β, L] | [A → α • Xβ, L] ∈ I })
```

Where:
- `I` is an item set
- `X` is a grammar symbol (terminal or non-terminal)
- For each item `[A → α • Xβ, L]` in `I`, add `[A → αX • β, L]` to the result
- Apply CLOSURE to the result

**Implementation:**
```java
public LRItemSet gotoSet(LRItemSet itemSet, String symbol) {
    LRItemSet result = new LRItemSet();
    
    // Find all items with dot before symbol
    for (LRItem item : itemSet.getItems()) {
        if (!item.isReduceItem() && item.getNextSymbol().equals(symbol)) {
            result.addItem(item.advance()); // Move dot forward
        }
    }
    
    if (result.getItems().isEmpty()) {
        return null;
    }
    
    return closure.closure(result); // Apply CLOSURE
}
```

### 2.5. Canonical Collection of LR(1) States

The canonical collection is built iteratively starting from the initial item set.

**Algorithm:**

```
C = {CLOSURE({[S' → • S, {#}]})}
repeat
    for each item set I in C:
        for each grammar symbol X:
            if GOTO(I, X) is not empty and not in C:
                add GOTO(I, X) to C
until no more item sets can be added
```

**Implementation:**
```java
private void buildCanonicalCollection() {
    // Create initial item set
    Production augmentedStart = grammar.getAugmentedStartProduction();
    LRItem initialItem = new LRItem(augmentedStart, 0, Set.of(END_MARKER));
    LRItemSet initialSet = new LRItemSet();
    initialSet.addItem(initialItem);
    initialSet = closure.closure(initialSet);
    
    int state0 = addItemSet(initialSet);
    List<Integer> toProcess = new ArrayList<>();
    toProcess.add(state0);
    
    while (!toProcess.isEmpty()) {
        int currentState = toProcess.remove(0);
        LRItemSet currentSet = stateToItemSet.get(currentState);
        
        // Find all symbols that can follow the dot
        Set<String> symbols = new HashSet<>();
        for (LRItem item : currentSet.getItems()) {
            if (!item.isReduceItem()) {
                symbols.add(item.getNextSymbol());
            }
        }
        
        // Compute GOTO for each symbol
        for (String symbol : symbols) {
            LRItemSet nextSet = gotoOp.gotoSet(currentSet, symbol);
            if (nextSet != null && !nextSet.getItems().isEmpty()) {
                Integer existingState = findExistingState(nextSet);
                if (existingState == null) {
                    // New state
                    int newState = addItemSet(nextSet);
                    toProcess.add(newState);
                }
            }
        }
    }
}
```

**Result:**
- For the PPJ grammar, approximately 823 states are generated
- Each state is a unique set of LR(1) items
- States are mapped to indices for ACTION and GOTO tables

### 2.6. ACTION and GOTO Tables

#### ACTION Table

The ACTION table determines the action for each (state, terminal) pair.

**SHIFT:**

If there is an item:
```
A → α • a β, L
```

And `GOTO(s, a) = t`, then:

```
ACTION[s][a] = SHIFT t
```

**REDUCE:**

If there is an item:
```
A → α •, L
```

Then for each `a ∈ L`:

```
ACTION[s][a] = REDUCE A → α
```

**ACCEPT:**

If there is an item:
```
S' → S •, { # }
```

Then:

```
ACTION[s][#] = ACCEPT
```

**Implementation:**
```java
private void buildActionTable(LRTable table) {
    for (int state = 0; state < itemSets.size(); state++) {
        LRItemSet itemSet = itemSets.get(state);
        Map<String, List<Action>> terminalActions = new HashMap<>();
        
        for (LRItem item : itemSet.getItems()) {
            if (item.isReduceItem()) {
                Production prod = item.getProduction();
                
                // Check for ACCEPT
                if (prod.lhs().equals(grammar.getAugmentedStartSymbol())) {
                    for (String lookahead : item.getLookahead()) {
                        if (lookahead.equals(END_MARKER)) {
                            addAction(terminalActions, lookahead, Action.accept());
                        }
                    }
                } else {
                    // Regular REDUCE
                    int prodIndex = grammar.getProductionIndex(prod);
                    for (String lookahead : item.getLookahead()) {
                        addAction(terminalActions, lookahead, 
                            Action.reduce(prodIndex, prod));
                    }
                }
            } else {
                // SHIFT
                String nextSymbol = item.getNextSymbol();
                if (grammar.isTerminal(nextSymbol)) {
                    LRItemSet nextSet = gotoOp.gotoSet(itemSet, nextSymbol);
                    if (nextSet != null) {
                        Integer nextState = findExistingState(nextSet);
                        if (nextState != null) {
                            addAction(terminalActions, nextSymbol, 
                                Action.shift(nextState));
                        }
                    }
                }
            }
        }
        
        // Resolve conflicts and set actions
        for (Map.Entry<String, List<Action>> entry : terminalActions.entrySet()) {
            Action resolved = resolveConflicts(entry.getValue(), 
                entry.getKey(), state);
            if (resolved != null) {
                table.setAction(state, entry.getKey(), resolved.toString());
            }
        }
    }
}
```

#### GOTO Table

The GOTO table determines the next state for each (state, non-terminal) pair.

**Algorithm:**

```
for each state s in C:
    for each non-terminal A:
        if GOTO(s, A) = t:
            GOTO[s][A] = t
```

**Implementation:**
```java
private void buildGotoTable(LRTable table) {
    for (int state = 0; state < itemSets.size(); state++) {
        LRItemSet itemSet = itemSets.get(state);
        
        for (String nonTerminal : grammar.getNonTerminals()) {
            if (!nonTerminal.equals(grammar.getAugmentedStartSymbol())) {
                LRItemSet nextSet = gotoOp.gotoSet(itemSet, nonTerminal);
                if (nextSet != null) {
                    Integer nextState = findExistingState(nextSet);
                    if (nextState != null) {
                        table.setGoto(state, nonTerminal, nextState);
                    }
                }
            }
        }
    }
}
```

### 2.7. Conflict Resolution

LR(1) parsers can have conflicts that must be resolved.

#### SHIFT/REDUCE Conflict

When both SHIFT and REDUCE actions exist for the same (state, terminal) pair.

**Resolution Rule:**
Always choose SHIFT.

**Implementation:**
```java
if (shiftAction != null && !reduceActions.isEmpty()) {
    LOG.warning(String.format(
        "SHIFT/REDUCE conflict in state %d for terminal %s: choosing SHIFT",
        state, terminal));
    return shiftAction;
}
```

#### REDUCE/REDUCE Conflict

When multiple REDUCE actions exist for the same (state, terminal) pair.

**Resolution Rule:**
Choose the reduction of the production that was defined earlier in `parser_definition.txt`.

**Implementation:**
```java
if (reduceActions.size() > 1) {
    Action chosen = reduceActions.get(0);
    for (Action action : reduceActions) {
        if (action.productionIndex() < chosen.productionIndex()) {
            chosen = action;
        }
    }
    LOG.warning(String.format(
        "REDUCE/REDUCE conflict in state %d for terminal %s: " +
        "choosing production %s (index %d)",
        state, terminal, 
        chosen.production().lhs() + " -> " + 
        String.join(" ", chosen.production().rhs()), 
        chosen.productionIndex()));
    return chosen;
}
```

**Important:** All conflicts must be logged, but execution proceeds according to the rules above.

## 3. Runtime Syntax Analyzer

### 3.1. Input Format

The analyzer receives exactly the lexer output (identical format from the `compiler-lexer` module):

```
UNIFORMNI_ZNAK REDAK LEKSIČKA_JEDINKA
```

**Example:**
```
KR_INT        1      0
IDN           1      1
L_ZAGRADA     1      2
D_ZAGRADA     1      3
```

Where:
- `UNIFORMNI_ZNAK` is the token type (uniform symbol)
- `REDAK` is the line number
- `LEKSIČKA_JEDINKA` is the lexical unit (value)

### 3.2. Data Structures

The parser uses two stacks:

1. **State Stack**: Stores parser states (integers)
2. **Tree Stack**: Stores parse tree nodes

**Initialization:**
```java
Stack<Integer> stateStack = new Stack<>();
Stack<ParseTree> treeStack = new Stack<>();

stateStack.push(0); // Initial state
```

### 3.3. Operations

#### SHIFT Operation

**Algorithm:**
1. Look up `ACTION[state][token] = SHIFT t`
2. Push state `t` onto state stack
3. Create a leaf node (terminal) with:
   - Token type
   - Line number
   - Lexical unit
4. Push leaf node onto tree stack
5. Advance to next token

**Implementation:**
```java
if (action.startsWith("s")) {
    int nextState = Integer.parseInt(action.substring(1));
    stateStack.push(nextState);
    
    // Create leaf node for terminal
    ParseTree leaf = new ParseTree(
        token.type(), 
        token.line(), 
        token.lexicalUnit());
    treeStack.push(leaf);
    
    tokenIndex++;
}
```

#### REDUCE Operation

**Algorithm:**
1. Look up `ACTION[state][token] = REDUCE A → α`
2. Pop `|α|` states from state stack
3. Pop `|α|` nodes from tree stack (these are the RHS children)
4. Create a new parent node `A` with the popped children
5. Look up `GOTO[state][A] = t`
6. Push state `t` onto state stack
7. Push parent node onto tree stack
8. Do NOT advance token (reduction doesn't consume input)

**Special Case - Epsilon Production:**

If `α = ε` (empty RHS), no nodes are popped, but a parent node is still created.

**Implementation:**
```java
if (action.startsWith("r")) {
    int productionIndex = Integer.parseInt(action.substring(1));
    Production prod = grammar.getProduction(productionIndex);
    
    // Pop RHS symbols (in reverse order)
    List<ParseTree> children = new ArrayList<>();
    if (!prod.rhs().isEmpty() && !prod.rhs().get(0).equals("$")) {
        for (int i = 0; i < prod.rhs().size(); i++) {
            stateStack.pop();
            children.add(0, treeStack.pop()); // Insert at beginning
        }
    }
    Collections.reverse(children); // Ensure correct order
    
    // Create parent node
    ParseTree parent = new ParseTree(prod.lhs());
    parent.addChildren(children);
    treeStack.push(parent);
    
    // GOTO
    int gotoState = table.getGoto(stateStack.peek(), prod.lhs());
    if (gotoState < 0) {
        throw new ParseException("Invalid GOTO for " + prod.lhs());
    }
    stateStack.push(gotoState);
    
    // Don't advance token - reduce doesn't consume input
}
```

#### ACCEPT Operation

**Algorithm:**
1. Look up `ACTION[state][#] = ACCEPT`
2. Verify tree stack has exactly one node (the root)
3. Return the root node

**Implementation:**
```java
if (action.equals("acc")) {
    if (treeStack.size() != 1) {
        throw new ParseException(
            "Accept condition met, but stack is not in expected state.");
    }
    return treeStack.pop();
}
```

### 3.4. Main Parsing Loop

**Algorithm:**

```
stack = [0]
treeStack = []
tokens = inputTokens + [#]

while true:
    state = stack.top()
    token = tokens[currentIndex]
    action = ACTION[state][token.type]
    
    if action == SHIFT t:
        stack.push(t)
        treeStack.push(leafNode(token))
        advance()
    
    else if action == REDUCE A → α:
        pop |α| from stack
        pop |α| nodes from treeStack
        parent = createNode(A, poppedNodes)
        treeStack.push(parent)
        gotoState = GOTO[stack.top()][A]
        stack.push(gotoState)
    
    else if action == ACCEPT:
        return treeStack.pop()
    
    else:
        error()
```

**Implementation:**
```java
public ParseTree parse(List<Token> tokens) throws ParseException {
    Stack<Integer> stateStack = new Stack<>();
    Stack<ParseTree> treeStack = new Stack<>();
    
    stateStack.push(0); // Initial state
    
    // Add end marker
    List<Token> tokensWithEnd = new ArrayList<>(tokens);
    tokensWithEnd.add(new Token(END_MARKER, 
        tokens.isEmpty() ? 1 : tokens.get(tokens.size() - 1).line(), ""));
    
    int tokenIndex = 0;
    
    while (tokenIndex < tokensWithEnd.size()) {
        Token token = tokensWithEnd.get(tokenIndex);
        int currentState = stateStack.peek();
        
        String action = table.getAction(currentState, token.type());
        
        if (action == null) {
            handleError(token, currentState, stateStack, treeStack);
            tokenIndex++;
            continue;
        }
        
        if (action.equals("acc")) {
            // ACCEPT
            if (treeStack.size() != 1) {
                throw new ParseException("Expected single root node on accept");
            }
            return treeStack.pop();
        } else if (action.startsWith("s")) {
            // SHIFT
            int nextState = Integer.parseInt(action.substring(1));
            stateStack.push(nextState);
            treeStack.push(new ParseTree(token.type(), token.line(), token.lexicalUnit()));
            tokenIndex++;
        } else if (action.startsWith("r")) {
            // REDUCE
            int productionIndex = Integer.parseInt(action.substring(1));
            Production prod = grammar.getProduction(productionIndex);
            
            List<ParseTree> children = new ArrayList<>();
            if (!prod.rhs().isEmpty() && !prod.rhs().get(0).equals("$")) {
                for (int i = 0; i < prod.rhs().size(); i++) {
                    stateStack.pop();
                    children.add(0, treeStack.pop());
                }
            }
            Collections.reverse(children);
            
            ParseTree parent = new ParseTree(prod.lhs());
            parent.addChildren(children);
            treeStack.push(parent);
            
            int gotoState = table.getGoto(stateStack.peek(), prod.lhs());
            if (gotoState < 0) {
                throw new ParseException("Invalid GOTO for " + prod.lhs());
            }
            stateStack.push(gotoState);
        } else {
            throw new ParseException("Unknown action: " + action);
        }
    }
    
    throw new ParseException("End of input reached without accept");
}
```

### 3.5. Error Handling

When `ACTION[state][token]` is undefined (null), an error occurs. The parser attempts error recovery using synchronization tokens.

**Error Recovery Algorithm:**
1. Log the error with line number and token information
2. Skip tokens until a synchronization token is found
3. Pop states from the stack until a state with a valid GOTO for a synchronization non-terminal is found
4. Push the GOTO state and continue parsing

**Implementation:**
```java
private void handleError(Token token, int currentState, 
                        Stack<Integer> stateStack,
                        Stack<ParseTree> treeStack) throws ParseException {
    LOG.warning(String.format(
        "Parse error at line %d, token %s ('%s')",
        token.line(), token.type(), token.value()));
    
    // Panic mode error recovery
    List<String> syncTokens = grammar.getSyncTokens();
    
    // TODO: Implement full error recovery
    throw new ParseException(String.format(
        "Parse error at line %d: unexpected token %s",
        token.line(), token.type()));
}
```

## 4. Tree Generation

### 4.1. Generative Tree

The generative tree is the complete parse tree showing the derivation process. Every grammar production is represented as a node.

**Format:**
```
0:<symbol>
    1:<child1>
        2:<child2>
    ...
```

Where:
- Numbers are node indices (for reference)
- Indentation shows tree structure
- Terminals include lexical units: `TOKEN , value`

### 4.2. Syntax Tree (AST)

The syntax tree is a simplified, compact representation optimized for semantic analysis and code generation. Intermediate grammar nodes that don't add semantic value are removed.

**Optimizations:**
- Skip wrapper nodes with single children
- Skip redundant list nodes
- Focus on semantically important structures

**Format:**
Same as generative tree, but with simplified structure.

## 5. Performance

### 5.1. Time Complexity

- **FIRST sets**: O(n²) where n is the number of productions
- **CLOSURE**: O(n) per iteration, maximum k iterations
- **GOTO**: O(n) where n is the number of items in the set
- **Canonical collection**: O(s × m) where s is the number of states, m is the number of symbols
- **Parsing**: O(n) where n is the number of tokens

### 5.2. Space Complexity

- **ACTION table**: O(s × t) where s is the number of states, t is the number of terminals
- **GOTO table**: O(s × n) where s is the number of states, n is the number of non-terminals
- **Total**: ~823 states × (~47 terminals + ~47 non-terminals) ≈ 77,000 entries

### 5.3. Caching

Parsing tables are serialized to `target/parser-cache/lr_table.ser` and loaded on subsequent runs, significantly speeding up testing.

## 6. Testing

### 6.1. Unit Tests

- `GrammarTest`: Tests grammar parsing and augmentation
- `FirstSetTest`: Tests FIRST set computation
- `LRItemSetTest`: Tests item merging with different lookahead sets

### 6.2. Integration Tests

- `ParserGoldenTest`: Tests output file generation
- `CompilerBinOutputTest`: Verifies compiler-bin files are generated correctly
- `LRTableGenerationTest`: Tests table generation

## 7. References

1. Srbljić, Siniša (2007). *Prevođenje programskih jezika*. Element, Zagreb. ISBN 978-953-197-625-1.

2. Aho, A. V., Lam, M. S., Sethi, R., & Ullman, J. D. (2006). *Compilers: Principles, Techniques, and Tools* (2nd ed.). Pearson Education.

## 8. Implementation Notes

### 8.1. Code Organization

- **Grammar parsing**: `hr.fer.ppj.parser.grammar` package
- **LR(1) algorithms**: `hr.fer.ppj.parser.lr` package
- **Tree generation**: `hr.fer.ppj.parser.tree` package
- **I/O**: `hr.fer.ppj.parser.io` package
- **Configuration**: `hr.fer.ppj.parser.config` package

### 8.2. Key Design Decisions

1. **Immutable data structures**: Grammar, productions, and items are immutable
2. **Caching**: LR tables are cached to avoid regeneration
3. **Error logging**: All conflicts and errors are logged
4. **Modularity**: Each algorithm is in its own class
5. **Type safety**: Sealed interfaces for AST nodes (future extension)

### 8.3. Future Enhancements

1. **AST Builder**: Convert ParseTree to typed AST nodes
2. **Better error recovery**: Implement full panic mode with synchronization
3. **Incremental parsing**: Support for parsing partial programs
4. **Error messages**: More detailed and helpful error messages
