// EXPECT OK
// Skip list-like structure with multiple levels

struct SkipNode {
    int value;
    struct SkipNode *next[3];
    int level;
};

struct SkipList {
    struct SkipNode *head;
    int max_level;
};

int main(void) {
    struct SkipList list;
    struct SkipNode node;
    int i;
    list.head = 0;
    list.max_level = 3;
    node.value = 10;
    node.level = 2;
    for (i = 0; i < 3; i = i + 1) {
        node.next[i] = 0;
    }
    return 0;
}

