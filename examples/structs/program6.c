// EXPECT OK
// Multiple local struct instances

struct Point {
    int x;
    int y;
};

int main(void) {
    struct Point p;
    struct Point q;
    p.x = 10;
    p.y = 20;
    q.x = 30;
    q.y = 40;
    return p.x + p.y + q.x + q.y;
}
