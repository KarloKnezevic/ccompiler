// EXPECT OK
// Casts between pointers and integers

int main(void) {
    int x = 5;
    int *p = &x;
    int addr = (int)p;
    int *q = (int *)addr;
    return 0;
}

