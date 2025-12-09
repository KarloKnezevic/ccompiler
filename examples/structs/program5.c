// EXPECT OK
// Global struct variable

struct Point {
    int x;
    int y;
};

struct Point p;

int main(void) {
    p.x = 5;
    p.y = 7;
    return p.x * p.y;
}
