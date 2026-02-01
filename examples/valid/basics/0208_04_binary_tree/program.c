// EXPECT OK
// Binary tree structure with calculation

struct TreeNode {
    int value;
    struct TreeNode *left;
    struct TreeNode *right;
};

int tree_sum(struct TreeNode *root) {
    if (!root) {
        return 0;
    }
    return (*root).value + tree_sum((*root).left) + tree_sum((*root).right);
}

int main(void) {
    struct TreeNode root;
    struct TreeNode left;
    struct TreeNode right;
    int sum;
    root.value = 10;
    left.value = 5;
    right.value = 15;
    root.left = &left;
    root.right = &right;
    left.left = 0;
    left.right = 0;
    right.left = 0;
    right.right = 0;
    sum = tree_sum(&root);
    return sum;
}
