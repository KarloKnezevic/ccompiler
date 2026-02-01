// EXPECT OK
// Mixed pointer and struct usage

struct S {
    int *p;
};

int main(void) {
    struct S s;
    int x = 5;
    s.p = &x;
    *s.p = 10;
    return 0;
}

