// EXPECT OK
// Struct field used in complex expression

struct Point {
    int x;
    int y;
};

int main(void) {
    struct Point p;
    p.x = 5;
    p.y = 3;
    return p.x * p.y + p.x - p.y;
}
