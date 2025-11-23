int nested_if(int x, int y) {
    if (x > 0) {
        if (y > 0) {
            if (x < y) {
                return 1;
            }
        }
    }
    return 0;
}

int main() {
    return nested_if(5, 10);
}

