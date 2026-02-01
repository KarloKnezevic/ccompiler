// EXPECT OK
// Iterator-like structure with array traversal

struct Iterator {
    int *current;
    int *end;
    int step;
};

int sum_range(struct Iterator *it) {
    int sum = 0;
    int *ptr;
    ptr = (*it).current;
    while (ptr != (*it).end) {
        sum = sum + *ptr;
        ptr = ptr + (*it).step;
    }
    return sum;
}

int main(void) {
    int arr[5];
    struct Iterator iter;
    int result;
    int i;
    for (i = 0; i < 5; i = i + 1) {
        arr[i] = i + 1;
    }
    iter.current = &arr[0];
    iter.end = &arr[5];
    iter.step = 1;
    result = sum_range(&iter);
    return result;
}
