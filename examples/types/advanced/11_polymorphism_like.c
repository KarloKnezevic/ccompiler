// EXPECT OK
// Different struct types with common calculations

struct Shape {
    int type;
    float area;
};

struct Circle {
    struct Shape base;
    float radius;
};

float calculate_circle_area(float radius) {
    return 3.14 * radius * radius;
}

int main(void) {
    struct Circle circle;
    float area;
    circle.base.type = 1;
    circle.radius = 5.0;
    area = calculate_circle_area(circle.radius);
    circle.base.area = area;
    return (int)area;
}
