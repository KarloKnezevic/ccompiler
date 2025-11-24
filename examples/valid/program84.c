int main(void) {
    int values[3];
    int *ptr;
    values[0] = 1;
    values[1] = 2;
    values[2] = 3;
    ptr = values;
    ptr = ptr + 2;
    return *ptr;
}


