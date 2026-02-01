// EXPECT SEMANTIC ERROR
// Return type mismatch

int f(void) {
    struct S {
        int x;
    };
    struct S s;
    return s;  // Error: cannot return struct from int function
}

int main(void) {
    return 0;
}

