int is_even(int n) {
    return n % 2 == 0;
}

int main() {
    int x;
    x = 6;
    if (is_even(x)) {
        return 1;
    }
    return 0;
}

