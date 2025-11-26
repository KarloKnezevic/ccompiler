# FRISC Simulator Usage Guide

This comprehensive guide covers all aspects of using the FRISC processor simulator, including both console and web-based interfaces, configuration options, debugging features, and integration with the PPJ compiler code generator.

## Table of Contents

- [Overview](#overview)
- [Console Simulator](#console-simulator)
- [Web-Based Simulator](#web-based-simulator)
- [FRISC Assembly Format](#frisc-assembly-format)
- [Configuration Options](#configuration-options)
- [Debugging and Monitoring](#debugging-and-monitoring)
- [Integration with PPJ Compiler](#integration-with-ppj-compiler)
- [Troubleshooting](#troubleshooting)
- [Examples and Use Cases](#examples-and-use-cases)

## Overview

FRISCjs is a complete FRISC processor simulator written in JavaScript, providing both command-line and web-based interfaces for executing FRISC assembly programs. The simulator includes:

- **FRISC Assembler**: Converts FRISC assembly code to machine code
- **FRISC CPU Simulator**: Executes machine code with full processor state simulation
- **Memory Management**: 256KB addressable memory with configurable size
- **I/O Support**: Memory-mapped I/O device simulation
- **Debugging Tools**: Step-by-step execution, breakpoints, and state monitoring

### Architecture Support

The simulator implements the complete FRISC architecture:
- **Registers**: R0-R7 general-purpose registers, PC, SR with flags
- **Memory**: 256KB byte-addressable memory (0x00000000 to 0x0003FFFF)
- **Instruction Set**: Complete FRISC instruction set including ALU, memory, and control operations
- **Interrupts**: Maskable and non-maskable interrupt support
- **I/O Devices**: Memory-mapped peripheral simulation

## Console Simulator

### Installation and Location

The console simulator is located at:
```
node_modules/friscjs/consoleapp/frisc-console.js
```

### Basic Usage

#### Running from File
```bash
node node_modules/friscjs/consoleapp/frisc-console.js program.frisc
```

#### Running from Standard Input
```bash
cat program.frisc | node node_modules/friscjs/consoleapp/frisc-console.js
```

#### Interactive Input
```bash
node node_modules/friscjs/consoleapp/frisc-console.js
# Type FRISC assembly code line by line
# Press Ctrl+D to execute
```

### Command Line Options

#### Verbose Mode (`-v`)
Enables detailed debugging output showing CPU state at each instruction:
```bash
node node_modules/friscjs/consoleapp/frisc-console.js -v program.frisc
```

**Verbose Output Includes**:
- CPU state before and after each instruction
- Register values (R0-R7, PC, SR)
- Status flags (C, V, Z, N, GIE, interrupts)
- Instruction being executed
- Memory access operations

#### CPU Frequency (`-cpufreq`)
Sets the simulation frequency in Hz (default: 1000):
```bash
node node_modules/friscjs/consoleapp/frisc-console.js -cpufreq 2000 program.frisc
```

**Use Cases**:
- **Low frequency (1-10 Hz)**: Detailed observation of execution
- **High frequency (1000+ Hz)**: Fast program execution
- **Custom frequency**: Match specific timing requirements

#### Memory Size (`-memsize`)
Sets memory size in KB (default: 256KB):
```bash
node node_modules/friscjs/consoleapp/frisc-console.js -memsize 64 program.frisc
```

**Memory Configurations**:
- **64KB**: Minimal configuration for simple programs
- **256KB**: Default configuration (recommended)
- **512KB**: Extended memory for large programs

#### Combining Options
Multiple options can be combined:
```bash
node node_modules/friscjs/consoleapp/frisc-console.js -v -cpufreq 10 -memsize 128 program.frisc
```

### Output Format

The console simulator provides structured output:

```
*********************************************************
** FRISCjs - FRISC simulator in JavaScript
** 
** Usage instructions and configuration info
*********************************************************

*********************************************************
Reading program from file: program.frisc
*********************************************************

*********************************************************
Input FRISC program:
*********************************************************
[Assembly code listing]

*********************************************************
Parsing input FRISC program.
*********************************************************

*********************************************************
Starting simulation!
*********************************************************
[Verbose debugging output if -v enabled]

*********************************************************
FRISC processor stopped! Status of CPU R6: [return_value]
*********************************************************

[return_value]
```

**Key Output Elements**:
- **Program Listing**: Complete assembly code being executed
- **Parsing Status**: Assembly compilation results
- **Execution Log**: Step-by-step execution (verbose mode)
- **Final State**: R6 register value (program return code)
- **Return Value**: Final R6 value on stdout for script integration

## Web-Based Simulator

### Accessing the Web Interface

The web-based simulator provides a graphical interface with advanced features:

#### Local Access
```bash
# Open in browser
open node_modules/friscjs/webapp/index.html
```

#### Online Version
```
https://fer-ppj.github.com/FRISCjs/main.html
```

### Web Interface Features

#### Code Editor
- **Syntax Highlighting**: FRISC assembly syntax highlighting
- **Line Numbers**: Easy navigation and error location
- **Auto-completion**: Instruction and register completion
- **Error Highlighting**: Real-time syntax error detection

#### Execution Control
- **Run**: Execute complete program
- **Step**: Single-step execution
- **Reset**: Reset CPU and memory state
- **Stop**: Halt execution at any point

#### Debugging Tools
- **Breakpoints**: Set breakpoints on specific lines
- **Watch Variables**: Monitor register and memory values
- **Call Stack**: Function call hierarchy
- **Memory Viewer**: Inspect memory contents

#### Visualization
- **Register Display**: Real-time register value updates
- **Memory Map**: Visual memory layout and usage
- **Execution Flow**: Instruction pointer tracking
- **I/O Devices**: Peripheral device status and control

### Web Interface Tabs

#### Simulator Tab
Main execution environment with:
- Assembly code editor
- CPU state display
- Memory viewer
- I/O device controls
- Execution controls

#### Load/Save Tab
Configuration management:
- **Save Configuration**: Store current setup locally
- **Load Configuration**: Restore previous setup
- **Export/Import**: Share configurations via text format
- **Example Programs**: Pre-loaded demonstration programs

#### Usage Instructions Tab
Built-in help and documentation:
- Instruction set reference
- Assembly syntax guide
- Programming examples
- Debugging tips

#### Examples Tab
Sample programs demonstrating:
- Basic arithmetic operations
- Control flow constructs
- Memory management
- I/O device usage
- Interrupt handling

#### About Tab
Project information:
- Version details
- Author credits
- License information
- Bug reporting links

## FRISC Assembly Format

### Syntax Requirements

The FRISC simulator expects specific assembly format:

#### Instruction Format
```assembly
        INSTRUCTION operand1, operand2, operand3    ; comment
```

**Key Requirements**:
- **Indentation**: Instructions must be indented (8 spaces recommended)
- **Case Insensitive**: Instructions and registers can be upper or lowercase
- **Operand Separation**: Comma-separated operands
- **Comments**: Semicolon-prefixed comments

#### Label Format
```assembly
LABEL_NAME                      ; Label definition
        INSTRUCTION ...         ; Indented instruction
```

#### Number Formats
```assembly
        MOVE 42, R0             ; Decimal number
        MOVE 0x2A, R0           ; Hexadecimal number (if supported)
        MOVE LABEL, R0          ; Label reference
```

#### Directive Format
```assembly
        `ORG 0                  ; Assembler directive
        `DW %D 42               ; Data word directive
        `EQU CONSTANT 100       ; Symbol definition
```

### Common Formatting Issues

#### Incorrect Indentation
```assembly
MOVE 42, R0                     ; ❌ No indentation - parsing error
```

#### Correct Indentation
```assembly
        MOVE 42, R0             ; ✅ Proper indentation
```

#### Large Numbers
```assembly
        MOVE 40000, R7          ; May cause parsing issues
        MOVE 1000, R7           ; Safer for stack pointer
```

## Configuration Options

### Memory Configuration

#### Default Memory Layout
```
0x00000000 - 0x0003FFFF: 256KB addressable memory
0x00000000 - 0x00000FFF: Program code area
0x00001000 - 0x0000FFFF: Data area
0x00010000 - 0x0003EFFF: Heap/stack area
0x0003F000 - 0x0003FFFF: I/O mapped devices
```

#### Custom Memory Sizes
```bash
# 64KB memory
node frisc-console.js -memsize 64 program.frisc

# 512KB memory
node frisc-console.js -memsize 512 program.frisc
```

### CPU Configuration

#### Frequency Settings
```bash
# Slow execution for debugging
node frisc-console.js -cpufreq 1 program.frisc

# Normal execution
node frisc-console.js -cpufreq 1000 program.frisc

# Fast execution
node frisc-console.js -cpufreq 10000 program.frisc
```

#### Register Initialization
All registers start at 0 except:
- **PC**: Set to program entry point (usually 0)
- **SP (R7)**: Must be initialized by program
- **SR**: Status register starts at 0

### I/O Device Configuration

#### Memory-Mapped I/O
Standard I/O device addresses:
```
0xFFFF0000: GPIO control register
0xFFFF0004: GPIO data register
0xFFFF1000: Timer control register
0xFFFF1004: Timer data register
```

## Debugging and Monitoring

### Verbose Mode Output

#### CPU State Format
```
## CPU state: r0: 42 r1: 0 r2: 0 r3: 0 r4: 0 r5: 0 r6: 0 r7: 1000 pc: 4 sr: 0 ( INT2: 0 INT1: 0 INT0: 0 GIE: 0 EINT2: 0 EINT1: 0 EINT0: 0 Z: 0 V: 0 C: 0 N: 0 ) iif: 1
```

**State Information**:
- **r0-r7**: General-purpose register values
- **pc**: Program counter (next instruction address)
- **sr**: Status register value
- **Flags**: Individual status flag values
- **iif**: Internal interrupt flag

#### Instruction Execution Log
```
## Executing FRISC instruction: MOVE 42, R0
## Executing FRISC instruction: ADD R0, R1, R2
## Executing FRISC instruction: HALT
```

### Breakpoint Usage (Web Interface)

#### Setting Breakpoints
1. Click on line number in code editor
2. Red dot appears indicating breakpoint
3. Program will pause at breakpoint during execution

#### Breakpoint Actions
- **Continue**: Resume execution to next breakpoint
- **Step Over**: Execute next instruction
- **Step Into**: Enter function calls
- **Step Out**: Exit current function

### Memory Inspection

#### Memory Viewer (Web Interface)
- **Address Range**: Specify memory range to view
- **Data Format**: View as hex, decimal, or ASCII
- **Real-time Updates**: Memory changes during execution
- **Search Function**: Find specific values or patterns

#### Console Memory Debugging
Use verbose mode to see memory operations:
```
## Executing FRISC instruction: LOAD R0, (1000)
## Executing FRISC instruction: STORE R1, (2000)
```

## Integration with PPJ Compiler

### Automatic Testing Workflow

#### Complete Compilation and Execution
```bash
# 1. Compile C program to FRISC assembly
./run.sh examples/valid/program.c

# 2. Execute generated assembly
node node_modules/friscjs/consoleapp/frisc-console.js compiler-bin/a.frisc

# 3. Check return value
echo $?
```

#### Automated Testing Script

A convenient test script `test_frisc.sh` is provided for automated testing:

```bash
#!/bin/bash
# Usage: ./test_frisc.sh program.c [expected_result]

./test_frisc.sh examples/valid/program1.c 0
./test_frisc.sh examples/valid/program2.c 5
```

**Features**:
- Automatic compilation and execution
- Expected result verification
- Generated code display
- Clear pass/fail indicators
- Error handling and reporting

**Example Usage**:
```bash
# Test simple program
./test_frisc.sh examples/valid/program1.c 0

# Test without expected result (just run)
./test_frisc.sh examples/valid/program2.c

# Output includes:
# 🔧 Compiling examples/valid/program1.c...
# 🚀 Running FRISC simulator...
# 📊 Program returned: 0
# ✅ Test PASSED (expected: 0)
# 🎯 Generated FRISC code: [assembly listing]
```

### Debugging Generated Code

#### Verbose Execution
```bash
# Generate code
./run.sh examples/valid/program.c

# Debug execution
node node_modules/friscjs/consoleapp/frisc-console.js -v compiler-bin/a.frisc > debug.log 2>&1

# Analyze debug log
less debug.log
```

#### Code Quality Verification
```bash
# Check generated assembly format
cat compiler-bin/a.frisc

# Verify proper indentation and syntax
node -e "
const fs = require('fs');
const code = fs.readFileSync('compiler-bin/a.frisc', 'utf8');
const lines = code.split('\n');
lines.forEach((line, i) => {
    if (line.trim() && !line.startsWith(';') && !line.endsWith(':')) {
        if (!line.startsWith('        ')) {
            console.log(\`Line \${i+1}: Missing indentation: '\${line}'\`);
        }
    }
});
"
```

### Performance Analysis

#### Execution Timing
```bash
# Time program execution
time node node_modules/friscjs/consoleapp/frisc-console.js compiler-bin/a.frisc

# Count instructions executed
node node_modules/friscjs/consoleapp/frisc-console.js -v compiler-bin/a.frisc 2>&1 | grep "Executing FRISC instruction" | wc -l
```

#### Memory Usage Analysis
```bash
# Analyze memory usage patterns
node node_modules/friscjs/consoleapp/frisc-console.js -v compiler-bin/a.frisc 2>&1 | grep -E "(LOAD|STORE|PUSH|POP)"
```

## Troubleshooting

### Common Issues and Solutions

#### Parsing Errors

**Problem**: `Parsing error on line X column Y`
```
Parsing error on line 2 column 6 -- SyntaxError: Expected instruction but "4" found.
```

**Solutions**:
1. **Check Indentation**: Ensure instructions are properly indented
2. **Verify Syntax**: Check instruction format and operands
3. **Number Format**: Use appropriate number formats
4. **Label Format**: Ensure labels end with colon

#### Execution Errors

**Problem**: Program doesn't execute or crashes
**Solutions**:
1. **Stack Initialization**: Ensure R7 is properly initialized
2. **Memory Bounds**: Check memory access within valid range
3. **Instruction Alignment**: Verify instruction addresses are word-aligned

#### Incorrect Results

**Problem**: Program returns unexpected values
**Solutions**:
1. **Use Verbose Mode**: Trace execution step by step
2. **Check Register Values**: Monitor register changes
3. **Verify Logic**: Compare with expected algorithm
4. **Memory Inspection**: Check memory contents

### Error Messages Reference

#### Assembly Errors
```
Parsing error on line X column Y -- SyntaxError: Expected "instruction" but "token" found.
```
- **Cause**: Invalid instruction syntax
- **Fix**: Check instruction format and indentation

#### Runtime Errors
```
Loading error -- Invalid memory address
```
- **Cause**: Memory access outside valid range
- **Fix**: Check memory addresses and bounds

#### Configuration Errors
```
ERROR: File does not exist!
```
- **Cause**: Invalid file path
- **Fix**: Verify file exists and path is correct

### Performance Issues

#### Slow Execution
**Symptoms**: Program takes too long to execute
**Solutions**:
1. Increase CPU frequency: `-cpufreq 10000`
2. Disable verbose mode
3. Optimize generated code

#### Memory Issues
**Symptoms**: Out of memory errors
**Solutions**:
1. Increase memory size: `-memsize 512`
2. Optimize memory usage in program
3. Check for memory leaks

## Examples and Use Cases

### Basic Program Testing

#### Simple Return Value Test
```c
// test.c
int main(void) {
    return 42;
}
```

```bash
# Compile and test
./run.sh test.c
node node_modules/friscjs/consoleapp/frisc-console.js compiler-bin/a.frisc
# Expected output: 42
```

#### Variable Assignment Test
```c
// variables.c
int main(void) {
    int x = 10;
    int y = 20;
    return x + y;
}
```

```bash
# Test with debugging
./run.sh variables.c
node node_modules/friscjs/consoleapp/frisc-console.js -v compiler-bin/a.frisc
# Expected output: 30
```

### Function Call Testing

#### Function with Parameters
```c
// function.c
int add(int a, int b) {
    return a + b;
}

int main(void) {
    return add(15, 25);
}
```

```bash
# Test function calls
./run.sh function.c
node node_modules/friscjs/consoleapp/frisc-console.js compiler-bin/a.frisc
# Expected output: 40
```

### Control Flow Testing

#### Conditional Statements
```c
// conditional.c
int main(void) {
    int x = 10;
    if (x > 5) {
        return 1;
    } else {
        return 0;
    }
}
```

#### Loop Constructs
```c
// loop.c
int main(void) {
    int sum = 0;
    int i;
    for (i = 1; i <= 5; i++) {
        sum = sum + i;
    }
    return sum;  // Expected: 15
}
```

### Advanced Testing

#### Recursive Functions
```c
// factorial.c
int factorial(int n) {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

int main(void) {
    return factorial(5);  // Expected: 120
}
```

#### Array Operations
```c
// array.c
int main(void) {
    int arr[3];
    arr[0] = 10;
    arr[1] = 20;
    arr[2] = 30;
    return arr[0] + arr[1] + arr[2];  // Expected: 60
}
```

### Batch Testing

#### Test Suite Script
```bash
#!/bin/bash
# run_tests.sh

TESTS=(
    "examples/valid/program1.c:0"
    "examples/valid/program2.c:5"
    "examples/valid/program3.c:30"
)

for test in "${TESTS[@]}"; do
    IFS=':' read -r program expected <<< "$test"
    echo "Testing $program (expected: $expected)"
    
    ./run.sh "$program"
    result=$(node node_modules/friscjs/consoleapp/frisc-console.js compiler-bin/a.frisc 2>/dev/null)
    
    if [ "$result" = "$expected" ]; then
        echo "✅ PASS"
    else
        echo "❌ FAIL (got: $result)"
    fi
    echo
done
```

### Web Interface Usage

#### Loading Programs
1. Open web interface in browser
2. Paste FRISC assembly code in editor
3. Click "Run" to execute
4. Monitor execution in real-time

#### Setting Breakpoints
1. Click on line numbers to set breakpoints
2. Use "Step" button for single-step execution
3. Inspect register and memory values
4. Continue execution or reset as needed

#### Saving Configurations
1. Go to "Load/Save" tab
2. Enter configuration name
3. Click "Save" to store locally
4. Use "Export" to share configurations

This comprehensive guide covers all aspects of FRISC simulator usage, from basic command-line execution to advanced debugging and integration with the PPJ compiler. The simulator provides powerful tools for testing and validating generated FRISC assembly code.
