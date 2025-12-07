float main(void) {
    float arr[3];
    int idx;
    float result;
    arr[0] = 9.0;
    arr[1] = 3.0;
    arr[2] = 2.0;
    idx = 0;
    result = arr[idx] / arr[++idx] * arr[++idx];
    return result;
}

