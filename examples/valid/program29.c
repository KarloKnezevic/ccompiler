int max(int a, int b) {
    if (a > b) {
        return a;
    } else {
        return b;
    }
}

int main() {
    int x;
    int y;
    int m;
    x = 10;
    y = 20;
    m = max(x, y);
    return m;
}

