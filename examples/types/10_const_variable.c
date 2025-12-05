// EXPECT SEMANTIC ERROR
// Assigning to const variable

int main(void) {
    const int x = 5;
    x = 6;  // Error: cannot assign to const
    return 0;
}

