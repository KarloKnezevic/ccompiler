struct Item {
    int value;
};

int main(void) {
    struct Item item;
    int *pointer;
    item.value = 4;
    pointer = &item;
    return *pointer;
}


