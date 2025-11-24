int main(void) {
    int value;
    int *pointer;
    value = 5;
    pointer = &value;
    *pointer = *pointer + 2;
    return value;
}


