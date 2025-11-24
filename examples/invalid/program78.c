int main(void) {
    int array[2];
    int *a;
    int *b;
    a = array;
    b = array + 1;
    a = a + b;
    return *a;
}


