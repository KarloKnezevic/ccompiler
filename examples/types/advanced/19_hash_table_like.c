// EXPECT OK
// Hash table-like structure

struct HashEntry {
    int key;
    int value;
    struct HashEntry *next;
};

struct HashTable {
    struct HashEntry *buckets[10];
};

void insert(struct HashTable *ht, int key, int value) {
    int index = key % 10;
    struct HashEntry *entry;
    (*entry).key = key;
    (*entry).value = value;
    (*entry).next = (*ht).buckets[index];
    (*ht).buckets[index] = entry;
}

int main(void) {
    struct HashTable ht;
    int i;
    for (i = 0; i < 10; i = i + 1) {
        ht.buckets[i] = 0;
    }
    insert(&ht, 42, 100);
    return 0;
}

