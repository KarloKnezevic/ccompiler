int main(void) {
    int buffer[4];
    int *first;
    int *second;
    int *result;
    first = buffer;
    second = buffer + 2;
    result = second - first;
    return *result;
}


