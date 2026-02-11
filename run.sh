#!/bin/bash

################################################################################
# PPJ Compiler Run Script
#
# This script provides a convenient wrapper for executing the PPJ compiler
# from the built JAR file. It handles JAR file detection, Java execution,
# and provides helpful error messages.
#
# Usage:
#   ./run.sh [flags] <source_file.c>
#
# Flags:
#   --lex                 Run lexical analysis only
#   --parse               Run syntax analysis (includes lexical)
#   --sem                 Run semantic analysis (includes lex + parse)
#   --ir                  Generate IR (includes semantic)
#   --frisc               Generate FRISC (includes IR)
#   --run                 Execute FRISC output (includes FRISC generation)
#   --all                 Run all compile stages
#   --bin <dir>           Output directory (default: compiler-bin)
#   run-ir <file.ir>      Execute IR directly with interpreter
#   --trace-ir            (with run-ir) Print interpreter execution trace
#   --ir-step-limit <n>   (with run-ir) Override interpreter step watchdog
#   --run-ir-all-real-world [interpreter flags]
#                         Execute interpreter for all IR files in examples/real_world
#
# Options:
#   -h, --help             Show this help message and exit
#   -v, --version          Show compiler version information
#
# Examples:
#   ./run.sh --lex program.c
#   ./run.sh --frisc program.c
#   ./run.sh --all --run program.c
#   ./run.sh run-ir examples/real_world/real_bfs_shortest_path/program.ir
#   ./run.sh run-ir --ir-step-limit 500000 examples/real_world/real_bfs_shortest_path/program.ir
#   ./run.sh --run-ir-all-real-world --ir-step-limit 500000
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
    ./run.sh [flags] <source_file.c>
    ./run.sh [OPTIONS]

${BOLD}Flags:${NC}
    --lex                 Run lexical analysis only
    --parse               Run syntax analysis (includes lexical)
    --sem                 Run semantic analysis (includes lex + parse)
    --ir                  Generate IR (includes semantic)
    --frisc               Generate FRISC (includes IR)
    --run                 Execute FRISC output (includes FRISC generation)
    --all                 Run all compile stages
    --bin <dir>           Output directory (default: compiler-bin)
    run-ir <file.ir>      Execute IR directly with interpreter
    --trace-ir            (with run-ir) Print interpreter execution trace
    --ir-step-limit <n>   (with run-ir) Override interpreter step watchdog
    --run-ir-all-real-world [interpreter flags]
                          Execute interpreter for all IR files in examples/real_world

${BOLD}Options:${NC}
    -h, --help             Show this help message and exit
    -v, --version          Show compiler version information

${BOLD}Examples:${NC}
    ./run.sh --lex program.c
    ./run.sh --frisc program.c
    ./run.sh --all --run program.c
    ./run.sh run-ir examples/real_world/real_bfs_shortest_path/program.ir
    ./run.sh run-ir --ir-step-limit 500000 examples/real_world/real_bfs_shortest_path/program.ir
    ./run.sh --run-ir-all-real-world --ir-step-limit 500000
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
        print_warning "Compiler JAR not found: $JAR_FILE"
        build_compiler_jar
    fi
}

build_compiler_jar() {
    print_info "Building compiler JAR (this may take a moment)..."
    if ./build.sh; then
        print_success "Compiler JAR is ready: $JAR_FILE"
    else
        print_error "Automatic build failed."
        echo ""
        echo "Run this command manually and retry:"
        echo -e "  ${CYAN}./build.sh${NC}"
        echo ""
        exit 2
    fi
}

jar_is_stale() {
    if [[ ! -f "$JAR_FILE" ]]; then
        return 1
    fi

    local stale_source
    stale_source=$(find \
        cli compiler-lexer compiler-parser compiler-semantics compiler-ir compiler-codegen-frisc config \
        -path "*/target/*" -prune -o \
        -type f \( -name "*.java" -o -name "*.txt" -o -name "pom.xml" \) \
        -newer "$JAR_FILE" -print -quit 2>/dev/null || true)

    if [[ -n "$stale_source" ]]; then
        print_info "Detected newer source/config file: $stale_source"
        return 0
    fi
    return 1
}

ensure_jar_is_fresh() {
    check_jar_exists
    if jar_is_stale; then
        print_info "Rebuilding compiler JAR because sources are newer than the current binary."
        build_compiler_jar
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
        print_error "No arguments provided"
        echo ""
        print_usage
        exit 1
    fi

    case "$1" in
        -h|--help)
            print_usage
            if [[ -f "$JAR_FILE" ]]; then
                echo ""
                echo "${BOLD}CLI help (${JAR_FILE}):${NC}"
                "$JAVA_CMD" -jar "$JAR_FILE" --help
            fi
            exit 0
            ;;
        -v|--version)
            print_version
            exit 0
            ;;
        *)
            return 0
            ;;
    esac
}

run_compiler() {
    # Execute compiler with provided flags and source file
    print_info "Executing compiler"
    echo ""

    if "$JAVA_CMD" -jar "$JAR_FILE" "$@"; then
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

run_interpreter_all_real_world() {
    local ir_root="examples/real_world"
    local -a ir_args=("$@")
    local -a ir_files
    local total=0
    local failed=0

    if [[ ! -d "$ir_root" ]]; then
        print_error "Directory not found: $ir_root"
        return 1
    fi

    # Support both historical and reorganized naming conventions.
    while IFS= read -r ir_file; do
        ir_files+=("$ir_file")
    done < <(find "$ir_root" -type f \( -name "program.ir" -o -name "main.ir" \) | sort)

    if [[ ${#ir_files[@]} -eq 0 ]]; then
        print_error "No IR files found under $ir_root"
        return 1
    fi

    print_info "Running IR interpreter for ${#ir_files[@]} real_world program(s)"
    echo ""

    for ir_file in "${ir_files[@]}"; do
        total=$((total + 1))
        echo "[$total/${#ir_files[@]}] $ir_file"

        local output
        if output=$("$JAVA_CMD" -jar "$JAR_FILE" run-ir "${ir_args[@]}" "$ir_file" 2>&1); then
            local ret
            ret=$(printf '%s\n' "$output" | awk -F': ' '/Return value/{print $2}')
            print_success "OK  return=${ret:-unknown}"
        else
            failed=$((failed + 1))
            print_error "FAILED for $ir_file"
            echo "$output"
        fi
        echo ""
    done

    if [[ $failed -gt 0 ]]; then
        print_error "IR interpreter summary: $failed failed / $total total"
        return 1
    fi

    print_success "IR interpreter summary: all $total programs passed"
    return 0
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
    ensure_jar_is_fresh

    # Script-level batch interpreter command.
    if [[ "${1:-}" == "--run-ir-all-real-world" ]]; then
        shift
        run_interpreter_all_real_world "$@"
        return $?
    fi
    
    # Run compiler with all arguments
    run_compiler "$@"
}

# Run main function
main "$@"
