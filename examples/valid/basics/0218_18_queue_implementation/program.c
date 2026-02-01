// EXPECT OK
// Queue implementation

struct QueueNode {
    int data;
    struct QueueNode *next;
};

struct Queue {
    struct QueueNode *front;
    struct QueueNode *rear;
};

void enqueue(struct Queue *q, int value) {
    struct QueueNode *node;
    (*node).data = value;
    (*node).next = 0;
    if (!(*q).rear) {
        (*q).front = node;
        (*q).rear = node;
    } else {
        (*(*q).rear).next = node;
        (*q).rear = node;
    }
}

int main(void) {
    struct Queue q;
    q.front = 0;
    q.rear = 0;
    enqueue(&q, 5);
    return 0;
}

