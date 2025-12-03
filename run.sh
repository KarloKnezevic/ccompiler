#!/bin/bash

################################################################################
# PPJ Compiler Run Script
#
# This script provides a convenient wrapper for executing the PPJ compiler
# from the built JAR file. It handles JAR file detection, Java execution,
# and provides helpful error messages.
#
# Usage:
#   ./run.sh <command> [arguments...]
#
# Commands:
#   lexer <file>           Run lexical analysis only
#   syntax <file>          Run syntax analysis (includes lexical)
#   semantic <file>        Run semantic analysis (includes all phases)
#   run <file>             Execute FRISC assembly code
#
# Options:
#   -h, --help             Show this help message and exit
#   -v, --version          Show compiler version information
#
# Examples:
#   ./run.sh semantic program.c
#   ./run.sh run compiler-bin/a.frisc
#   ./run.sh --help
#
# Exit Codes:
#   0   Execution completed successfully
#   1   Execution failed or invalid arguments
#   2   JAR file not found (build required)
#   3   Java execution failed
#
# Author: Karlo Knežević
# Website: https://karloknezevic.github.io/
################################################################################

set -euo pipefail

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="cli/target/ccompiler.jar"
JAVA_CMD="java"

# Colors for output (if terminal supports it)
if [[ -t 1 ]]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    CYAN='\033[0;36m'
    BOLD='\033[1m'
    NC='\033[0m' # No Color
else
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    CYAN=''
    NC=''
    BOLD=''
fi

################################################################################
# Helper Functions
################################################################################

print_usage() {
    cat << EOF
${BOLD}PPJ Compiler Run Script${NC}

${BOLD}Usage:${NC}
    ./run.sh <command> [arguments...]
    ./run.sh [OPTIONS]

${BOLD}Commands:${NC}
    lexer <file>           Run lexical analysis only
    syntax <file>          Run syntax analysis (includes lexical)
    semantic <file>        Run semantic analysis (includes all phases)
    run <file>             Execute FRISC assembly code

${BOLD}Options:${NC}
    -h, --help             Show this help message and exit
    -v, --version          Show compiler version information

${BOLD}Examples:${NC}
    ./run.sh semantic program.c
    ./run.sh run compiler-bin/a.frisc
    ./run.sh syntax examples/valid/program1.c
    ./run.sh --help

${BOLD}Note:${NC}
    The compiler JAR file must be built first using:
    ${CYAN}./build.sh${NC}

EOF
}

print_error() {
    echo -e "${RED}${BOLD}Error:${NC} $1" >&2
}

print_success() {
    echo -e "${GREEN}${BOLD}✓${NC} $1"
}

print_info() {
    echo -e "${BLUE}${BOLD}ℹ${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}${BOLD}⚠${NC} $1"
}

check_jar_exists() {
    if [[ ! -f "$JAR_FILE" ]]; then
        print_error "Compiler JAR file not found: $JAR_FILE"
        echo ""
        echo "The compiler must be built before it can be executed."
        echo ""
        echo "To build the compiler, run:"
        echo -e "  ${CYAN}./build.sh${NC}"
        echo ""
        echo "Or manually:"
        echo -e "  ${CYAN}mvn clean package -DskipTests${NC}"
        echo ""
        exit 2
    fi
}

check_java() {
    if ! command -v java &> /dev/null; then
        print_error "Java runtime not found in PATH"
        echo ""
        echo "Please install Java 21+ from: https://openjdk.org/"
        exit 3
    fi
    
    # Check Java version
    local java_version
    java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
    
    if [[ "$java_version" -lt 21 ]]; then
        print_warning "Java version $java_version detected. Java 21+ is recommended."
        echo ""
    fi
}

get_jar_version() {
    # Try to extract version from JAR manifest or Maven properties
    if command -v unzip &> /dev/null; then
        local version
        version=$(unzip -p "$JAR_FILE" META-INF/MANIFEST.MF 2>/dev/null | grep -i "Implementation-Version" | cut -d' ' -f2 | tr -d '\r' || echo "unknown")
        echo "$version"
    else
        echo "unknown"
    fi
}

print_version() {
    check_jar_exists
    local version
    version=$(get_jar_version)
    
    echo -e "${BOLD}PPJ Compiler${NC}"
    echo "  Version: $version"
    echo "  JAR File: $JAR_FILE"
    echo ""
    
    if [[ -f "$JAR_FILE" ]]; then
        local jar_size
        jar_size=$(du -h "$JAR_FILE" | cut -f1)
        echo "  Size: $jar_size"
    fi
    
    echo ""
    echo "Java Information:"
    java -version 2>&1 | sed 's/^/  /'
}

parse_arguments() {
    # Handle help and version flags
    if [[ $# -eq 0 ]]; then
        print_error "No command specified"
        echo ""
        print_usage
        exit 1
    fi
    
    case "$1" in
        -h|--help)
            print_usage
            exit 0
            ;;
        -v|--version)
            print_version
            exit 0
            ;;
        *)
            # Valid command, continue execution
            return 0
            ;;
    esac
}

validate_command() {
    local command="$1"
    local valid_commands=("lexer" "syntax" "semantic" "run")
    
    if [[ ! " ${valid_commands[*]} " =~ " ${command} " ]]; then
        print_error "Invalid command: $command"
        echo ""
        echo "Valid commands are: ${valid_commands[*]}"
        echo ""
        print_usage
        exit 1
    fi
}

validate_file() {
    local file="$1"
    
    if [[ -z "$file" ]]; then
        print_error "No file specified for command"
        echo ""
        print_usage
        exit 1
    fi
    
    # For 'run' command, file might be a FRISC assembly file
    # For other commands, it should be a C source file
    if [[ ! -f "$file" ]] && [[ ! -f "$SCRIPT_DIR/$file" ]]; then
        print_warning "File not found: $file"
        echo ""
        echo "Please ensure the file exists and the path is correct."
        exit 1
    fi
}

run_compiler() {
    local command="$1"
    shift
    
    # Validate command
    validate_command "$command"
    
    # Validate file if command requires it
    if [[ "$command" != "run" ]] || [[ $# -gt 0 ]]; then
        if [[ $# -eq 0 ]]; then
            print_error "No file specified for command: $command"
            echo ""
            print_usage
            exit 1
        fi
        validate_file "$1"
    fi
    
    # Execute compiler
    print_info "Executing compiler command: $command"
    echo ""
    
    if "$JAVA_CMD" -jar "$JAR_FILE" "$command" "$@"; then
        echo ""
        print_success "Command completed successfully"
        return 0
    else
        local exit_code=$?
        echo ""
        print_error "Command failed with exit code: $exit_code"
        return $exit_code
    fi
}

################################################################################
# Main Execution
################################################################################

main() {
    # Change to script directory
    cd "$SCRIPT_DIR" || exit 1
    
    # Parse arguments (help/version handled here)
    parse_arguments "$@"
    
    # Check prerequisites
    check_java
    check_jar_exists
    
    # Run compiler with all arguments
    run_compiler "$@"
}

# Run main function
main "$@"
