int main(void) {
    int i;
    for (i = 0; ; i = i + 1) {
        if (i > 10) {
            break;
        }
    }
    return i;
}

