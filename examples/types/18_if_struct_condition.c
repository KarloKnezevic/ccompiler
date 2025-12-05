// EXPECT SEMANTIC ERROR
// Struct as condition in if statement

int main(void) {
    struct S {
        int x;
    };
    struct S s;
    if (s) {  // Error: struct is not scalar
        return 1;
    }
    return 0;
}

