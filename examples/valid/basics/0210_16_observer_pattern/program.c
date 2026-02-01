// EXPECT OK
// Observer-like structure with state tracking

struct Observer {
    int id;
    int last_value;
};

struct Subject {
    struct Observer *observers[10];
    int observer_count;
    int state;
};

void notify_observers(struct Subject *subj, int value) {
    int i;
    for (i = 0; i < (*subj).observer_count; i = i + 1) {
        if ((*subj).observers[i]) {
            (*(*subj).observers[i]).last_value = value;
        }
    }
}

int main(void) {
    struct Subject s;
    struct Observer o;
    int result;
    s.observer_count = 1;
    s.state = 0;
    o.id = 1;
    o.last_value = 0;
    s.observers[0] = &o;
    notify_observers(&s, 42);
    result = o.last_value;
    return result;
}
