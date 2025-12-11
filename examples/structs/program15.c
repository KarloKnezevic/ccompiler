// EXPECT OK
// Function returning struct (by value)

struct Point {
    int x;
    int y;
};

struct Point makePoint(int x, int y) {
    struct Point p;
    p.x = x;
    p.y = y;
    return p;
}

int main(void) {
    struct Point p;
    p = makePoint(7, 8);
    return p.x + p.y;
}
