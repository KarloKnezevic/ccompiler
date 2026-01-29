#!/bin/bash

################################################################################
# IR Validation Report Generator
#
# Generates a comprehensive report for all C programs in examples/valid
# containing: C source, generative tree, semantic tree, and IR program.
#
# Usage:
#   ./generate_ir_report.sh
#
# Output:
#   ir_validation_report.txt - Complete report for all programs
#
################################################################################

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Build all modules first
echo "Building compiler modules..."
mvn compile -q -DskipTests

# Run the report generator
echo "Generating IR validation report..."
java -cp "cli/target/classes:compiler-ir/target/classes:compiler-semantics/target/classes:compiler-parser/target/classes:compiler-lexer/target/classes" \
     hr.fer.ppj.cli.IrValidationReport

echo ""
echo "Report generated: ir_validation_report.txt"

