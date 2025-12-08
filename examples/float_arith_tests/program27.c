float helper(float a, float b, float c) {
    return (a * b) / (c + 1.0);
}

float main(void) {
    float x = 6.0;
    float y = 2.0;
    float z = 2.0;
    float result = helper(x, y, z);
    return result;
}

