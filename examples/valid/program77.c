int main(void) {
    int i;
    i = 0;
    while (i < 10) {
        i = i + 1;
        if (i % 2 == 0) {
            continue;
        }
        if (i > 5) {
            break;
        }
    }
    return i;
}

