float complex_expr(float a, float b, float c, float d) {
    return (a + b) / c * (d - a) / b;
}

float main(void) {
    float x = 8.0;
    float y = 4.0;
    float z = 2.0;
    float w = 6.0;
    float result = complex_expr(x, y, z, w);
    return result;
}

