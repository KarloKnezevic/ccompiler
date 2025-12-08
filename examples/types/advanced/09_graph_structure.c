// EXPECT OK
// Graph structure with adjacency list

struct GraphNode {
    int id;
    struct GraphNode *neighbors[5];
    int neighbor_count;
};

void add_neighbor(struct GraphNode *node, struct GraphNode *neighbor) {
    if ((*node).neighbor_count < 5) {
        (*node).neighbors[(*node).neighbor_count] = neighbor;
        (*node).neighbor_count = (*node).neighbor_count + 1;
    }
}

int main(void) {
    struct GraphNode g1;
    struct GraphNode g2;
    g1.id = 1;
    g2.id = 2;
    g1.neighbor_count = 0;
    g2.neighbor_count = 0;
    add_neighbor(&g1, &g2);
    return 0;
}

