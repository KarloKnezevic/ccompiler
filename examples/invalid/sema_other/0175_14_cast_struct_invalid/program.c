// EXPECT SEMANTIC ERROR
// Cast between incompatible struct types

int main(void) {
    struct S1 {
        int x;
    };
    
    struct S2 {
        int y;
    };
    
    struct S1 s1;
    struct S2 s2 = (struct S2)s1;  // Error: incompatible struct types
    return 0;
}

