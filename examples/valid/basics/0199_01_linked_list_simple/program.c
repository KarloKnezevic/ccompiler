// EXPECT OK
// Simple singly linked list

struct Node {
    int data;
    struct Node *next;
};

int main(void) {
    struct Node n1;
    struct Node n2;
    n1.data = 10;
    n2.data = 20;
    n1.next = &n2;
    n2.next = 0;
    return 0;
}

