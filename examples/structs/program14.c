// EXPECT OK
// Struct with large array field

struct Large {
    int arr[10];
};

int main(void) {
    struct Large l;
    int i;
    int sum;
    sum = 0;
    i = 0;
    while (i < 10) {
        l.arr[i] = i + 1;
        sum = sum + l.arr[i];
        i = i + 1;
    }
    return sum;
}
