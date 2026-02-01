// EXPECT OK
// Trie (prefix tree) structure

struct TrieNode {
    int is_end;
    struct TrieNode *children[26];
};

int main(void) {
    struct TrieNode root;
    int i;
    root.is_end = 0;
    for (i = 0; i < 26; i = i + 1) {
        root.children[i] = 0;
    }
    return 0;
}
