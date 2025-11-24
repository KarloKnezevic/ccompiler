int main(void) {
    int value;
    int *p1;
    int **p2;
    value = 2;
    p1 = &value;
    p2 = &p1;
    **p2 = **p2 + 8;
    return value;
}


