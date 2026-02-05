#!/bin/bash

################################################################################
# PPJ Compiler Build Script
#
# This script builds the entire PPJ compiler project using Maven, creating a
# single executable JAR file suitable for distribution and execution.
#
# Usage:
#   ./build.sh [OPTIONS]
#
# Options:
#   -h, --help      Show this help message and exit
#   -v, --verbose   Enable verbose Maven output
#   -t, --tests     Run tests during build (default: skip tests)
#   -c, --clean     Clean build artifacts before building
#
# Exit Codes:
#   0   Build completed successfully
#   1   Build failed or invalid arguments
#   2   Prerequisites not met (Java/Maven not found)
#
# Author: Karlo Knežević
# Website: https://karloknezevic.github.io/
################################################################################

set -euo pipefail

# Script configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR_FILE="cli/target/ccompiler.jar"
SKIP_TESTS=true
VERBOSE=false
CLEAN_BUILD=false

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
    BOLD=''
    NC=''
fi

################################################################################
# Helper Functions
################################################################################

print_usage() {
    cat << EOF
${BOLD}PPJ Compiler Build Script${NC}

${BOLD}Usage:${NC}
    ./build.sh [OPTIONS]

${BOLD}Options:${NC}
    -h, --help      Show this help message and exit
    -v, --verbose   Enable verbose Maven output
    -t, --tests     Run tests during build (default: skip tests)
    -c, --clean     Clean build artifacts before building

${BOLD}Examples:${NC}
    ./build.sh                    # Quick build (skip tests)
    ./build.sh -t                 # Build with tests
    ./build.sh -v -c              # Verbose clean build
    ./build.sh -t -v              # Build with tests and verbose output

${BOLD}Output:${NC}
    The compiled JAR file will be located at:
    ${CYAN}cli/target/ccompiler.jar${NC}

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

check_prerequisites() {
    local missing_deps=()
    
    if ! command -v java &> /dev/null; then
        missing_deps+=("Java")
    else
        local java_version
        java_version=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
        if [[ "$java_version" -lt 21 ]]; then
            print_warning "Java version $java_version detected. Java 21+ is recommended."
        fi
    fi
    
    if ! command -v mvn &> /dev/null; then
        missing_deps+=("Maven")
    fi
    
    if [[ ${#missing_deps[@]} -gt 0 ]]; then
        print_error "Missing required dependencies: ${missing_deps[*]}"
        echo ""
        echo "Please install the missing dependencies:"
        [[ " ${missing_deps[*]} " =~ " Java " ]] && echo "  - Java 21+: https://openjdk.org/"
        [[ " ${missing_deps[*]} " =~ " Maven " ]] && echo "  - Maven 3.8+: https://maven.apache.org/"
        exit 2
    fi
}

parse_arguments() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            -h|--help)
                print_usage
                exit 0
                ;;
            -v|--verbose)
                VERBOSE=true
                shift
                ;;
            -t|--tests)
                SKIP_TESTS=false
                shift
                ;;
            -c|--clean)
                CLEAN_BUILD=true
                shift
                ;;
            *)
                print_error "Unknown option: $1"
                echo ""
                print_usage
                exit 1
                ;;
        esac
    done
}

build_project() {
    local mvn_args=("clean" "package")
    
    if [[ "$SKIP_TESTS" == true ]]; then
        mvn_args+=("-DskipTests" "-Dspotbugs.skip=true")
        print_info "Tests will be skipped during build"
    else
        print_info "Tests will be executed during build"
    fi
    
    if [[ "$VERBOSE" == false ]]; then
        mvn_args+=("-q")
    fi
    
    echo ""
    echo -e "${BOLD}${CYAN}Building PPJ Compiler...${NC}"
    echo ""
    
    if [[ "$VERBOSE" == true ]]; then
        print_info "Maven command: mvn ${mvn_args[*]}"
        echo ""
    fi
    
    # Execute Maven build
    if mvn "${mvn_args[@]}"; then
        return 0
    else
        return 1
    fi
}

verify_build() {
    if [[ ! -f "$JAR_FILE" ]]; then
        print_error "Build completed but JAR file not found: $JAR_FILE"
        return 1
    fi
    
    local jar_size
    jar_size=$(du -h "$JAR_FILE" | cut -f1)
    
    print_success "Build completed successfully!"
    echo ""
    echo -e "  ${BOLD}JAR Location:${NC} ${CYAN}$JAR_FILE${NC}"
    echo -e "  ${BOLD}JAR Size:${NC}     $jar_size"
    echo ""
    
    return 0
}

print_next_steps() {
    echo -e "${BOLD}Next Steps:${NC}"
    echo ""
    echo "  To run the compiler:"
    echo -e "    ${CYAN}./run.sh [flags] <source_file.c>${NC}"
    echo ""
    echo "  Or directly:"
    echo -e "    ${CYAN}java -jar $JAR_FILE [flags] <source_file.c>${NC}"
    echo ""
    echo "  Common flags:"
    echo "    --lex    - Lexical analysis only"
    echo "    --parse  - Syntax analysis (includes lexical)"
    echo "    --sem    - Semantic analysis (includes lexical + parse)"
    echo "    --ir     - IR generation (includes semantic)"
    echo "    --frisc  - FRISC generation (includes IR)"
    echo "    --run    - Execute FRISC output"
    echo "    --all    - Run all compile stages"
    echo ""
}

################################################################################
# Main Execution
################################################################################

main() {
    # Parse command-line arguments
    parse_arguments "$@"
    
    # Change to script directory
    cd "$SCRIPT_DIR" || exit 1
    
    # Check prerequisites
    print_info "Checking prerequisites..."
    check_prerequisites
    print_success "All prerequisites met"
    
    # Clean if requested
    if [[ "$CLEAN_BUILD" == true ]]; then
        print_info "Cleaning build artifacts..."
        mvn clean -q || {
            print_error "Failed to clean build artifacts"
            exit 1
        }
        print_success "Build artifacts cleaned"
    fi
    
    # Build project
    if build_project; then
        # Verify build output
        if verify_build; then
            print_next_steps
            exit 0
        else
            exit 1
        fi
    else
        print_error "Build failed. Check the output above for details."
        exit 1
    fi
}

# Run main function
main "$@"
