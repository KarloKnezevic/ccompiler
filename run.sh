#!/bin/bash

# Run script for PPJ Compiler
# This script runs the compiler from the built JAR file

set -e

JAR_FILE="cli/target/ccompiler.jar"

# Check if JAR file exists
if [ ! -f "$JAR_FILE" ]; then
    echo "Error: Compiler JAR file not found: $JAR_FILE"
    echo ""
    echo "Please build the project first using:"
    echo "  ./build.sh"
    echo ""
    echo "Or manually:"
    echo "  mvn clean package -DskipTests"
    exit 1
fi

# Run the compiler with all arguments
java -jar "$JAR_FILE" "$@"

