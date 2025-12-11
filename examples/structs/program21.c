// EXPECT OK
// Struct field used in conditional

struct Point {
    int x;
    int y;
};

int main(void) {
    struct Point p;
    p.x = 5;
    p.y = 10;
    if (p.x < p.y) {
        return p.y - p.x;
    }
    return 0;
}
