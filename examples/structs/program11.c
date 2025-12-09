// EXPECT OK
// Struct with pointer field (pointer not dereferenced)

struct Node {
    int value;
    int *next;  // Pointer field (not dereferenced)
};

int main(void) {
    struct Node n;
    n.value = 42;
    return n.value;
}
