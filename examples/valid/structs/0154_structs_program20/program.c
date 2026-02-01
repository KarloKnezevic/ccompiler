// EXPECT OK
// Mix of global and local struct variables

struct Point {
    int x;
    int y;
};

struct Point global;

int main(void) {
    struct Point local;
    global.x = 10;
    global.y = 20;
    local.x = 30;
    local.y = 40;
    return global.x + global.y + local.x + local.y;
}
