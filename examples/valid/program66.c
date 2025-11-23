int count_digits(int n) {
    int count;
    count = 0;
    while (n != 0) {
        count = count + 1;
        n = n / 10;
    }
    return count;
}

int main() {
    return count_digits(12345);
}

