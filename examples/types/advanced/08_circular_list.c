// EXPECT OK
// Circular linked list

struct CNode {
    int id;
    struct CNode *next;
};

int main(void) {
    struct CNode n1;
    struct CNode n2;
    struct CNode n3;
    n1.id = 1;
    n2.id = 2;
    n3.id = 3;
    n1.next = &n2;
    n2.next = &n3;
    n3.next = &n1;
    return 0;
}

