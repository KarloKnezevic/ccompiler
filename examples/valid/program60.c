int find_max(int arr[], int n) {
    int i;
    int max_val;
    max_val = arr[0];
    for (i = 1; i < n; i = i + 1) {
        if (arr[i] > max_val) {
            max_val = arr[i];
        }
    }
    return max_val;
}

int main() {
    int arr[5];
    arr[0] = 3;
    arr[1] = 7;
    arr[2] = 2;
    arr[3] = 9;
    arr[4] = 1;
    return find_max(arr, 5);
}

