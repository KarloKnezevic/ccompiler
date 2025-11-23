int main() {
    int i;
    int j;
    int k;
    int count;
    count = 0;
    for (i = 0; i < 3; i = i + 1) {
        for (j = 0; j < 3; j = j + 1) {
            for (k = 0; k < 3; k = k + 1) {
                count = count + 1;
            }
        }
    }
    return count;
}

