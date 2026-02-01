// EXPECT OK
// Struct passed as parameter by value

struct Point {
    int x;
    int y;
};

int sum(struct Point p) {
    return p.x + p.y;
}

int main(void) {
    struct Point p;
    p.x = 15;
    p.y = 25;
    return sum(p);
}
