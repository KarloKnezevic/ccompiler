// EXPECT OK
// Nested pointer structures with calculations

struct Inner {
    int data;
    float value;
};

struct Outer {
    struct Inner *inner;
    float *values;
};

int calculate_sum(struct Outer *o) {
    int result;
    result = (*(*o).inner).data + (int)(*(*o).values);
    return result;
}

int main(void) {
    int x = 42;
    float f = 3.14;
    struct Inner i;
    struct Outer o;
    int result;
    i.data = 10;
    i.value = 2.5;
    o.inner = &i;
    o.values = &f;
    result = calculate_sum(&o);
    return result;
}
