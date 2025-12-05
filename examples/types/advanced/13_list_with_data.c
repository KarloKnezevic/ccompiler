// EXPECT OK
// Linked list with complex data structures

struct Data {
    int id;
    float score;
    char *name;
};

struct ListNode {
    struct Data data;
    struct ListNode *next;
};

int main(void) {
    struct ListNode node1;
    struct ListNode node2;
    node1.data.id = 1;
    node1.data.score = 95.5;
    node2.data.id = 2;
    node2.data.score = 87.0;
    node1.next = &node2;
    node2.next = 0;
    return 0;
}

