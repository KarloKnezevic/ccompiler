// EXPECT OK
// Linked list traversal

struct Node {
    int value;
    struct Node *next;
};

int sum_list(struct Node *head) {
    int total = 0;
    struct Node *current = head;
    while (current) {
        total = total + (*current).value;
        current = (*current).next;
    }
    return total;
}

int main(void) {
    struct Node n1;
    struct Node n2;
    int result;
    n1.value = 5;
    n2.value = 10;
    n1.next = &n2;
    n2.next = 0;
    result = sum_list(&n1);
    return 0;
}

