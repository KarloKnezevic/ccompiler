// EXPECT OK
// Tree traversal with calculation

struct Tree {
    int data;
    struct Tree *children[3];
};

int count_nodes(struct Tree *node) {
    int count;
    int i;
    if (!node) {
        return 0;
    }
    count = 1;
    for (i = 0; i < 3; i = i + 1) {
        count = count + count_nodes((*node).children[i]);
    }
    return count;
}

int main(void) {
    struct Tree t;
    int nodes;
    t.data = 1;
    t.children[0] = 0;
    t.children[1] = 0;
    t.children[2] = 0;
    nodes = count_nodes(&t);
    return nodes;
}
