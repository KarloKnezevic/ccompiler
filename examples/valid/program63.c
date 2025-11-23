int sum_recursive(int arr[], int n) {
    if (n <= 0) {
        return 0;
    }
    return arr[n - 1] + sum_recursive(arr, n - 1);
}

int main(void) {
    int arr[4];
    arr[0] = 1;
    arr[1] = 2;
    arr[2] = 3;
    arr[3] = 4;
    return sum_recursive(arr, 4);
}

