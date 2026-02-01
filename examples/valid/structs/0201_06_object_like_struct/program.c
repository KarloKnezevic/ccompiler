// EXPECT OK
// Struct with nested structures and calculations

struct Point {
    int x;
    int y;
};

struct Rectangle {
    struct Point *top_left;
    struct Point *bottom_right;
};

int calculate_area(struct Rectangle *rect) {
    int width;
    int height;
    width = (*(*rect).bottom_right).x - (*(*rect).top_left).x;
    height = (*(*rect).top_left).y - (*(*rect).bottom_right).y;
    return width * height;
}

int main(void) {
    struct Rectangle rect;
    struct Point p1;
    struct Point p2;
    int area;
    p1.x = 0;
    p1.y = 10;
    p2.x = 5;
    p2.y = 0;
    rect.top_left = &p1;
    rect.bottom_right = &p2;
    area = calculate_area(&rect);
    return area;
}
