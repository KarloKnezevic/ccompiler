int binary_search_recursive(int arr[], int left, int right, int key) {
    int mid;
    if (left > right) {
        return -1;
    }
    mid = (left + right) / 2;
    if (arr[mid] == key) {
        return mid;
    }
    if (arr[mid] > key) {
        return binary_search_recursive(arr, left, mid - 1, key);
    }
    return binary_search_recursive(arr, mid + 1, right, key);
}

int main() {
    int arr[5];
    arr[0] = 1;
    arr[1] = 3;
    arr[2] = 5;
    arr[3] = 7;
    arr[4] = 9;
    return binary_search_recursive(arr, 0, 4, 5);
}

