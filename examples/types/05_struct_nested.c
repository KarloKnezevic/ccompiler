// EXPECT OK
// Nested structs with arrays and pointers

struct Inner {
    int value;
};

struct Outer {
    struct Inner inner;
    int arr[5];
    int *ptr;
};

int main(void) {
    struct Outer o;
    int x = 5;
    o.inner.value = 42;
    o.arr[0] = 1;
    o.ptr = &x;
    return 0;
}

