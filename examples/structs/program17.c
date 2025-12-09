// EXPECT OK
// Complex nested struct with arrays

struct Inner {
    int arr[2];
    int value;
};

struct Middle {
    struct Inner inner;
    int arr[3];
};

struct Outer {
    struct Middle middle;
    int base;
};

int main(void) {
    struct Outer o;
    o.middle.inner.arr[0] = 1;
    o.middle.inner.arr[1] = 2;
    o.middle.inner.value = 3;
    o.middle.arr[0] = 4;
    o.middle.arr[1] = 5;
    o.middle.arr[2] = 6;
    o.base = 7;
    return o.middle.inner.arr[0] + o.middle.inner.value + o.middle.arr[2] + o.base;
}
