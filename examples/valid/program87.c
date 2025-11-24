int main(void) {
    int data[2];
    int *first;
    int *second;
    data[0] = 5;
    data[1] = 7;
    first = data;
    second = data + 1;
    if (first < second) {
        return *second;
    }
    return *first;
}


