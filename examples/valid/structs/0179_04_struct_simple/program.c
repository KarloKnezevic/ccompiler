// EXPECT OK
// Simple struct definition and field access

struct Point {
    int x;
    int y;
};

int main(void) {
    struct Point p;
    int sum;
    p.x = 10;
    p.y = 20;
    sum = p.x + p.y;
    return 0;
}

