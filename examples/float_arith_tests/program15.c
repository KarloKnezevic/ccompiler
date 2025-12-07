float div_conditional(float a, float b, float c) {
    if (a > b) {
        return a / c;
    } else {
        return b / c;
    }
}

float main(void) {
    float x = 12.0;
    float y = 8.0;
    float z = 4.0;
    float result = div_conditional(x, y, z);
    return result;
}

