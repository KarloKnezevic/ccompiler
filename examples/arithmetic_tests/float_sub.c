// EXPECT: -0.5
// Q16.16: -32768 (-0.5 * 65536 = -32768)

float main(void) {
    float a = 1.5;
    float b = 2.0;
    return a - b;
}

