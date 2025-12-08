// EXPECT OK
// Memory pool-like structure with allocation tracking

struct PoolBlock {
    int used;
    char data[64];
    struct PoolBlock *next;
};

struct MemoryPool {
    struct PoolBlock *blocks;
    struct PoolBlock *free_list;
    int total_blocks;
    int used_blocks;
};

int count_used_blocks(struct MemoryPool *pool) {
    struct PoolBlock *block;
    int count = 0;
    block = (*pool).blocks;
    while (block) {
        if ((*block).used) {
            count = count + 1;
        }
        block = (*block).next;
    }
    return count;
}

int main(void) {
    struct MemoryPool pool;
    int result;
    pool.blocks = 0;
    pool.free_list = 0;
    pool.total_blocks = 0;
    pool.used_blocks = 0;
    result = count_used_blocks(&pool);
    return result;
}
