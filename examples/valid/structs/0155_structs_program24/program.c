// EXPECT OK
// Access field of struct element in array

struct Point {
    int x;
    int y;
};

int main(void) {
    struct Point arr[3];
    arr[0].x = 1;
    arr[0].y = 2;
    arr[1].x = 3;
    arr[1].y = 4;
    arr[2].x = 5;
    arr[2].y = 6;
    return arr[0].x + arr[1].y + arr[2].x;
}
