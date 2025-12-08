// EXPECT OK
// Const pointer variants

int main(void) {
    int x = 5;
    int y = 10;
    
    int * const p1 = &x;  // const pointer
    const int *p2 = &x;   // pointer to const
    const int * const p3 = &x;  // const pointer to const
    
    *p1 = 6;  // OK: can modify through const pointer
    // p1 = &y;  // Would be error: cannot reassign const pointer
    // *p2 = 7;  // Would be error: cannot modify through pointer to const
    p2 = &y;  // OK: can reassign pointer to const
    
    return 0;
}

