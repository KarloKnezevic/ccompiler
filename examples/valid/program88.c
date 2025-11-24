int sum(int *start, int *end) {
    int total;
    total = 0;
    while (start < end) {
        total = total + *start;
        start = start + 1;
    }
    return total;
}

int main(void) {
    int numbers[3];
    numbers[0] = 2;
    numbers[1] = 4;
    numbers[2] = 6;
    return sum(numbers, numbers + 3);
}


