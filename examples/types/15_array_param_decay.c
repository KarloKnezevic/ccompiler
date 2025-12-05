// EXPECT OK
// Array parameter decay to pointer

void f(int a[]) {
    a[0] = 1;
}

int main(void) {
    int arr[5];
    f(arr);
    return 0;
}

