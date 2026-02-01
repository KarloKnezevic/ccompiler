// EXPECT OK
// Tree with parent pointers and depth calculation

struct PTree {
    int value;
    struct PTree *parent;
    struct PTree *left;
    struct PTree *right;
};

int find_depth(struct PTree *node) {
    int depth = 0;
    while (node && (*node).parent) {
        depth = depth + 1;
        node = (*node).parent;
    }
    return depth;
}

int main(void) {
    struct PTree root;
    struct PTree child;
    int depth;
    root.value = 10;
    root.parent = 0;
    child.value = 5;
    child.parent = &root;
    root.left = &child;
    root.right = 0;
    child.left = 0;
    child.right = 0;
    depth = find_depth(&child);
    return depth;
}
