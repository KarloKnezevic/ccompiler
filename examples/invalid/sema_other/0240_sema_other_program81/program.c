int main(void) {
    int value;
    int **pp;
    value = 10;
    pp = &value;
    return **pp;
}


