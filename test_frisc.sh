#!/bin/bash
# FRISC Simulator Test Script
# Usage: ./test_frisc.sh program.c [expected_result]

if [ $# -lt 1 ]; then
    echo "Usage: $0 program.c [expected_result]"
    echo "Example: $0 examples/valid/program1.c 0"
    exit 1
fi

PROGRAM=$1
EXPECTED=${2:-""}

echo "🔧 Compiling $PROGRAM..."
./run.sh "$PROGRAM"

if [ $? -ne 0 ]; then
    echo "❌ Compilation failed"
    exit 1
fi

echo "🚀 Running FRISC simulator..."
RESULT=$(node node_modules/friscjs/consoleapp/frisc-console.js compiler-bin/a.frisc 2>/dev/null)

echo "📊 Program returned: $RESULT"

if [ -n "$EXPECTED" ]; then
    if [ "$RESULT" = "$EXPECTED" ]; then
        echo "✅ Test PASSED (expected: $EXPECTED)"
    else
        echo "❌ Test FAILED (expected: $EXPECTED, got: $RESULT)"
        exit 1
    fi
fi

echo "🎯 Generated FRISC code:"
echo "----------------------------------------"
cat compiler-bin/a.frisc
echo "----------------------------------------"
