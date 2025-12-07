float compute(float x, int n) {
    return x / n + x * n;
}

float main(void) {
    float val = 4.0;
    int num = 2;
    float result = compute(val, num);
    return result;
}

