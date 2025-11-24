int main(void) {
    int value;
    int *pointer;
    value = 3;
    pointer = &value;
    pointer = pointer * 2;
    return *pointer;
}


