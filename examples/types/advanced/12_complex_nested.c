// EXPECT OK
// Complex nested structures with calculations

struct Level2 {
    float data_value;
    int multiplier;
};

struct Level1 {
    int matrix_value;
    struct Level2 *level2;
};

int calculate_total(struct Level1 *l1) {
    int total;
    total = (*l1).matrix_value;
    if ((*l1).level2) {
        total = total + (int)(*(*l1).level2).data_value * (*(*l1).level2).multiplier;
    }
    return total;
}

int main(void) {
    struct Level1 l1;
    struct Level2 l2;
    int result;
    l1.matrix_value = 10;
    l2.data_value = 5.5;
    l2.multiplier = 2;
    l1.level2 = &l2;
    result = calculate_total(&l1);
    return result;
}
