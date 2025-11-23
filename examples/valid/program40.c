int main() {
    int i;
    int j;
    for (i = 0; i < 5; i = i + 1) {
        for (j = 0; j < 5; j = j + 1) {
            if (i == j) {
                break;
            }
        }
    }
    return i;
}

