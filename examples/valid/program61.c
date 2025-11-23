int linear_search(int arr[], int n, int key) {
    int i;
    for (i = 0; i < n; i = i + 1) {
        if (arr[i] == key) {
            return i;
        }
    }
    return -1;
}

int main(void) {
    int arr[5];
    arr[0] = 10;
    arr[1] = 20;
    arr[2] = 30;
    arr[3] = 40;
    arr[4] = 50;
    return linear_search(arr, 5, 30);
}

