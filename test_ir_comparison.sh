#!/bin/bash
# Compare generated IR with expected IR from examples/valid

cd "$(dirname "$0")"

FAILED=0
PASSED=0
TOTAL=0

for c_file in examples/valid/program*.c; do
    if [ ! -f "$c_file" ]; then
        continue
    fi
    
    basename=$(basename "$c_file" .c)
    expected_ir="examples/valid/${basename}.ir"
    generated_ir="/tmp/${basename}_generated.ir"
    
    if [ ! -f "$expected_ir" ]; then
        echo "WARNING: No expected IR file for $c_file"
        continue
    fi
    
    TOTAL=$((TOTAL + 1))
    
    # Generate IR
    CLASSPATH="cli/target/classes:compiler-ir/target/classes:compiler-lexer/target/classes:compiler-parser/target/classes:compiler-semantics/target/classes:$(mvn -q dependency:build-classpath -pl cli -DincludeScope=compile 2>/dev/null)"
    java -cp "$CLASSPATH" hr.fer.ppj.cli.IrGenerator "$c_file" "$generated_ir" 2>&1 | grep -v "^Jan\|^INFO:" > /tmp/error.log
    
    if [ ! -f "$generated_ir" ]; then
        echo "FAIL: $basename - IR generation failed"
        cat /tmp/error.log | head -5
        FAILED=$((FAILED + 1))
        continue
    fi
    
    # Compare ignoring empty lines and whitespace
    # Remove empty lines and normalize whitespace
    sed '/^[[:space:]]*$/d' "$expected_ir" | sed 's/[[:space:]]*$//' > /tmp/expected_clean.ir
    sed '/^[[:space:]]*$/d' "$generated_ir" | sed 's/[[:space:]]*$//' > /tmp/generated_clean.ir
    
    if diff -q /tmp/expected_clean.ir /tmp/generated_clean.ir > /dev/null 2>&1; then
        echo "PASS: $basename"
        PASSED=$((PASSED + 1))
    else
        echo "FAIL: $basename - IR mismatch"
        echo "Expected:"
        head -20 /tmp/expected_clean.ir
        echo "---"
        echo "Generated:"
        head -20 /tmp/generated_clean.ir
        echo "---"
        FAILED=$((FAILED + 1))
    fi
done

echo ""
echo "Summary: $PASSED/$TOTAL passed, $FAILED failed"

exit $FAILED
