// EXPECT OK
// Struct field modified in loop

struct Counter {
    int value;
};

int main(void) {
    struct Counter c;
    int i;
    c.value = 0;
    i = 0;
    while (i < 5) {
        c.value = c.value + i;
        i = i + 1;
    }
    return c.value;
}
