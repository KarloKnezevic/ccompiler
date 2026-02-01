// EXPECT OK
// Composite pattern with tree structure and sum calculation

struct Component {
    int value;
    struct Component *children[5];
    int child_count;
};

int sum_component(struct Component *c) {
    int total = (*c).value;
    int i;
    for (i = 0; i < (*c).child_count; i = i + 1) {
        if ((*c).children[i]) {
            total = total + sum_component((*c).children[i]);
        }
    }
    return total;
}

int main(void) {
    struct Component comp;
    int result;
    comp.value = 10;
    comp.child_count = 0;
    result = sum_component(&comp);
    return result;
}
