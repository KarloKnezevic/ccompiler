// EXPECT OK
// Three levels of nesting

struct Level1 {
    int a;
};

struct Level2 {
    struct Level1 l1;
    int b;
};

struct Level3 {
    struct Level2 l2;
    int c;
};

int main(void) {
    struct Level3 l3;
    l3.l2.l1.a = 1;
    l3.l2.b = 2;
    l3.c = 3;
    return l3.l2.l1.a + l3.l2.b + l3.c;
}
