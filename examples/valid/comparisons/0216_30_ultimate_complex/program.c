// EXPECT OK
// Ultimate complexity: multiple data structures with calculations

struct Node {
    int id;
    float weight;
    struct Node *connections[5];
    int conn_count;
};

struct Graph {
    struct Node *nodes[20];
    int node_count;
};

int calculate_total_weight(struct Graph *g) {
    float total = 0.0;
    int i;
    for (i = 0; i < (*g).node_count; i = i + 1) {
        if ((*g).nodes[i]) {
            total = total + (*(*g).nodes[i]).weight;
        }
    }
    return (int)total;
}

int main(void) {
    struct Graph g;
    struct Node n1;
    struct Node n2;
    int i;
    int result;
    
    g.node_count = 2;
    n1.id = 1;
    n1.weight = 1.5;
    n1.conn_count = 1;
    n2.id = 2;
    n2.weight = 2.0;
    n2.conn_count = 0;
    
    for (i = 0; i < 5; i = i + 1) {
        n1.connections[i] = 0;
        n2.connections[i] = 0;
    }
    
    n1.connections[0] = &n2;
    g.nodes[0] = &n1;
    g.nodes[1] = &n2;
    
    result = calculate_total_weight(&g);
    return result;
}
