# Bibliography and Further Reading

## Overview

This document provides references to literature, resources, and further reading materials relevant to compiler construction and the PPJ compiler project.

## Core Textbooks

### Compiler Construction

1. **Siniša Srbljić**, *Prevođenje programskih jezika* (Translation of Programming Languages), Element, Zagreb, 2007.
   - Comprehensive textbook on compiler construction
   - Covers lexical analysis, parsing, semantic analysis, code generation
   - Used as primary reference for PPJ compiler design

2. **Siniša Srbljić**, *Uvod u teoriju računarstva* (Introduction to Theory of Computation), Element, Zagreb, 2007.
   - Formal language theory foundations
   - Automata theory (DFA, NFA, pushdown automata)
   - Context-free grammars and parsing theory

3. **Danko Basch, Mario Kovač**, *Osnove procesora FRISC (2. izd.)* (Fundamentals of FRISC Processor, 2nd ed.), Antonić, Zagreb, 2004.
   - FRISC processor architecture reference
   - Instruction set documentation
   - Assembly programming guide

### Standard References

4. **Alfred V. Aho, Monica S. Lam, Ravi Sethi, Jeffrey D. Ullman**, *Compilers: Principles, Techniques, and Tools* (2nd ed.), Pearson, 2006.
   - Classic compiler construction textbook (Dragon Book)
   - Comprehensive coverage of all compiler phases
   - Advanced topics: optimization, code generation

5. **Andrew W. Appel**, *Modern Compiler Implementation in Java* (2nd ed.), Cambridge University Press, 2002.
   - Java-based compiler implementation
   - Practical implementation techniques
   - Code generation and optimization

6. **Keith Cooper, Linda Torczon**, *Engineering a Compiler* (2nd ed.), Morgan Kaufmann, 2011.
   - Engineering perspective on compiler construction
   - Code generation and optimization focus
   - Practical implementation details

## Formal Language Theory

7. **John E. Hopcroft, Rajeev Motwani, Jeffrey D. Ullman**, *Introduction to Automata Theory, Languages, and Computation* (3rd ed.), Pearson, 2006.
   - Comprehensive automata theory
   - Regular languages and finite automata
   - Context-free languages and pushdown automata

8. **Michael Sipser**, *Introduction to the Theory of Computation* (3rd ed.), Cengage Learning, 2012.
   - Theoretical foundations
   - Formal language hierarchy
   - Computational complexity

## Parsing Theory

9. **Dick Grune, Ceriel J. H. Jacobs**, *Parsing Techniques: A Practical Guide* (2nd ed.), Springer, 2008.
   - Comprehensive parsing techniques
   - LR parsing algorithms
   - Error recovery strategies

10. **Terence Parr**, *Language Implementation Patterns*, Pragmatic Bookshelf, 2010.
    - Practical parsing patterns
    - Parser generators (ANTLR)
    - Tree construction techniques

## Code Generation

11. **Steven Muchnick**, *Advanced Compiler Design and Implementation*, Morgan Kaufmann, 1997.
    - Code generation techniques
    - Register allocation
    - Instruction selection

12. **Keith D. Cooper, Timothy J. Harvey**, *Engineering a Compiler*, Morgan Kaufmann, 2003.
    - Code generation algorithms
    - Optimization techniques
    - Runtime systems

## FRISC Architecture

13. **FRISC Processor Documentation**: Available from course materials
    - Instruction set reference
    - Addressing modes
    - Assembly language syntax

14. **FRISCjs Simulator**: [GitHub Repository](https://github.com/izuzak/FRISCjs)
    - JavaScript implementation of FRISC simulator
    - Web and console interfaces
    - Used for testing generated code

## Online Resources

### Compiler Construction

15. **LLVM Documentation**: [llvm.org/docs](https://llvm.org/docs/)
    - Modern compiler infrastructure
    - Code generation techniques
    - Optimization passes

16. **GCC Internals**: [gcc.gnu.org/onlinedocs](https://gcc.gnu.org/onlinedocs/)
    - GCC compiler internals
    - Intermediate representations
    - Code generation

### Formal Languages

17. **Regular Expressions**: [regexr.com](https://regexr.com/)
    - Interactive regex testing
    - Pattern reference
    - Examples and tutorials

18. **Automata Theory**: Various online courses and tutorials
    - Coursera: Automata Theory courses
    - MIT OpenCourseWare: Theory of Computation

## Academic Papers

### Lexical Analysis

19. **Thompson, Ken**, "Regular Expression Search Algorithm", Communications of the ACM, 1968.
    - Thompson's construction algorithm
    - ε-NFA construction from regex

20. **Aho, A. V., Corasick, M. J.**, "Efficient String Matching: An Aid to Bibliographic Search", Communications of the ACM, 1975.
    - String matching algorithms
    - Keyword search techniques

### Parsing

21. **Knuth, Donald E.**, "On the Translation of Languages from Left to Right", Information and Control, 1965.
    - LR parsing foundations
    - Canonical LR construction

22. **DeRemer, Frank L.**, "Simple LR(k) Grammars", Communications of the ACM, 1971.
    - SLR parsing
    - Simplified LR construction

### Code Generation

23. **Aho, A. V., Ganapathi, M., Tjiang, S. W. K.**, "Code Generation Using Tree Matching and Dynamic Programming", ACM Transactions on Programming Languages and Systems, 1989.
    - Tree-based code generation
    - Instruction selection algorithms

## Software Tools

### Parser Generators

24. **ANTLR**: [antlr.org](https://www.antlr.org/)
    - Parser generator framework
    - Grammar-based parser construction
    - Tree construction support

25. **Yacc/Bison**: [gnu.org/software/bison](https://www.gnu.org/software/bison/)
    - LALR parser generator
    - Classic parser generation tool
    - Grammar specification language

### Lexer Generators

26. **Flex**: [github.com/westes/flex](https://github.com/westes/flex)
    - Fast lexical analyzer generator
    - Lex-compatible
    - DFA-based tokenization

### Compiler Frameworks

27. **LLVM**: [llvm.org](https://llvm.org/)
    - Compiler infrastructure
    - Intermediate representation (LLVM IR)
    - Code generation backends

28. **GCC**: [gcc.gnu.org](https://gcc.gnu.org/)
    - GNU Compiler Collection
    - Multiple language frontends
    - Multiple target backends

## Further Reading Recommendations

### For Beginners

- Start with **Aho et al. (Dragon Book)** for comprehensive coverage
- Use **Appel's Modern Compiler Implementation** for Java-based examples
- Reference **Srbljić's textbooks** for course-specific material

### For Advanced Topics

- **Muchnick's Advanced Compiler Design** for code generation
- **Cooper & Torczon** for optimization techniques
- **LLVM Documentation** for modern compiler infrastructure

### For FRISC-Specific Topics

- **Basch & Kovač's FRISC book** for architecture details
- **FRISCjs repository** for simulator implementation
- Course materials for FRISC-specific conventions

## Contributing to Documentation

If you find additional resources that should be included:

1. Verify resource quality and relevance
2. Categorize appropriately
3. Provide complete citation information
4. Include brief description of content

---

*This bibliography provides a foundation for understanding compiler construction theory and practice. The listed resources complement the PPJ compiler documentation and implementation.*
