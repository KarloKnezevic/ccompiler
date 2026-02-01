int read_value(int *ptr) {
    return *ptr;
}

int main(void) {
    int number;
    number = 6;
    return read_value(number);
}


