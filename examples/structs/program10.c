// EXPECT OK
// Struct assignment (byte-wise copy)

struct Point {
    int x;
    int y;
};

int main(void) {
    struct Point p;
    struct Point q;
    p.x = 10;
    p.y = 20;
    q = p;  // Copy struct
    return q.x + q.y;
}
