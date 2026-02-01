// EXPECT SEMANTIC ERROR
// Return with value in void function

void f(void) {
    return 1;  // Error: void function cannot return value
}

int main(void) {
    return 0;
}

