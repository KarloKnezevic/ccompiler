// EXPECT OK
// Basic pointer operations

int main(void) {
    int x = 5;
    int *p = &x;
    int y;
    *p = 10;
    y = *p;
    return 0;
}

