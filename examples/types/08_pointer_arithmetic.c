// EXPECT OK
// Pointer arithmetic

int main(void) {
    int arr[10];
    int *p = arr;
    int *q;
    p = p + 3;
    q = p - 1;
    return 0;
}

