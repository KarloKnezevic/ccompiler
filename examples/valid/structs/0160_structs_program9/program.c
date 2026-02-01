// EXPECT OK
// Nested struct with array inside inner struct

struct Inner {
    int arr[3];
};

struct Outer {
    struct Inner inner;
    int base;
};

int main(void) {
    struct Outer o;
    o.inner.arr[0] = 1;
    o.inner.arr[1] = 2;
    o.inner.arr[2] = 3;
    o.base = 10;
    return o.inner.arr[0] + o.inner.arr[1] + o.inner.arr[2] + o.base;
}
