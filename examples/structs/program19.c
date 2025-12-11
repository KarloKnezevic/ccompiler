// EXPECT OK
// Struct with multiple fields of same type

struct Triple {
    int a;
    int b;
    int c;
};

int main(void) {
    struct Triple t;
    t.a = 1;
    t.b = 2;
    t.c = 3;
    return t.a * 100 + t.b * 10 + t.c;
}
