struct Node {
    int value;
    int next;
};

int main() {
    struct Node node;
    node.value = 42;
    node.next = 0;
    return node.value;
}

