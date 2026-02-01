// EXPECT OK
// Struct with single field

struct Single {
    int value;
};

int main(void) {
    struct Single s;
    s.value = 42;
    return s.value;
}
