// EXPECT OK
// Pointer as condition in while loop

int main(void) {
    int x = 5;
    int *p = &x;
    while (p) {
        p = 0;
    }
    return 0;
}

