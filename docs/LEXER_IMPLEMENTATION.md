# Lexical Analyzer - Technical Implementation Documentation

## Table of Contents

1. [Overview](#overview)
2. [Language Subset](#language-subset)
3. [Lexer Specification Format](#lexer-specification-format)
4. [Lexer Generation Pipeline](#lexer-generation-pipeline)
5. [Specification Parsing](#specification-parsing)
6. [Regular Expression to ε-NFA Construction](#regular-expression-to-ε-nfa-construction)
7. [ε-NFA to NFA Conversion](#ε-nfa-to-nfa-conversion)
8. [NFA to DFA Conversion (Subset Construction)](#nfa-to-dfa-conversion-subset-construction)
9. [Lexer Runtime - Token Matching](#lexer-runtime---token-matching)
10. [Error Recovery](#error-recovery)
11. [State Management](#state-management)
12. [Action Processing](#action-processing)
13. [Symbol Table](#symbol-table)

---

## Overview

This lexer generator implements a complete lexical analysis system for a subset of the C programming language. The implementation follows a multi-stage pipeline:

1. **Specification Parsing**: Reads and parses the lexer definition file (`config/lexer_definition.txt`)
2. **Macro Expansion**: Recursively expands macro definitions
3. **ε-NFA Construction**: Converts regular expressions to epsilon-Nondeterministic Finite Automata using Thompson's construction
4. **NFA Construction**: Removes epsilon transitions (implicit in ε-NFA)
5. **DFA Conversion**: Converts NFA to Deterministic Finite Automaton using subset construction algorithm
6. **Runtime Tokenization**: Uses generated DFAs to tokenize input text with maximal munch and rule priority

The lexer supports multiple states (for handling comments, string literals, etc.), actions (state transitions, backtracking, line counting), and comprehensive error recovery.

---

## Language Subset

This lexer focuses on a subset of the C programming language with the following features:

### Keywords

The lexer recognizes the following C keywords:

- **Control Flow**: `break`, `else`, `return`, `if`, `while`, `for`, `continue`
- **Types**: `char`, `int`, `void`, `const`, `struct`, `float`

### Special Characters and Operators

The lexer recognizes the following special characters and operators:

#### Brackets and Delimiters
- `[` `]` - Array brackets
- `(` `)` - Parentheses
- `{` `}` - Braces
- `;` - Semicolon
- `,` - Comma
- `.` - Dot

#### Arithmetic Operators
- `+` - Plus
- `-` - Minus
- `*` - Asterisk (multiplication or pointer dereference)
- `/` - Division
- `%` - Modulo

#### Increment/Decrement
- `++` - Increment operator
- `--` - Decrement operator

#### Comparison Operators
- `<` - Less than
- `>` - Greater than
- `<=` - Less than or equal
- `>=` - Greater than or equal
- `==` - Equality
- `!=` - Inequality

#### Logical Operators
- `&&` - Logical AND
- `||` - Logical OR
- `!` - Logical NOT

#### Bitwise Operators
- `&` - Bitwise AND
- `|` - Bitwise OR
- `^` - Bitwise XOR
- `~` - Bitwise NOT

#### Assignment
- `=` - Assignment operator

#### Other
- `_` - Underscore (allowed in identifiers)

### Identifiers and Literals

- **Identifiers**: Start with letter or underscore, followed by letters, digits, or underscores
- **Numbers**: Integer and floating-point literals
- **Character Literals**: Single characters enclosed in single quotes
- **String Literals**: Sequences of characters enclosed in double quotes
- **Comments**: Single-line (`//`) and multi-line (`/* */`) comments

---

## Lexer Specification Format

The lexer definition file (`config/lexer_definition.txt`) uses the PPJ format with the following structure:

### 1. Macro Definitions

Macros define reusable regular expression patterns:

```
{macroName} pattern
```

**Example:**
```
{znak} a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z|A|B|C|D|E|F|G|H|I|J|K|L|M|N|O|P|Q|R|S|T|U|V|W|X|Y|Z
{znamenka} 0|1|2|3|4|5|6|7|8|9
```

### 2. State Declarations

Declares all lexer states:

```
%X state1 state2 state3 ...
```

**Example:**
```
%X S_pocetno S_komentar S_jednolinijskiKomentar S_string
```

### 3. Token Declarations

Declares all token types:

```
%L TOKEN1 TOKEN2 TOKEN3 ...
```

**Example:**
```
%L IDN BROJ ZNAK NIZ_ZNAKOVA KR_BREAK KR_CHAR KR_CONST ...
```

### 4. Lexer Rules

Defines matching rules for each state:

```
<state>pattern { actions }
```

**Example:**
```
<S_pocetno>"int"
{
KR_INT
}

<S_pocetno>{znak}({znak}|{znamenka}|_)*
{
IDN
}
```

---

## Lexer Generation Pipeline

The lexer generation process consists of the following stages:

### Stage 1: Specification Parsing

**Class**: `LexerSpecParser`

The parser reads the specification file line by line and extracts:

1. **Macro Definitions**: Stored in a `Map<String, String>`
2. **State Declarations**: Stored in a `List<String>`
3. **Token Declarations**: Stored in a `List<String>`
4. **Lexer Rules**: Stored as `LexerRule` records containing:
   - State name
   - Pattern string
   - Token type (may be null for whitespace/comments)
   - List of actions

**Pattern Processing**:
- Quoted patterns like `"break"` are treated as literal strings (quotes removed)
- Quoted patterns containing regex operators like `"({macro}|\\")*"` are treated as regex patterns (quotes preserved)
- Escape sequences are processed: `\\`, `\"`, `\n`, `\t`, `\_` (underscore → space)

### Stage 2: Macro Expansion

**Class**: `LexerGenerator.expandMacros()`

Macros are expanded recursively:

1. **Macro-to-Macro Expansion**: Macros that reference other macros are expanded first
2. **Pattern Expansion**: When processing rules, macro references like `{znak}` are replaced with their values
3. **Precedence Preservation**: All macro values are wrapped in parentheses to preserve operator precedence

**Algorithm**:
```java
while (changed && iterations < 100) {
  changed = false;
  for (each macro) {
    if (macro value contains {otherMacro}) {
      replace {otherMacro} with (otherMacroValue)
      changed = true;
    }
  }
}
```

### Stage 3: ε-NFA Construction

**Class**: `RegexParser`

Each regex pattern is converted to an epsilon-NFA using **Thompson's construction algorithm**.

#### Supported Operators

- **Concatenation**: `ab` (implicit, no operator)
- **Union**: `a|b` (alternation)
- **Kleene Star**: `a*` (zero or more)
- **Groups**: `(a|b)*` (parentheses for grouping)
- **Epsilon**: `$` (empty string)

#### Thompson's Construction Algorithm

The algorithm recursively processes the regex pattern:

1. **Union (`|`)**: Creates epsilon transitions from a start state to each alternative, and from each alternative to an end state
2. **Concatenation**: Chains states sequentially
3. **Kleene Star (`*`)**: Creates a loop with epsilon transitions
4. **Single Character**: Creates a simple two-state automaton with a character transition
5. **Groups `()`**: Processed recursively, then integrated into the parent automaton

**Example**: Pattern `a|b*`

```
     ε
   ──→ [0] ──→ [1] (a)
   │
   │    ε         ε
   ──→ [2] ──→ [3] ──→ [4] (b)
        ↑      │
        └──ε───┘
```

### Stage 4: NFA Combination

**Class**: `LexerGenerator.buildDFAForState()`

For each lexer state, all rules are combined into a single ε-NFA:

1. Create a common start state
2. For each rule:
   - Build an ε-NFA for the rule's pattern
   - Add an epsilon transition from the common start state to the pattern's start state
   - Copy all transitions and states from the pattern NFA to the combined NFA
   - Mark the pattern's end state as accepting, storing the token type and actions

**Important**: Epsilon transitions from pattern NFAs must be copied **before** adding the transition from the common start state, to ensure epsilon closure works correctly.

### Stage 5: ε-NFA to NFA Conversion

**Note**: In this implementation, epsilon transitions are preserved in the NFA structure. The conversion to DFA handles epsilon closures directly.

The NFA structure maintains:
- **Regular transitions**: `Map<State, Map<Character, Set<State>>>`
- **Epsilon transitions**: `Map<State, Set<State>>`
- **Accepting states**: `Set<State>`

### Stage 6: NFA to DFA Conversion (Subset Construction)

**Class**: `NFAToDFAConverter`

The **subset construction algorithm** converts the NFA to a DFA:

#### Algorithm Steps

1. **Initialization**:
   - Start with epsilon closure of NFA start state: `ε-closure({s0})`
   - Create first DFA state from this NFA state set

2. **State Processing**:
   - For each unprocessed DFA state (representing a set of NFA states):
     - For each input symbol `a`:
       - Compute `move(S, a)` = set of NFA states reachable from states in `S` on symbol `a`
       - Compute `ε-closure(move(S, a))`
       - If this set is new, create a new DFA state
       - Add transition: `DFA[S, a] = newState`

3. **Accepting States**:
   - A DFA state is accepting if it contains at least one accepting NFA state
   - If multiple accepting NFA states exist, use rule priority (earlier rules win)
   - Store token type and actions from the highest-priority accepting state

4. **Termination**:
   - Process continues until all DFA states have been processed
   - Result is a complete DFA with deterministic transitions

#### Epsilon Closure

The epsilon closure of a set of states `S` is computed as:

```
ε-closure(S):
  closure = S
  while (changed):
    changed = false
    for each state s in closure:
      for each state t reachable via ε from s:
        if t not in closure:
          add t to closure
          changed = true
  return closure
```

#### Rule Priority (P3)

When multiple rules match the same input, the **earlier rule in the specification wins**. This is implemented by:

1. Assigning each rule an index (0, 1, 2, ...) based on its position in the specification
2. When a DFA state contains multiple accepting NFA states, sorting them by rule index
3. Using the accepting state with the lowest index (earliest rule)

---

## Lexer Runtime - Token Matching

**Class**: `Lexer`

The runtime lexer uses the generated DFAs to tokenize input text.

### Main Tokenization Loop

```java
while (has input) {
  1. Read line into buffer
  2. While buffer not empty:
     a. Attempt to match token using current state's DFA
     b. If match found:
        - Extract matched text
        - Process actions (VRATI_SE, UDJI_U_STANJE, NOVI_REDAK)
        - Create token and add to symbol table
        - Remove consumed characters from buffer
     c. If no match:
        - Check for state change (VRATI_SE 0)
        - If in S_string and no match: handle unterminated string
        - Otherwise: apply error recovery (Algorithm C)
}
```

### Maximal Munch Algorithm (P2)

The lexer implements **maximal munch**: always selects the longest possible match.

**Algorithm**:
```java
int longestMatch = 0;
int currentState = dfa.getStartState();
int matchLength = 0;
int lastAcceptingState = -1;
int lastAcceptingMatch = 0;

for (int i = 0; i < input.length(); i++) {
  char c = input.charAt(i);
  Integer nextState = dfa.getTransition(currentState, c);
  
  if (nextState == null) {
    // No transition - use last accepting state if available
    if (lastAcceptingState != -1) {
      longestMatch = lastAcceptingMatch;
    }
    break;
  }
  
  currentState = nextState;
  matchLength++;
  
  if (dfa.isAccepting(currentState)) {
    // Update longest match
    longestMatch = matchLength;
    lastAcceptingState = currentState;
    lastAcceptingMatch = matchLength;
  }
}
```

**Special Case - String Literals**:
- When matching a string literal (`NIZ_ZNAKOVA`), the lexer stops at the closing quote
- This prevents over-matching (e.g., `"a"+"b"` should produce three tokens, not one)
- Escaped quotes (`\"`) are handled correctly (they don't terminate the string)

### Rule Priority (P3)

If multiple rules match the same length, the **earlier rule wins**. This is already handled during DFA construction (see Rule Priority section above).

---

## Error Recovery

**Algorithm C - Panic Mode Recovery**

When the lexer encounters an unrecognized character (no transition in the DFA), it applies error recovery:

1. **Error Reporting**:
   - Print error message to `stderr` with line and column number
   - Format: `"Leksička greška na retku %d, stupcu %d: neprepoznat znak '%c' (0x%02x)"`

2. **Recovery**:
   - Discard the first character from the input buffer
   - Update position (line/column)
   - Continue tokenization

### Special Error Handling - Unterminated Strings

When in `S_string` state and no match is found:

1. **Detection**:
   - Check if buffer starts with newline (string terminated by newline)
   - Or if no valid match exists (string never terminated)

2. **Error Reporting**:
   - Find the start of the string (search backwards for opening `"`)
   - Report error at the string's start position
   - Format: `"Leksička greška na retku %d, stupcu %d: nezatvoren string literal"`

3. **Recovery**:
   - Discard all characters up to and including the newline (or EOF)
   - Exit `S_string` state and return to `S_pocetno`
   - Continue tokenization

---

## State Management

The lexer maintains a current state that determines which DFA is used for matching.

### States

- **S_pocetno**: Initial state, handles most tokens
- **S_string**: String literal state, handles characters within string literals
- **S_komentar**: Multi-line comment state (`/* ... */`)
- **S_jednolinijskiKomentar**: Single-line comment state (`// ...`)

### State Transitions

State transitions occur when `UDJI_U_STANJE` actions are executed:

```java
if (action.startsWith("UDJI_U_STANJE")) {
  String[] parts = action.split("\\s+");
  if (parts.length > 1) {
    lexerState.enterState(parts[1]);
  }
}
```

### State Entry Example

When entering `S_string`:
```
<S_pocetno>"
{
-
UDJI_U_STANJE S_string
VRATI_SE 0
}
```

- `VRATI_SE 0` means: consume 0 characters (the `"` remains in buffer)
- `UDJI_U_STANJE S_string` changes state to `S_string`
- The next tokenization step will use the `S_string` DFA, which will match the `"` and the string content

---

## Action Processing

The lexer supports three types of actions:

### 1. UDJI_U_STANJE (Enter State)

**Syntax**: `UDJI_U_STANJE <stateName>`

**Effect**: Changes the current lexer state.

**Example**:
```
<S_pocetno>"
{
-
UDJI_U_STANJE S_string
VRATI_SE 0
}
```

### 2. VRATI_SE (Backtrack / yyless)

**Syntax**: `VRATI_SE <n>`

**Effect**: Of the matched characters, keep the first `n` in the token, return the rest to the input buffer.

**Algorithm**:
```java
int actualMatch;
if (backtrack != null && backtrack >= 0 && backtrack <= longestMatch) {
  actualMatch = backtrack;  // Keep first n characters
} else {
  actualMatch = longestMatch;  // Keep entire match
}

// Remove only actualMatch characters from buffer
input.delete(0, actualMatch);
// Characters from actualMatch to longestMatch remain in buffer
```

**Example - Entering String State**:
- Pattern: `"` matches 1 character
- Action: `VRATI_SE 0`
- Result: 0 characters consumed, `"` remains in buffer for next match

**Example - Two-Character Operator**:
- Pattern: `--` matches 2 characters
- Action: `VRATI_SE 1` (if we wanted to match only `-`)
- Result: 1 character consumed (`-`), 1 character returned (`-`)

### 3. NOVI_REDAK (New Line)

**Syntax**: `NOVI_REDAK`

**Effect**: Increments the line number for the next token.

**Implementation**:
- If a newline character (`\n`) was already consumed in the matched text, the line number was already incremented
- Otherwise, increment the line number now (affects the next token)

---

## Symbol Table

Every token is automatically added to the symbol table.

### Structure

The symbol table is a list of `SymbolTableEntry` records:
```java
record SymbolTableEntry(String token, String text) {}
```

### Indexing

- Tokens with the same type and text share the same index
- When a token is created, the lexer checks if an entry with the same `(token, text)` pair exists
- If it exists, the existing index is used
- Otherwise, a new entry is added and its index is used

### Output Format

The symbol table is output in the following format:
```
tablica znakova:
indeks   uniformni znak   izvorni tekst
     0   KR_INT            int
     1   IDN               x
     2   TOCKAZAREZ        ;
```

### Token Stream Output

The token stream includes references to symbol table indices:
```
niz uniformnih znakova:
uniformni znak    redak    indeks u tablicu znakova
KR_INT               1       0
IDN                  1       1
TOCKAZAREZ           1       2
```

---

## Implementation Details

### Character Encoding

- Input is read as UTF-8 characters
- All transitions in DFAs use `char` values (16-bit Unicode)

### Position Tracking

- **Line Number**: Starts at 1, incremented on `\n` or `NOVI_REDAK` action
- **Column Number**: Starts at 1, incremented for each character (except newline)
- Position is saved **before** processing a match (line number increment applies to next token)

### Buffer Management

- Input is read line by line into a `StringBuilder` buffer
- Characters are consumed from the front of the buffer as tokens are matched
- The buffer may contain characters that will be returned due to `VRATI_SE` actions

### Performance Considerations

- **DFA Lookup**: O(1) for each character (deterministic transitions)
- **Symbol Table**: O(n) linear search (could be optimized with HashMap)
- **Epsilon Closure**: O(n²) in worst case (n = number of states)
- **Subset Construction**: O(2ⁿ) in worst case (n = number of NFA states), but typically much better

---

## Summary

This lexer generator implements a complete lexical analysis system with:

1. **Manual regex parsing** (no regex libraries)
2. **Thompson's construction** for ε-NFA building
3. **Subset construction** for DFA conversion
4. **Maximal munch** with rule priority
5. **Multi-state support** for context-sensitive tokens
6. **Comprehensive error recovery**
7. **Symbol table management**

The implementation is modular, extensible, and follows best practices for compiler construction.
