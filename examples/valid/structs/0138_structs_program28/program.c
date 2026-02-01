// EXPECT OK
// Struct field passed to function

struct Point {
    int x;
    int y;
};

int add(int a, int b) {
    return a + b;
}

int main(void) {
    struct Point p;
    p.x = 15;
    p.y = 25;
    return add(p.x, p.y);
}
