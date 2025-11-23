int sum_array(int arr[], int n) {
    int i;
    int sum;
    sum = 0;
    for (i = 0; i < n; i = i + 1) {
        sum = sum + arr[i];
    }
    return sum;
}

int main() {
    int arr[5];
    arr[0] = 1;
    arr[1] = 2;
    arr[2] = 3;
    arr[3] = 4;
    arr[4] = 5;
    return sum_array(arr, 5);
}

