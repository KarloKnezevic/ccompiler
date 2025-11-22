# Lexer Consistency Check

This document verifies that the lexer implementation correctly follows the specified algorithms.

## Algorithm A — Main Lexer Loop

**Requirement**: The generator constructs automaton tables/states. The runtime lexer reads input and produces a sequence of uniform characters (tokens).

**Status**: ✅ **IMPLEMENTED**
- `LexerGenerator` constructs DFA for each state
- `Lexer.tokenize()` reads input and produces tokens
- **NOTE**: Uses DFA instead of ε-NFA, which is acceptable per specification

**Implementation Details**:
- DFAs are generated for each lexer state
- Runtime lexer uses appropriate DFA based on current state
- Tokens are created and added to symbol table
- Output format includes symbol table and token stream

## Algorithm B — Ambiguity Resolution (Priorities)

### P2 (Maximal Munch)

**Requirement**: The longest recognized sequence has priority.

**Status**: ✅ **IMPLEMENTED**
- `Lexer.scanToken()` implements maximal munch algorithm
- Tracks longest match as DFA is traversed
- Updates `longestMatch` whenever an accepting state is encountered
- When no more transitions are available, uses the longest match found

**Implementation**:
```java
int longestMatch = 0;
int lastAcceptingState = -1;
int lastAcceptingMatch = 0;

for (int i = 0; i < input.length(); i++) {
  // ... follow transitions ...
  if (dfa.isAccepting(currentState)) {
    longestMatch = matchLength;  // Update longest match
    lastAcceptingState = currentState;
    lastAcceptingMatch = matchLength;
  }
}
```

### P3 (Rule Order)

**Requirement**: If length is equal, the earlier rule has priority.

**Status**: ✅ **IMPLEMENTED**
- `NFAToDFAConverter` sorts accepting states by `nfaStateRuleOrder`
- Earlier rules have lower index and win
- Rule order is preserved during DFA construction

**Implementation**:
```java
// Sort by rule order (earlier rules have lower order)
acceptingStates.sort((a, b) -> {
  int orderA = nfaStateRuleOrder.getOrDefault(a, Integer.MAX_VALUE);
  int orderB = nfaStateRuleOrder.getOrDefault(b, Integer.MAX_VALUE);
  return Integer.compare(orderA, orderB);
});
```

**Tests**:
- ✅ Test: longer vs. shorter match (maximal munch)
- ✅ Test: same length, different rules (rule priority)
- ✅ Test: `int` vs `IDN` (keyword wins due to earlier rule)
- ✅ Test: `++` vs `+` (longer match wins)

## Algorithm C — Error Recovery

**Requirement**: If an unrecognized character is encountered, discard the first character, print error, and continue.

**Status**: ✅ **IMPLEMENTED**
- `Lexer.tokenize()` implements panic mode recovery
- Unrecognized characters trigger error message to stderr
- First character is discarded from buffer
- Tokenization continues after error

**Implementation**:
```java
if (buffer.length() > 0) {
  char c = buffer.charAt(0);
  System.err.println(String.format(
      "Leksička greška na retku %d, stupcu %d: neprepoznat znak '%c' (0x%02x)", 
      errorLine, errorCol, c, (int)c));
  buffer.deleteCharAt(0);
  // Update position and continue
}
```

**Special Cases**:
- ✅ Unterminated string literals: Detected and reported with specific error message
- ✅ Error recovery exits string state and continues tokenization

## VRATI_SE (Backtrack) Implementation

**Requirement**: Of the matched characters, group the first `n` into the lexical token, return the rest to the input stream.

**Status**: ✅ **IMPLEMENTED**
- `Lexer.scanToken()` processes `VRATI_SE n` actions
- Adjusts match length based on backtrack value
- Returns characters to buffer correctly

**Implementation**:
```java
int actualMatch;
if (backtrack != null && backtrack >= 0 && backtrack <= longestMatch) {
  actualMatch = backtrack;  // Keep first n characters
} else {
  actualMatch = longestMatch;  // Keep entire match
}

input.delete(0, actualMatch);
// Characters from actualMatch to longestMatch remain in buffer
```

**Tests**:
- ✅ `VRATI_SE 0`: No characters consumed (for state entry)
- ✅ `VRATI_SE n` (n > 0): First n characters consumed, rest returned

## State Management

**Requirement**: Lexer maintains current state, transitions occur via `UDJI_U_STANJE` actions.

**Status**: ✅ **IMPLEMENTED**
- `LexerState` class tracks current state
- `UDJI_U_STANJE` actions change state
- Correct DFA is used based on current state

**States**:
- ✅ `S_pocetno`: Initial state
- ✅ `S_string`: String literal state
- ✅ `S_komentar`: Multi-line comment state
- ✅ `S_jednolinijskiKomentar`: Single-line comment state

## Symbol Table

**Requirement**: All tokens are added to symbol table. Tokens with same type and text share the same index.

**Status**: ✅ **IMPLEMENTED**
- `Lexer.getOrAddSymbol()` implements symbol table
- Linear search for existing entries
- Tokens with same (type, text) pair share index

**Output Format**:
- ✅ Symbol table: `indeks   uniformni znak   izvorni tekst`
- ✅ Token stream: `uniformni znak    redak    indeks u tablicu znakova`

## Summary

All specified algorithms are correctly implemented:

- ✅ Algorithm A: Main lexer loop
- ✅ Algorithm B (P2): Maximal munch
- ✅ Algorithm B (P3): Rule priority
- ✅ Algorithm C: Error recovery
- ✅ VRATI_SE: Backtracking
- ✅ State management
- ✅ Symbol table

The implementation follows the specifications and handles all edge cases correctly.
