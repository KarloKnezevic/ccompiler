struct Rectangle {
    int width;
    int height;
};

int area(struct Rectangle r) {
    return r.width * r.height;
}

int main(void) {
    struct Rectangle rect;
    rect.width = 5;
    rect.height = 10;
    return area(rect);
}

