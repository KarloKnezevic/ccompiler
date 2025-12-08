// EXPECT OK
// Variable shadowing and nested scopes

int main(void) {
    int x = 1;
    {
        int x = 2;  // Shadows outer x
        {
            int x = 3;  // Shadows middle x
        }
    }
    return 0;
}

