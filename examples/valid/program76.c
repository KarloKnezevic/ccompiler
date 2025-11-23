int main() {
    int i;
    int sum;
    sum = 0;
    i = 0;
    while (i < 10) {
        sum = sum + i;
        i = i + 1;
        if (sum > 20) {
            break;
        }
    }
    return sum;
}

