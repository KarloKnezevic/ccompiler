#!/bin/bash
# Test all examples/valid programs and report failures

cd "$(dirname "$0")"

EXAMPLES_DIR="examples/valid"
BIN_DIR="compiler-bin"
JAR="cli/target/ccompiler.jar"

# Build classpath
CLASSPATH="cli/target/classes:compiler-ir/target/classes:compiler-semantics/target/classes:compiler-parser/target/classes:compiler-lexer/target/classes:$(mvn -q dependency:build-classpath -pl cli -DincludeScope=compile 2>/dev/null | tail -1)"

total=0
passed=0
failed=0
errors=0
failures=()

echo "Testing all examples from $EXAMPLES_DIR..."
echo ""

# Find all .c files
c_files=$(find "$EXAMPLES_DIR" -name "program*.c" | sort)

for c_file in $c_files; do
    filename=$(basename "$c_file" .c)
    golden_ir="$EXAMPLES_DIR/${filename}.ir"
    generated_ir="$BIN_DIR/medukod.ir"
    
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
        # Use Java to do the comparison
        if java -cp "$CLASSPATH" -c "
import hr.fer.ppj.ir.util.IrNormalizer;
import java.nio.file.Files;
import java.nio.file.Paths;
String expected = Files.readString(Paths.get(\"$golden_ir\"));
String actual = Files.readString(Paths.get(\"$generated_ir\"));
System.exit(IrNormalizer.equalsNormalized(expected, actual) ? 0 : 1);
" 2>/dev/null; then
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
echo "Summary:"
echo "  Total:  $total"
echo "  Passed: $passed"
echo "  Failed: $failed"
echo "  Errors: $errors"

if [[ ${#failures[@]} -gt 0 ]]; then
    echo ""
    echo "Failed programs:"
    for f in "${failures[@]}"; do
        echo "  - $f"
    done
fi

if [[ $failed -gt 0 ]] || [[ $errors -gt 0 ]]; then
    exit 1
fi

exit 0
