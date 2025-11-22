#!/bin/bash

# Build script for PPJ Compiler
# This script compiles the entire project and creates a single executable JAR file

set -e

echo "Building PPJ Compiler..."
echo ""

# Clean and compile all modules
mvn clean package -DskipTests -Dspotbugs.skip=true

echo ""
echo "✅ Build completed successfully!"
echo ""
echo "The compiler JAR file is located at:"
echo "  cli/target/ccompiler.jar"
echo ""
echo "To run the compiler, use:"
echo "  java -jar cli/target/ccompiler.jar <command> <file>"
echo ""
echo "Or use the run script:"
echo "  ./run.sh <command> <file>"

