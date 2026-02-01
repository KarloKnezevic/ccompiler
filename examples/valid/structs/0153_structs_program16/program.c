// EXPECT OK
// Anonymous struct

int main(void) {
    struct {
        int x;
        int y;
    } p;
    p.x = 11;
    p.y = 22;
    return p.x + p.y;
}
