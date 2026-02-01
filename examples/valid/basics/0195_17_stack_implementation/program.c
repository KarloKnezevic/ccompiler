// EXPECT OK
// Stack implementation with linked list

struct StackNode {
    int value;
    struct StackNode *next;
};

struct Stack {
    struct StackNode *top;
    int size;
};

void push(struct Stack *s, int value) {
    struct StackNode *node;
    (*node).value = value;
    (*node).next = (*s).top;
    (*s).top = node;
    (*s).size = (*s).size + 1;
}

int main(void) {
    struct Stack stack;
    stack.top = 0;
    stack.size = 0;
    push(&stack, 10);
    return 0;
}

