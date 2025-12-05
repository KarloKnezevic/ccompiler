// EXPECT SEMANTIC ERROR
// Dereferencing non-pointer

int main(void) {
    int x = 5;
    int y = *x;  // Error: x is not a pointer
    return 0;
}

