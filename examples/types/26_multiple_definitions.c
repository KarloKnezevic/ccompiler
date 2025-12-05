// EXPECT SEMANTIC ERROR
// Duplicate variable definition

int main(void) {
    int x = 1;
    int x = 2;  // Error: duplicate definition
    return 0;
}

