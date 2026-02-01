// EXPECT OK
// Complex expression with struct fields

struct Math {
    int a;
    int b;
    int c;
};

int main(void) {
    struct Math m;
    m.a = 2;
    m.b = 3;
    m.c = 4;
    return m.a * m.b + m.c * m.a - m.b;
}
