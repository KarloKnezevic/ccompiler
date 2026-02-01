// EXPECT OK
// Float promotions with int and char

int main(void) {
    int a = 5;
    char b = 3;
    float c = 2.0;
    
    float d = a + c;  // int + float -> float
    float e = b + c;  // char + float -> float
    float f = a * c;  // int * float -> float
    return 0;
}

