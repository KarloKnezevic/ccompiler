// EXPECT OK
// Nested struct: Outer contains Inner

struct Inner {
    int value;
};

struct Outer {
    struct Inner inner;
    int y;
};

int main(void) {
    struct Outer o;
    o.inner.value = 42;
    o.y = 10;
    return o.inner.value + o.y;
}
