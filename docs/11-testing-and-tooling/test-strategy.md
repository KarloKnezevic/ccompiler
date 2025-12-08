# Testing Strategy

## Overview

The PPJ compiler employs a comprehensive testing strategy covering unit tests, integration tests, and end-to-end validation. Testing ensures correctness across all compiler phases from lexical analysis through code generation.

## Testing Philosophy

### Testing Principles

1. **Correctness First**: All tests verify correct behavior, not just absence of crashes
2. **Comprehensive Coverage**: Tests cover both valid and invalid inputs
3. **Golden File Testing**: Reference outputs stored for regression testing
4. **Integration Testing**: Full pipeline testing from source to executable assembly
5. **Automated Validation**: Tests run automatically during build process

## Test Organization

### Module-Level Tests

Each compiler module has its own test suite:

- **compiler-lexer**: Lexical analysis tests
- **compiler-parser**: Syntax analysis tests
- **compiler-semantics**: Semantic analysis tests
- **compiler-codegen**: Code generation tests

### Test Types

1. **Unit Tests**: Test individual components in isolation
2. **Integration Tests**: Test interactions between components
3. **Golden File Tests**: Compare outputs against reference files
4. **End-to-End Tests**: Full compilation pipeline tests

## Lexical Analysis Testing

### Test Cases

**Basic Tokenization**:
- Keywords: `int`, `char`, `if`, `while`, etc.
- Identifiers: Valid identifier patterns
- Numbers: Integer and floating-point literals
- Operators: All operator tokens
- Delimiters: Brackets, parentheses, braces

**Advanced Scenarios**:
- String literals with escaped quotes
- Comments (single-line and multi-line)
- Maximal munch (longest match selection)
- Rule priority (earlier rules win ties)
- Error recovery (unrecognized characters)

### Golden File Tests

**Test Structure**:
```
compiler-lexer/src/test/resources/
  ppjc_case_00/
    program.c
    leksicke_jedinke.txt  # Golden file
  ppjc_case_01/
    ...
```

**Test Execution**:
```java
@Test
void testLexerGolden() {
    Lexer lexer = new Lexer();
    String output = lexer.tokenize(input);
    String expected = Files.readString(goldenFile);
    assertEquals(expected, output);
}
```

### Running Lexer Tests

```bash
# Run all lexer tests
mvn test -pl compiler-lexer

# Run specific test class
mvn test -pl compiler-lexer -Dtest=LexerGoldenTest

# Run with verbose output
mvn test -pl compiler-lexer -X
```

## Syntax Analysis Testing

### Test Cases

**Grammar Coverage**:
- All production rules tested
- Edge cases (empty productions, epsilon)
- Error recovery scenarios

**Parse Tree Validation**:
- Correct tree structure
- Proper node relationships
- Line number preservation

### Running Parser Tests

```bash
# Run all parser tests
mvn test -pl compiler-parser

# Run specific test
mvn test -pl compiler-parser -Dtest=ParserGoldenTest
```

## Semantic Analysis Testing

### Test Cases

**Type Checking**:
- Valid type operations
- Type mismatch errors
- Implicit conversions
- Const qualification

**Scope Resolution**:
- Variable lookup
- Function resolution
- Nested scope handling
- Duplicate declaration detection

**Control Flow**:
- Return statement validation
- Break/continue validation
- Function call validation

### Running Semantic Tests

```bash
# Run all semantic tests
mvn test -pl compiler-semantics
```

## Code Generation Testing

### Integration Testing

**Full Pipeline Tests**:
- Compile complete programs
- Generate FRISC assembly
- Validate assembly syntax
- Execute on FRISC simulator
- Compare execution results

### Test Programs

**Valid Programs** (`examples/valid/`):
- 90 test programs covering:
  - Basic functions and variables
  - Control flow (if, while, for)
  - Expressions and operators
  - Function calls
  - Arrays and pointers (partial)

**Invalid Programs** (`examples/invalid/`):
- 70+ error test cases:
  - Syntax errors
  - Type errors
  - Scope errors
  - Semantic errors

### Test Results

**Success Rate**: 82.2% (74/90 valid programs)

**Successfully Implemented**:
- ✅ Basic functions and variables
- ✅ Control flow statements
- ✅ Arithmetic and logical operations
- ✅ Function calls
- ✅ Stack management

**Not Yet Implemented**:
- ❌ Float types (4 programs)
- ❌ Struct types (4 programs)
- ❌ Advanced pointers (8 programs)

### Running Code Generation Tests

```bash
# Compile a test program
./run.sh examples/valid/program1.c

# View generated assembly
cat compiler-bin/a.frisc

# Run on FRISC simulator
node node_modules/friscjs/consoleapp/frisc-console.js compiler-bin/a.frisc
```

## HTML Report Generation

### Report Generation

Generate comprehensive HTML reports:

```bash
java -cp "cli/target/ccompiler.jar" hr.fer.ppj.examples.ExamplesReportGenerator
```

**Report Contents**:
- Source code listings
- Lexical token analysis
- Parse tree visualizations
- Semantic analysis results
- Generated FRISC assembly
- Execution results

**Output Files**:
- `examples/report_valid.html`: Valid program reports
- `examples/report_invalid.html`: Invalid program reports

## FRISC Simulator Integration

### Simulator Usage

The compiler integrates with FRISCjs simulator for code validation:

**Console Interface**:
```bash
node node_modules/friscjs/consoleapp/frisc-console.js compiler-bin/a.frisc
```

**Web Interface**:
```bash
# Open in browser
open node_modules/friscjs/webapp/index.html
```

### Simulator Validation

**Execution Verification**:
- Programs execute correctly
- Return values in R6 register
- Stack operations function properly
- Memory access patterns correct

**See Also**: [FRISC Simulator Guide](../09-runtime-and-support/runtime-library.md)

## Continuous Integration

### Build Integration

Tests run automatically during build:

```bash
# Full build with tests
mvn clean verify

# Skip tests (development only)
mvn clean package -DskipTests
```

### Test Failures

**Handling Failures**:
1. Identify failing test
2. Examine error output
3. Compare with expected results
4. Fix implementation or update golden files
5. Re-run tests

## Test Maintenance

### Updating Golden Files

When lexer/parser behavior changes:

1. Run tests to identify failures
2. Review changes for correctness
3. Update golden files if changes are intentional:
   ```bash
   # Regenerate golden files
   mvn test -pl compiler-lexer -DupdateGoldenFiles=true
   ```

### Adding New Tests

**Test Structure**:
1. Create test input file
2. Create expected output (golden file)
3. Write test case
4. Add to test suite
5. Verify test passes

## Further Reading

- **[Example Programs](example-programs.md)**: Test program catalog
- **[Debugging Workflow](debugging-workflow.md)**: Debugging techniques
- **[FRISC Simulator Guide](../09-runtime-and-support/runtime-library.md)**: Simulator usage

---

*Comprehensive testing ensures compiler correctness and enables confident development and maintenance.*
