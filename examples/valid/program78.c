int main(void) {
    int i;
    for (i = 0; i < 10; i = i + 1) {
        if (i == 3) {
            continue;
        }
        if (i == 7) {
            break;
        }
    }
    return i;
}

