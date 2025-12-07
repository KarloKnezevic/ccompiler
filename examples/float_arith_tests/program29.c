float main(void) {
    float val = 20.0;
    int counter = 0;
    float sum = 0.0;
    while (counter < 4) {
        sum = sum + val / (counter + 1);
        counter++;
    }
    return sum;
}

