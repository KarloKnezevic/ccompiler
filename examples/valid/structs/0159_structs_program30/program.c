// EXPECT OK
// Comprehensive test: nested structs, arrays, assignments, function calls

struct Inner {
    int arr[2];
    int value;
};

struct Outer {
    struct Inner inner;
    int base;
};

int compute(struct Outer o) {
    return o.inner.arr[0] + o.inner.arr[1] + o.inner.value + o.base;
}

int main(void) {
    struct Outer o1;
    struct Outer o2;
    o1.inner.arr[0] = 1;
    o1.inner.arr[1] = 2;
    o1.inner.value = 3;
    o1.base = 4;
    o2 = o1;  // Copy struct
    return compute(o2);
}
