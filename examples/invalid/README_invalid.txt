# Invalid Test Programs

This directory contains invalid C programs that should fail lexical, syntax, or semantic analysis.

## Program Descriptions

### Syntax Errors - Missing Semicolons

**program1.c**
- Error Type: Syntax
- Description: Missing semicolon after variable declaration
- Location: Line 2, after `int x`

**program2.c**
- Error Type: Syntax
- Description: Missing semicolon after assignment statement
- Location: Line 2, after `int x = 5`

### Semantic Errors - Undeclared Variables

**program3.c**
- Error Type: Semantic
- Description: Use of undeclared variable `x` in if condition
- Location: Line 2, variable `x` used before declaration

**program4.c**
- Error Type: Semantic
- Description: Use of undeclared variable `y`
- Location: Line 3, variable `y` assigned without declaration

### Syntax Errors - Missing Parentheses

**program5.c**
- Error Type: Syntax
- Description: Missing closing parenthesis in if condition
- Location: Line 2, after `x > 0`

**program6.c**
- Error Type: Syntax
- Description: Missing opening brace after if condition
- Location: Line 2, after `if (x > 0)`

**program7.c**
- Error Type: Syntax
- Description: Missing closing parenthesis in while condition
- Location: Line 2, after `x < 10`

**program8.c**
- Error Type: Syntax
- Description: Missing closing parenthesis in for loop
- Location: Line 2, after `i = i + 1`

### Syntax Errors - Invalid Declarations

**program9.c**
- Error Type: Syntax
- Description: Missing identifier in declaration (just `int = 10;`)
- Location: Line 3, invalid declaration syntax

**program10.c**
- Error Type: Syntax
- Description: Return statement with type name instead of value
- Location: Line 2, `return int;` should be `return 0;`

### Lexical Errors - Invalid Identifiers

**program11.c**
- Error Type: Lexical
- Description: Identifier starting with digit (`9abc`)
- Location: Line 2, invalid identifier name

**program12.c**
- Error Type: Lexical
- Description: Identifier containing invalid character `@` (`x@`)
- Location: Line 2, invalid character in identifier

**program13.c**
- Error Type: Lexical
- Description: Identifier containing invalid character `#` (`x#`)
- Location: Line 2, invalid character in identifier

### Lexical Errors - Incomplete Literals

**program14.c**
- Error Type: Lexical
- Description: Incomplete character literal (missing closing quote)
- Location: Line 2, `'a` should be `'a'`

### Syntax Errors - Invalid Statements

**program15.c**
- Error Type: Syntax
- Description: Declaration after if without braces
- Location: Line 4, declaration `int y;` not allowed directly after if

**program16.c**
- Error Type: Syntax
- Description: If statement with else but no if body
- Location: Line 2, `if (x > 0) else` is invalid

**program17.c**
- Error Type: Syntax
- Description: Else statement with semicolon but no body
- Location: Line 5, `else;` is invalid

### Semantic Errors - Invalid Control Flow

**program18.c**
- Error Type: Semantic
- Description: Break statement outside of loop
- Location: Line 2, break not in loop context

**program19.c**
- Error Type: Semantic
- Description: Continue statement outside of loop
- Location: Line 2, continue not in loop context

**program20.c**
- Error Type: Semantic
- Description: Break statement in if statement (not in loop)
- Location: Line 4, break not in loop context

### Semantic Errors - Function Call Mismatches

**program21.c**
- Error Type: Semantic
- Description: Function call with too many arguments
- Location: Line 5, `f()` called with argument but takes none

**program22.c**
- Error Type: Semantic
- Description: Function call with too few arguments
- Location: Line 6, `f(5)` called but function requires 2 arguments

**program23.c**
- Error Type: Semantic
- Description: Function call with too many arguments
- Location: Line 5, `f(5, 10)` called but function takes only 1 argument

### Semantic Errors - Return Type Mismatches

**program24.c**
- Error Type: Semantic
- Description: Missing return value in non-void function
- Location: Line 2, `int main()` requires return value

**program25.c**
- Error Type: Semantic
- Description: Return value in void function
- Location: Line 2, `void f()` cannot return value

### Semantic Errors - Variable Redeclaration

**program26.c**
- Error Type: Semantic
- Description: Multiple declarations of same variable in same scope
- Location: Lines 2-3, `int x;` declared twice

**program27.c**
- Error Type: Semantic (may be valid depending on scoping rules)
- Description: Variable `x` declared in inner scope, then used in outer scope
- Location: Line 8, `x` from inner scope used in outer scope

### Semantic Errors - Type Mismatches

**program28.c**
- Error Type: Semantic
- Description: Attempt to add string to integer
- Location: Line 3, `x + "text"` is type mismatch

**program29.c**
- Error Type: Semantic
- Description: Complex type mismatch (float + string)
- Location: Line 5, `f + "text"` is type mismatch

**program30.c**
- Error Type: Semantic
- Description: Attempt to assign value to array (not array element)
- Location: Line 2, `arr = 5` should be `arr[0] = 5`

### Syntax Errors - Invalid Operators

**program31.c**
- Error Type: Syntax
- Description: Triple increment operator (`x++++;`)
- Location: Line 4, invalid operator sequence

**program32.c**
- Error Type: Syntax
- Description: Triple decrement operator (`x---;`)
- Location: Line 4, invalid operator sequence

### Semantic Errors - Scope Issues

**program33.c**
- Error Type: Semantic
- Description: Variable `y` used outside its scope
- Location: Line 8, `y` declared in if block, used outside

**program34.c**
- Error Type: Semantic
- Description: Variable `y` used outside for loop scope
- Location: Line 7, `y` declared in for loop, used outside

