# Valid Test Programs

This directory contains valid C programs that should pass lexical, syntax, and semantic analysis.

## Program Descriptions

### Basic Programs

**program1.c**
- Minimal valid program with main function returning 0
- Tests: basic function declaration, return statement

**program2.c**
- Simple variable declaration and assignment
- Tests: variable declaration, assignment operator, return with variable

**program3.c**
- Multiple variable declarations and arithmetic operations
- Tests: addition operator, multiple declarations

**program4.c**
- Subtraction operation
- Tests: subtraction operator

**program5.c**
- Multiplication operation
- Tests: multiplication operator

**program6.c**
- Division operation
- Tests: division operator

**program7.c**
- Modulo operation
- Tests: modulo operator

### Conditional Statements

**program8.c**
- Simple if statement without else
- Tests: if statement, comparison operator (>)

**program9.c**
- If-else statement
- Tests: if-else construct

**program10.c**
- If-else with less-than comparison
- Tests: less-than operator (<)

**program11.c**
- Greater-than-or-equal comparison
- Tests: >= operator

**program12.c**
- Less-than-or-equal comparison
- Tests: <= operator

**program13.c**
- Equality comparison
- Tests: == operator

**program14.c**
- Inequality comparison
- Tests: != operator

**program15.c**
- Logical AND operator
- Tests: && operator, compound conditions

**program16.c**
- Logical OR operator
- Tests: || operator

**program17.c**
- Logical NOT operator
- Tests: ! operator

### Loops

**program18.c**
- While loop
- Tests: while statement, loop increment

**program19.c**
- While loop with break statement
- Tests: break statement inside while loop

**program20.c**
- While loop with continue statement
- Tests: continue statement, modulo in condition

**program21.c**
- For loop
- Tests: for statement with initialization, condition, increment

**program22.c**
- For loop with sum calculation
- Tests: for loop with accumulator pattern

**program23.c**
- For loop with break
- Tests: break in for loop

**program24.c**
- For loop with continue
- Tests: continue in for loop

### Functions

**program25.c**
- Simple function with two parameters
- Tests: function definition, function call, parameters

**program26.c**
- Function with parameters and return value
- Tests: function call with variables as arguments

**program27.c**
- Void function
- Tests: void return type, function without return value

**program28.c**
- Function returning square
- Tests: function with single parameter, return expression

**program29.c**
- Function with conditional return
- Tests: if-else in function, multiple return statements

**program30.c**
- Function with conditional return (simplified)
- Tests: function with if statement, single return

**program31.c**
- Function returning char
- Tests: char return type, character literal

**program32.c**
- Function returning float
- Tests: float return type, floating-point literal

### Recursion

**program33.c**
- Recursive factorial function
- Tests: recursion, base case, recursive call

**program34.c**
- Recursive fibonacci function
- Tests: recursion with multiple recursive calls

**program35.c**
- Array sum function
- Tests: array parameters, array indexing, loops with arrays

**program36.c**
- Recursive power function
- Tests: recursion with base case check

**program37.c**
- Recursive GCD (Euclidean algorithm)
- Tests: recursion with modulo operation

### Complex Control Flow

**program38.c**
- Nested for loops
- Tests: nested loops, loop counters

**program39.c**
- Nested if statements
- Tests: nested conditionals

**program40.c**
- Nested for loops with break
- Tests: break in nested loops

**program76.c**
- While loop with break condition
- Tests: break based on accumulated value

**program77.c**
- While loop with continue and break
- Tests: continue and break in same loop

**program78.c**
- For loop with continue and break
- Tests: continue and break in for loop

### Structures

**program41.c**
- Simple struct definition and usage
- Tests: struct definition, struct member access

**program42.c**
- Struct as function parameter
- Tests: struct passed to function, struct member access in function

**program43.c**
- Struct with multiple members
- Tests: struct with multiple fields

**program64.c**
- Struct with float members
- Tests: struct with float fields

### Assignment Operators

**program44.c**
- Increment with addition
- Tests: x = x + 1 pattern

**program45.c**
- Decrement with subtraction
- Tests: x = x - 1 pattern

**program46.c**
- Addition via explicit assignment
- Tests: x = x + value pattern

**program47.c**
- Subtraction via explicit assignment
- Tests: x = x - value pattern

**program48.c**
- Multiplication via explicit assignment
- Tests: x = x * value pattern

**program49.c**
- Division via explicit assignment
- Tests: x = x / value pattern

**program50.c**
- Modulo via explicit assignment
- Tests: x = x % value pattern

### Increment/Decrement Operators

**program51.c**
- Post-increment operator
- Tests: x++ operator

**program52.c**
- Post-decrement operator
- Tests: x-- operator

**program53.c**
- Pre-increment operator
- Tests: ++x operator

**program54.c**
- Pre-decrement operator
- Tests: --x operator

### Arrays

**program55.c**
- Array declaration and indexing
- Tests: array declaration, array assignment, array access

**program60.c**
- Array with function (find max)
- Tests: array passed to function, array iteration

**program61.c**
- Array with function (linear search)
- Tests: array search algorithm

**program62.c**
- Recursive binary search
- Tests: recursion with array, array bounds

**program63.c**
- Recursive array sum
- Tests: recursion with array parameter

### Types

**program56.c**
- Char variable
- Tests: char type, character literal

**program57.c**
- Float variable
- Tests: float type, floating-point literal

**program58.c**
- Const global variable
- Tests: const keyword, global variable

**program59.c**
- Const local variable
- Tests: const keyword, local variable

### Complex Expressions

**program65.c**
- Function call in condition
- Tests: function call as boolean expression

**program66.c**
- While loop with division
- Tests: division in loop condition

**program67.c**
- Number reversal algorithm
- Tests: complex arithmetic expressions

**program68.c**
- Prime number check
- Tests: nested loops, modulo in condition

**program69.c**
- Expression with operator precedence
- Tests: multiplication before addition

**program70.c**
- Expression with parentheses
- Tests: parentheses for precedence control

**program71.c**
- Logical expression result
- Tests: logical expression assignment

**program72.c**
- Multiple AND conditions
- Tests: chained && operators

**program73.c**
- Multiple OR conditions
- Tests: chained || operators

**program74.c**
- Nested if with function
- Tests: nested conditionals with function call

**program75.c**
- Triple nested loops
- Tests: three levels of nesting

### Multiple Parameters

**program79.c**
- Function with three parameters
- Tests: function with multiple parameters

**program80.c**
- Function with four parameters
- Tests: function with many parameters

### Pointers

**program81.c**
- Pointer basic assignment
- Tests: taking address and modifying through pointer

**program82.c**
- Pointer parameter usage
- Tests: passing pointer into function

**program83.c**
- Pointer to pointer
- Tests: double dereference

**program84.c**
- Pointer arithmetic over array
- Tests: advancing pointer and dereferencing

**program85.c**
- Pointer difference computation
- Tests: subtracting pointers to same array

**program86.c**
- Pointer-based increment helper
- Tests: modifying value through pointer parameter

**program87.c**
- Pointer comparison
- Tests: comparing addresses to choose value

**program88.c**
- Pointer iteration sum
- Tests: iterating with pointer while loop

**program89.c**
- Pointer to struct with arrow operator
- Tests: accessing struct fields through pointer

**program90.c**
- Pointer traversal with accumulation
- Tests: summing array via pointer movement

