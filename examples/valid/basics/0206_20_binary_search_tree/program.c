// EXPECT OK
// Binary search tree operations with value calculation

struct BSTNode {
    int key;
    struct BSTNode *left;
    struct BSTNode *right;
};

struct BSTNode *search(struct BSTNode *root, int key) {
    if (!root || (*(root)).key == key) {
        return root;
    }
    if (key < (*(root)).key) {
        return search((*(root)).left, key);
    }
    return search((*(root)).right, key);
}

int main(void) {
    struct BSTNode root;
    struct BSTNode *found;
    int result;
    root.key = 10;
    root.left = 0;
    root.right = 0;
    found = search(&root, 10);
    if (found) {
        result = (*found).key;
    } else {
        result = 0;
    }
    return result;
}
