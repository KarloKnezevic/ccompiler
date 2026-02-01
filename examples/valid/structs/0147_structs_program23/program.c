// EXPECT OK
// Nested struct assignment

struct Inner {
    int value;
};

struct Outer {
    struct Inner inner;
    int y;
};

int main(void) {
    struct Outer o1;
    struct Outer o2;
    o1.inner.value = 42;
    o1.y = 10;
    o2 = o1;  // Copy entire struct
    return o2.inner.value + o2.y;
}
