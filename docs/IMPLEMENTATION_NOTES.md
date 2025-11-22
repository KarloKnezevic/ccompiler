# Implementation Notes

## Lexer Implementation

### Pattern Processing
- **Quoted patterns**: Patterns like `"` (single quote) are extracted and unescaped
- **Regex patterns**: Patterns like `"({macro}|\\")*"` are processed as regex
- **Literal patterns**: Patterns like `break`, `char` are treated as literal strings
- **Macro expansion**: Recursive expansion of macros (e.g., `{znak}`, `{znamenka}`)

### Regex Parser
- Manual implementation following `regex_pseudokod.txt`
- Supports: concatenation, union (`|`), Kleene star (`*`), groups `()`, epsilon (`$`)
- Handles escaped characters: `\t`, `\n`, `\\`, `\"`
- Special escape: `_` → space character

### NFA to DFA Conversion
- Subset construction algorithm
- Epsilon closure computation
- Preserves rule priority (earlier rules processed first)

### Action Handling
- `UDJI_U_STANJE <state>`: Enter new lexer state
- `VRATI_SE n`: Backtrack n characters
- `NOVI_REDAK`: Increment line number
- Actions stored with accepting states in DFA

### Maximal Munch
- Finds longest matching token
- On tie, earlier rule wins (priority)

### Error Recovery
- **Algorithm C**: Discard unrecognized character and continue
- **Unterminated strings**: Detect and report error, discard to newline, exit string state

## Parser Implementation

### Grammar Parser
- Parses `ppjLang_sintaksa.txt` format
- Handles: `%V` (non-terminals), `%T` (terminals), `%Syn` (sync tokens)
- Manual parsing (no regex)

### LR(1) Table Generation
- Canonical LR(1) item set construction
- Closure computation with lookahead
- GOTO computation
- ACTION/GOTO table generation

### AST Structure
- Sealed interfaces with records (Java 21)
- All nodes include line/column information
- Hierarchical structure:
  - `Program` → `Declaration*`
  - `Declaration` → `VariableDeclaration | FunctionDeclaration | StructDeclaration`
  - `Statement` → `IfStatement | WhileStatement | ForStatement | ...`
  - `Expression` → `BinaryExpression | UnaryExpression | PrimaryExpression | ...`
  - `Type` → `PrimitiveType | PointerType | ArrayType | StructType`

## Known Issues / TODO

1. **Lexer**:
   - ✅ String literal over-matching fixed
   - ✅ Unterminated string error recovery implemented
   - ✅ Maximal munch and rule priority working correctly

2. **Parser**:
   - AST node construction from parse tree
   - Error recovery with `%Syn` tokens
   - Generativno stablo output

3. **Semantics**:
   - Type system implementation
   - Symbol tables with scopes
   - Semantic analysis visitors

4. **Codegen**:
   - FRISC instruction emitter
   - Register allocation
   - Function ABI
   - Short-circuit evaluation
