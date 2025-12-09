// EXPECT OK
// Struct with array field

struct A {
    int arr[5];
};

int main(void) {
    struct A a;
    a.arr[0] = 1;
    a.arr[1] = 2;
    a.arr[2] = 3;
    a.arr[3] = 4;
    a.arr[4] = 5;
    return a.arr[0] + a.arr[1] + a.arr[2] + a.arr[3] + a.arr[4];
}
