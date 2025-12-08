// EXPECT OK
// Struct tag namespace separate from variables

struct S {
    int x;
};

int main(void) {
    int S = 5;  // OK: variable name can be same as struct tag
    struct S s;
    s.x = S;
    return 0;
}

