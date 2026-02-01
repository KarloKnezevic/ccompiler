// EXPECT OK
// Doubly linked list

struct DNode {
    int data;
    struct DNode *prev;
    struct DNode *next;
};

int main(void) {
    struct DNode n1;
    struct DNode n2;
    n1.data = 1;
    n2.data = 2;
    n1.prev = 0;
    n1.next = &n2;
    n2.prev = &n1;
    n2.next = 0;
    return 0;
}

