// EXPECT OK
// Struct with encapsulated data and methods via calculations

struct Counter {
    int count;
    int step;
};

void increment_counter(struct Counter *c) {
    (*c).count = (*c).count + (*c).step;
}

int get_count(struct Counter *c) {
    return (*c).count;
}

int main(void) {
    struct Counter c;
    int result;
    c.count = 0;
    c.step = 5;
    increment_counter(&c);
    increment_counter(&c);
    result = get_count(&c);
    return result;
}
