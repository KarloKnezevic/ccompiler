// EXPECT OK
// Struct with mixed field types

struct Mixed {
    char c;
    int x;
    int arr[2];
};

int main(void) {
    struct Mixed m;
    m.c = 'X';
    m.x = 100;
    m.arr[0] = 1;
    m.arr[1] = 2;
    return m.x + m.arr[0] + m.arr[1];
}
