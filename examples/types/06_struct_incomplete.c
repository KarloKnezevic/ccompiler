// EXPECT SEMANTIC ERROR
// Using struct tag before definition

int main(void) {
    struct S s;  // Error: struct S not yet defined
    return 0;
}

struct S {
    int x;
};

