// EXPECT OK
// Red-black tree structure with color checking

struct RBNode {
    int key;
    int color;
    struct RBNode *parent;
    struct RBNode *left;
    struct RBNode *right;
};

int is_red(struct RBNode *node) {
    if (!node) {
        return 0;
    }
    return (*node).color == 1;
}

int count_red_nodes(struct RBNode *root) {
    int count;
    if (!root) {
        return 0;
    }
    count = 0;
    if (is_red(root)) {
        count = 1;
    }
    count = count + count_red_nodes((*root).left);
    count = count + count_red_nodes((*root).right);
    return count;
}

int main(void) {
    struct RBNode root;
    int result;
    root.key = 10;
    root.color = 1;
    root.parent = 0;
    root.left = 0;
    root.right = 0;
    result = count_red_nodes(&root);
    return result;
}
