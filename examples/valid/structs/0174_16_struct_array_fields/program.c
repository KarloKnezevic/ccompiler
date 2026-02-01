// EXPECT OK
// Struct with array and pointer fields

struct Data {
    int arr[10];
    int *ptr;
};

int main(void) {
    struct Data d;
    int x = 5;
    int y;
    d.arr[0] = 1;
    d.arr[1] = 2;
    d.ptr = &x;
    y = *d.ptr;
    return 0;
}

