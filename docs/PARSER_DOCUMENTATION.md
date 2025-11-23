# Parser Documentation

## Overview

The parser module implements a canonical LR(1) syntax analyzer for the PPJ language. It generates both generative (parse) trees and abstract syntax trees (AST) suitable for semantic analysis and code generation.

## Architecture

### Components

1. **Grammar Parser** (`GrammarParser`)
   - Parses grammar definition from `parser_definition.txt`
   - Handles non-terminals, terminals, synchronization tokens, and productions

2. **Grammar** (`Grammar`)
   - Represents the parsed grammar with augmentation
   - Provides access to productions, terminals, and non-terminals

3. **FIRST Set Computer** (`FirstSetComputer`)
   - Computes FIRST sets for grammar symbols and sequences
   - Essential for LR(1) item set construction

4. **LR(1) Parser Generator**
   - `LRClosure`: Implements CLOSURE algorithm
   - `LRGoto`: Implements GOTO algorithm
   - `LRTableBuilder`: Builds ACTION and GOTO tables
   - Generates ~823 states for the PPJ grammar

5. **Runtime Parser** (`LRParser`)
   - Uses generated tables to parse token streams
   - Builds parse trees during parsing

6. **Tree Generation**
   - `ParseTree`: Represents generative and syntax trees
   - Generative tree: Complete parse tree with all grammar nodes
   - Syntax tree: Simplified AST suitable for semantic analysis

## Output Files

The parser generates two output files:

### `generativno_stablo.txt`
Complete parse tree showing the derivation process. Every grammar production is represented as a node.

**Format:**
```
0:<symbol>
    1:<child1>
        2:<child2>
    ...
```

### `sintaksno_stablo.txt`
Abstract syntax tree (AST) optimized for semantic analysis and code generation. Intermediate grammar nodes that don't add semantic value are removed.

**Format:**
Same as generative tree, but with simplified structure.

## Usage

### Via CLI

```bash
# Run lexical and syntax analysis
./ccompiler syntax program.c

# Output files are generated in compiler-bin/:
# - leksicke_jedinke.txt
# - generativno_stablo.txt
# - sintaksno_stablo.txt
```

### Programmatic Usage

```java
ParserConfig.Config config = ParserConfig.Config.createDefault(
    inputTokensPath,
    outputDirectory
);

Parser parser = new Parser();
parser.parse(config);
```

## LR(1) Table Caching

The parser uses table caching to avoid regenerating LR(1) parsing tables on every run. Tables are serialized to `target/parser-cache/lr_table.ser` and reused across test runs.

To clear the cache:
```java
LRTableCache.clearCache();
```

## Error Handling

The parser implements basic error recovery using synchronization tokens defined in the grammar (`%Syn` section). When a parse error occurs:

1. Error is logged with line number and token information
2. Parser attempts to recover by skipping to synchronization tokens
3. If recovery fails, a `ParserException` is thrown

## Tree Structure

### Generative Tree
- Complete representation of the parse
- Every grammar production is a node
- Useful for debugging and understanding the parse process

### Syntax Tree (AST)
- Simplified structure
- Removes intermediate nodes that don't add semantic value
- Optimized for:
  - Semantic analysis
  - Type checking
  - Code generation

**Nodes skipped in syntax tree:**
- Intermediate list nodes that are just wrappers
- Redundant expression nodes
- Single-child wrapper nodes

## Performance

- **State generation**: ~823 states generated in ~7 seconds
- **Table caching**: First run builds table, subsequent runs load from cache
- **Parsing speed**: Linear time complexity O(n) where n is number of tokens

## Testing

The parser includes comprehensive tests:

- **Unit tests**: Test individual components (Grammar, FIRST sets, etc.)
- **Integration tests**: Test full parsing pipeline
- **Golden file tests**: Verify output file generation (no comparison with expected)

## Future Enhancements

1. **AST Builder**: Convert ParseTree to typed AST nodes
2. **Better error recovery**: Implement panic mode with more sophisticated recovery
3. **Incremental parsing**: Support for parsing partial programs
4. **Error messages**: More detailed and helpful error messages

## References

1. Srbljić, Siniša (2007). *Prevođenje programskih jezika*. Element, Zagreb. ISBN 978-953-197-625-1.

2. Aho, A. V., Lam, M. S., Sethi, R., & Ullman, J. D. (2006). *Compilers: Principles, Techniques, and Tools* (2nd ed.). Pearson Education.

## Additional Documentation

For detailed technical documentation on the LR parser implementation, see:
- [LR_PARSER_TECHNICAL.md](LR_PARSER_TECHNICAL.md) - Detailed technical documentation on algorithms and implementation

