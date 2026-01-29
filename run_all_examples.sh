#!/bin/bash

# Script to run all examples/valid tests and compare IR output
# Usage: ./run_all_examples.sh

set -e

EXAMPLES_DIR="examples/valid"
BIN_DIR="compiler-bin"
JAR="cli/target/ccompiler.jar"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

total=0
passed=0
failed=0
errors=0

echo "Running all examples from $EXAMPLES_DIR..."
echo ""

# Find all .c files
c_files=$(find "$EXAMPLES_DIR" -name "*.c" | sort)

for c_file in $c_files; do
    filename=$(basename "$c_file" .c)
    golden_ir="$EXAMPLES_DIR/${filename}.ir"
    generated_ir="$BIN_DIR/medukod.ir"
    
    ((total++))
    
    # Skip if no golden IR file exists
    if [[ ! -f "$golden_ir" ]]; then
        echo -e "${YELLOW}SKIP${NC} $filename.c (no golden IR file)"
        continue
    fi
    
    echo -n "Testing $filename.c... "
    
    # Run compiler
    if java -jar "$JAR" semantic "$c_file" >/dev/null 2>&1; then
        # Check if IR was generated
        if [[ ! -f "$generated_ir" ]]; then
            echo -e "${RED}FAIL${NC} (IR not generated)"
            ((failed++))
            continue
        fi
        
        # Compare IR files (normalize whitespace)
        if diff -q <(cat "$golden_ir" | tr -d '\r' | sed 's/[[:space:]]*$//' | sed '/^$/N;/^\n$/d') \
                   <(cat "$generated_ir" | tr -d '\r' | sed 's/[[:space:]]*$//' | sed '/^$/N;/^\n$/d') >/dev/null 2>&1; then
            echo -e "${GREEN}PASS${NC}"
            ((passed++))
        else
            echo -e "${RED}FAIL${NC} (IR mismatch)"
            ((failed++))
        fi
    else
        echo -e "${RED}ERROR${NC} (compilation failed)"
        ((errors++))
    fi
done

echo ""
echo "Summary:"
echo "  Total:  $total"
echo "  Passed: $passed"
echo "  Failed: $failed"
echo "  Errors: $errors"

if [[ $failed -gt 0 ]] || [[ $errors -gt 0 ]]; then
    exit 1
fi

exit 0

