// EXPECT OK
// Simple local struct with scalar fields

struct Point {
    int x;
    int y;
};

int main(void) {
    struct Point p;
    p.x = 10;
    p.y = 20;
    return p.x + p.y;
}
