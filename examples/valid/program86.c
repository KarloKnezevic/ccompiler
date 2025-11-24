void increment(int *value) {
    *value = *value + 1;
}

int main(void) {
    int counter;
    counter = 0;
    increment(&counter);
    increment(&counter);
    return counter;
}


