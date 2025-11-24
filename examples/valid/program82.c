int add_pointer(int *ptr) {
    return *ptr + 3;
}

int main(void) {
    int number;
    int *p;
    number = 4;
    p = &number;
    return add_pointer(p);
}