### Semantic Errors - Function Parameter Mismatches

**program35.c**
- Error Type: Semantic
- Description: Function call with too few arguments (zero instead of one)
- Location: Line 5, `f()` called but function requires 1 argument

**program44.c**
- Error Type: Semantic
- Description: Function call with too few arguments
- Location: Line 5, `f(1, 2)` called but function requires 3 arguments

**program45.c**
- Error Type: Semantic
- Description: Function call with too many arguments
- Location: Line 5, `f(1, 2, 3, 4)` called but function takes only 1 argument

### Syntax Errors - Unreachable Code

**program36.c**
- Error Type: Semantic (unreachable code)
- Description: Code after return statements in if-else
- Location: Line 7, `return 2;` is unreachable

**program37.c**
- Error Type: Semantic (unreachable code)
- Description: Variable declaration after return statements
- Location: Line 7, `int y;` is unreachable

**program38.c**
- Error Type: Semantic (unreachable code)
- Description: Variable declaration and assignment after return
- Location: Lines 7-8, unreachable code

**program39.c**
- Error Type: Semantic (unreachable code)
- Description: Variable declaration after return
- Location: Line 7, unreachable code

**program40.c**
- Error Type: Semantic (unreachable code)
- Description: Variable declaration after return
- Location: Line 7, unreachable code

**program43.c**
- Error Type: Semantic (unreachable code)
- Description: Variable declaration after return
- Location: Line 7, unreachable code

**program46.c**
- Error Type: Semantic (unreachable code)
- Description: Variable declaration after return
- Location: Line 7, unreachable code

**program67.c**
- Error Type: Semantic (unreachable code)
- Description: Variable declaration after return
- Location: Line 7, unreachable code

**program68.c**
- Error Type: Semantic (unreachable code)
- Description: Variable declaration after return
- Location: Line 7, unreachable code

**program69.c**
- Error Type: Semantic (unreachable code)
- Description: Variable declaration after return
- Location: Line 7, unreachable code

**program70.c**
- Error Type: Semantic (unreachable code)
- Description: Variable declaration after return
- Location: Line 7, unreachable code

### Syntax Errors - Incomplete Expressions

**program47.c**
- Error Type: Syntax
- Description: Incomplete addition expression (`x +;`)
- Location: Line 3, missing right operand

**program48.c**
- Error Type: Syntax
- Description: Incomplete multiplication expression (`x *;`)
- Location: Line 3, missing right operand

**program49.c**
- Error Type: Syntax
- Description: Incomplete compound assignment (`x +=;`)
- Location: Line 3, missing right operand

**program50.c**
- Error Type: Syntax
- Description: Incomplete compound assignment (`x -=;`)
- Location: Line 3, missing right operand

**program51.c**
- Error Type: Syntax
- Description: Incomplete compound assignment (`x *=;`)
- Location: Line 3, missing right operand

**program52.c**
- Error Type: Syntax
- Description: Incomplete compound assignment (`x /=;`)
- Location: Line 3, missing right operand

**program53.c**
- Error Type: Syntax
- Description: Incomplete compound assignment (`x %=;`)
- Location: Line 3, missing right operand

### Syntax Errors - Incomplete Operators

**program54.c**
- Error Type: Syntax
- Description: Incomplete logical AND (`&&` without right operand)
- Location: Line 3, `x > 0 &&` missing right side

**program55.c**
- Error Type: Syntax
- Description: Incomplete logical OR (`||` without right operand)
- Location: Line 3, `x > 0 ||` missing right side

**program56.c**
- Error Type: Syntax
- Description: Incomplete logical NOT (`!` without operand)
- Location: Line 3, `!` missing operand

**program57.c**
- Error Type: Syntax
- Description: Incomplete equality operator (`==` without right operand)
- Location: Line 3, `x ==` missing right side

**program58.c**
- Error Type: Syntax
- Description: Incomplete inequality operator (`!=` without right operand)
- Location: Line 3, `x !=` missing right side

**program59.c**
- Error Type: Syntax
- Description: Incomplete less-than operator (`<` without right operand)
- Location: Line 3, `x <` missing right side

**program60.c**
- Error Type: Syntax
- Description: Incomplete greater-than operator (`>` without right operand)
- Location: Line 3, `x >` missing right side

**program61.c**
- Error Type: Syntax
- Description: Incomplete less-than-or-equal operator (`<=` without right operand)
- Location: Line 3, `x <=` missing right side

**program62.c**
- Error Type: Syntax
- Description: Incomplete greater-than-or-equal operator (`>=` without right operand)
- Location: Line 3, `x >=` missing right side

### Syntax Errors - Incomplete Control Structures

**program63.c**
- Error Type: Syntax
- Description: While loop with empty condition
- Location: Line 3, `while ()` missing condition

**program64.c**
- Error Type: Syntax
- Description: For loop with missing initialization
- Location: Line 2, `for (; i < 10; ...)` missing init

**program65.c**
- Error Type: Syntax
- Description: For loop with missing condition
- Location: Line 2, `for (i = 0; ; ...)` missing condition

**program66.c**
- Error Type: Syntax
- Description: For loop with missing increment
- Location: Line 2, `for (i = 0; i < 10;)` missing increment

### Semantic Errors - Struct Issues

**program41.c**
- Error Type: Semantic
- Description: Struct definition inside function (may be invalid depending on grammar)
- Location: Line 3, struct definition in function body

**program42.c**
- Error Type: Semantic (or runtime)
- Description: Array index out of bounds (accessing arr[10] in array of size 10)
- Location: Line 3, valid indices are 0-9

