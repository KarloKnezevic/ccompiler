float main(void) {
    float sum = 0.0;
    int i;
    for (i = 1; i <= 5; i++) {
        float val = 10.0;
        sum = sum + val / i;
    }
    return sum;
}

