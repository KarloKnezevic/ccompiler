int reverse_number(int n) {
    int reversed;
    reversed = 0;
    while (n != 0) {
        reversed = reversed * 10 + n % 10;
        n = n / 10;
    }
    return reversed;
}

int main() {
    return reverse_number(123);
}

