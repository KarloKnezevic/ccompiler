// EXPECT OK
// AVL tree-like structure with balance calculation

struct AVLNode {
    int key;
    int height;
    struct AVLNode *left;
    struct AVLNode *right;
};

int get_height(struct AVLNode *node) {
    if (!node) {
        return 0;
    }
    return (*node).height;
}

int get_balance(struct AVLNode *node) {
    if (!node) {
        return 0;
    }
    return get_height((*node).left) - get_height((*node).right);
}

int main(void) {
    struct AVLNode node;
    int balance;
    node.key = 10;
    node.height = 1;
    node.left = 0;
    node.right = 0;
    balance = get_balance(&node);
    return balance;
}
