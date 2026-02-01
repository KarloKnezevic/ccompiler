// EXPECT OK
// State machine with state transitions and counting

struct State {
    int id;
    int value;
    struct State *next;
};

struct StateMachine {
    struct State *current;
    int step_count;
    int total_value;
};

int calculate_total(struct StateMachine *sm) {
    struct State *curr;
    int total;
    total = 0;
    curr = (*sm).current;
    total = total + (*curr).value;
    curr = (*curr).next;
    if (curr != 0) {
        total = total + (*curr).value;
    }
    return total;
}

int main(void) {
    struct StateMachine sm;
    struct State s1;
    struct State s2;
    int result;
    sm.current = 0;
    sm.step_count = 0;
    sm.total_value = 0;
    s1.id = 1;
    s1.value = 5;
    s2.id = 2;
    s2.value = 10;
    s1.next = &s2;
    s2.next = 0;
    sm.current = &s1;
    result = calculate_total(&sm);
    return result;
}
