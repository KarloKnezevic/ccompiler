// EXPECT OK
// Multiple global struct variables

struct Point {
    int x;
    int y;
};

struct Point p1;
struct Point p2;

int main(void) {
    p1.x = 1;
    p1.y = 2;
    p2.x = 3;
    p2.y = 4;
    return p1.x + p1.y + p2.x + p2.y;
}
