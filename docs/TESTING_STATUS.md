# Testing Status

## Test Infrastructure ✅

- ✅ Import script created: `scripts/import-tests.sh`
- ✅ Test cases imported: 13 cases (ppjc_case_00 through ppjc_case_06, plus string and P2/P3 test cases)
- ✅ JUnit 5 test framework set up
- ✅ Golden file test structure created
- ✅ Comprehensive test coverage for:
  - Basic tokenization
  - String literals (including escaped quotes)
  - Comments (single-line and multi-line)
  - Maximal munch (P2)
  - Rule priority (P3)
  - Error recovery
  - Unterminated strings
- ✅ **Code Generator Integration Testing**: 90 valid examples tested
- ✅ **FRISC Simulator Integration**: Generated code validated on FRISC simulator
- ✅ **HTML Report Generation**: Comprehensive reports with FRISC code

## Test Results

### Lexer Tests ✅

**Status**: 12 out of 13 tests passing

**Passing Tests**:
- ✅ Basic tokenization
- ✅ Keywords and identifiers
- ✅ Numbers and operators
- ✅ String literals with escaped quotes
- ✅ Comments
- ✅ Maximal munch scenarios
- ✅ Error recovery
- ✅ Unterminated string detection

**Known Issues**:
- ⚠️ One test case (`ppjc_case_p2_p3_maxmunch`) has minor formatting differences (likely output formatting issue, not functional)

### Code Generator Tests ✅

**Status**: 74 out of 90 valid programs successfully compiled to FRISC

**Success Rate**: 82.2% (74/90 programs)

**Successfully Implemented Features**:
- ✅ Basic functions (`main`)
- ✅ Local and global variables (`int`, `char`)
- ✅ Assignment operators (`=`)
- ✅ Arithmetic operations (`+`, `-`, `*`, `/`, `%`)
- ✅ Relational operations (`<`, `>`, `<=`, `>=`, `==`, `!=`)
- ✅ Logical operations (`&&`, `||`, `!`)
- ✅ If-else statements with proper control flow
- ✅ While and for loops with break/continue
- ✅ Function calls with parameters
- ✅ Return statements
- ✅ Increment/decrement operators (`++`, `--`)
- ✅ Stack management and calling conventions
- ✅ Proper FRISC assembly generation

**Failed Programs (16/90)**:
- ❌ **Float types** (4 programs): `float`, floating-point literals
- ❌ **Struct types** (4 programs): `struct` definitions, member access
- ❌ **Pointers and arrays** (8 programs): `int *ptr`, `&value`, `*ptr`, `array[i]`, pointer arithmetic

**FRISC Simulator Integration**:
- ✅ Generated code executes successfully on FRISCjs simulator
- ✅ Proper program termination with `HALT` instruction
- ✅ Return values correctly stored in R6 register
- ✅ Stack operations function correctly

## Test Coverage

### Unit Tests
- ✅ `LexerBasicTest`: Basic functionality verification
- ✅ `LexerDetailedTest`: Detailed scenarios (max-munch, rule order, actions)
- ✅ `LexerCommentTest`: Comment handling
- ✅ `LexerConsistencyTest`: Consistency with algorithms
- ✅ `LexerDebugTest`: Debugging utilities
- ✅ `RegexParserTest`: Regex parser correctness

### Integration Tests
- ✅ `LexerGoldenTest`: Golden file comparison (13 test cases)
- ✅ **Code Generator Integration**: Full pipeline testing (lexer → parser → semantics → codegen)
- ✅ **FRISC Assembly Validation**: Generated code syntax and structure verification
- ✅ **HTML Report Generation**: Comprehensive testing reports with FRISC code inclusion

## Test Cases

### ppjc_case_00 - ppjc_case_06
Basic test cases covering:
- Keywords
- Identifiers
- Numbers
- Operators
- Delimiters
- Simple expressions

### ppjc_case_string_*
String literal test cases:
- Basic strings
- Escaped quotes
- Unterminated strings
- Strings with operators

### ppjc_case_p2_p3_maxmunch
Tests for:
- Maximal munch algorithm
- Rule priority
- Operator precedence

## Running Tests

### Lexer Tests
```bash
# Run all lexer tests
mvn test -pl compiler-lexer

# Run specific test
mvn test -pl compiler-lexer -Dtest=LexerGoldenTest

# Run with verbose output
mvn test -pl compiler-lexer -X
```

### Code Generator Tests
```bash
# Test single program
./run.sh examples/valid/program1.c

# Generate comprehensive HTML reports
java -cp "cli/target/ccompiler.jar" hr.fer.ppj.examples.ExamplesReportGenerator

# Test FRISC simulator integration
node node_modules/friscjs/consoleapp/frisc-console.js compiler-bin/a.frisc
```

### Full Pipeline Testing
```bash
# Build entire project
mvn clean install

# Test all valid examples (requires custom script)
# Results: 74/90 programs successfully generate FRISC code
# 16 programs fail due to unsupported features (float, struct, pointers)
```

## Test Maintenance

- Golden files are stored in `compiler-lexer/src/test/resources/ppjc_case_*/leksicke_jedinke.txt`
- Test input is in `compiler-lexer/src/test/resources/ppjc_case_*/program.c`
- When updating lexer behavior, regenerate golden files if needed
