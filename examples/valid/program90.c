int main(void) {
    int buffer[5];
    int *ptr;
    int result;
    buffer[0] = 1;
    buffer[1] = 3;
    buffer[2] = 5;
    buffer[3] = 7;
    buffer[4] = 9;
    ptr = buffer;
    result = 0;
    while (ptr < buffer + 5) {
        result = result + *ptr;
        ptr = ptr + 1;
    }
    return result;
}


