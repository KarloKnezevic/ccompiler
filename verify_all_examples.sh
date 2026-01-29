#!/bin/bash
# Verify all examples/valid programs against expected IR

cd "$(dirname "$0")"

EXAMPLES_DIR="examples/valid"
CLASSPATH="cli/target/classes:compiler-ir/target/classes:compiler-semantics/target/classes:compiler-parser/target/classes:compiler-lexer/target/classes:$(mvn -q dependency:build-classpath -pl cli -DincludeScope=compile 2>/dev/null | tail -1)"

total=0
passed=0
failed=0
errors=0
failures=()

echo "Verifying all examples from $EXAMPLES_DIR..."
echo ""

# Find all .c files
c_files=$(find "$EXAMPLES_DIR" -name "program*.c" | sort)

for c_file in $c_files; do
    filename=$(basename "$c_file" .c)
    golden_ir="$EXAMPLES_DIR/${filename}.ir"
    generated_ir="/tmp/${filename}_generated.ir"
    
    ((total++))
    
    # Skip if no golden IR file exists
    if [[ ! -f "$golden_ir" ]]; then
        echo "SKIP $filename.c (no golden IR file)"
        continue
    fi
    
    echo -n "Testing $filename.c... "
    
    # Run compiler
    if java -cp "$CLASSPATH" hr.fer.ppj.cli.IrGenerator "$c_file" "$generated_ir" >/dev/null 2>&1; then
        # Check if IR was generated
        if [[ ! -f "$generated_ir" ]]; then
            echo "FAIL (IR not generated)"
            ((failed++))
            failures+=("$filename")
            continue
        fi
        
        # Compare IR files using normalized comparison (ignore blank lines)
        # Remove blank lines and trailing whitespace, then compare
        if diff -q <(cat "$golden_ir" | sed '/^[[:space:]]*$/d' | sed 's/[[:space:]]*$//') \
                   <(cat "$generated_ir" | sed '/^[[:space:]]*$/d' | sed 's/[[:space:]]*$//') >/dev/null 2>&1; then
            echo "PASS"
            ((passed++))
        else
            echo "FAIL (IR mismatch)"
            ((failed++))
            failures+=("$filename")
        fi
    else
        echo "ERROR (compilation failed)"
        ((errors++))
        failures+=("$filename")
    fi
done

echo ""
echo "========================================="
echo "Summary:"
echo "  Total:  $total"
echo "  Passed: $passed"
echo "  Failed: $failed"
echo "  Errors: $errors"
echo "========================================="

if [[ ${#failures[@]} -gt 0 ]]; then
    echo ""
    echo "Failed programs:"
    for f in "${failures[@]}"; do
        echo "  - $f"
    done
    echo ""
    echo "To see differences for a specific program, run:"
    echo "  diff -u <(cat examples/valid/PROGRAM.ir | sed '/^[[:space:]]*$/d') <(cat /tmp/PROGRAM_generated.ir | sed '/^[[:space:]]*$/d')"
fi

if [[ $failed -gt 0 ]] || [[ $errors -gt 0 ]]; then
    exit 1
fi

exit 0
