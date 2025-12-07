float main(void) {
    float a = 7.0;
    float b = 3.0;
    float c = 2.0;
    float result;
    if (a > b) {
        result = a / c + b * c;
    } else {
        result = b / c + a * c;
    }
    return result;
}

