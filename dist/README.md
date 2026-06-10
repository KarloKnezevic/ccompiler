# Prebuilt compiler

`ccompiler.jar` is a ready-to-run, self-contained build of the FRISCcc compiler
(produced by `./build.sh` / `mvn clean package`). You can use it without
building anything.

Run it **from the repository root** so the bundled FRISC simulator
(`node_modules/friscjs`) and the `examples/` tree are on hand:

```bash
# Compile a C program to FRISC and run it on the simulator:
java -jar dist/ccompiler.jar --all --run examples/real_world/math_fibonacci_iter/program.c
#   → Program output: 6765

# Execute the typed IR on the tree-walking interpreter:
java -jar dist/ccompiler.jar run-ir examples/real_world/math_fibonacci_iter/program.ir

# Lower the IR to bytecode and run it on the virtual machine:
java -jar dist/ccompiler.jar run-vm examples/real_world/math_fibonacci_iter/program.ir

# Full flag reference:
java -jar dist/ccompiler.jar --help
```

Requires a JDK 21+ on `PATH` (and Node.js 18+ for the `--run` simulation step).
See the top-level [`README.md`](../README.md) for the full guide.

This JAR is also attached to each [GitHub release](https://github.com/KarloKnezevic/ccompiler/releases).
