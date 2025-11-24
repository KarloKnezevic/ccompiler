int difference(int *end, int *start) {
    return end - start;
}

int main(void) {
    int array[4];
    int *begin;
    int *finish;
    begin = array;
    finish = array + 4;
    return difference(finish, begin);
}


