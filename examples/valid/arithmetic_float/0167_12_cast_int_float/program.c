// EXPECT OK
// Casts between int and float

int main(void) {
    int x = 5;
    float y = 3.14;
    
    float z = (float)x;
    int w = (int)y;
    char c = (char)x;
    float f = (float)c;
    return 0;
}

