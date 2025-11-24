struct Pair {
    int first;
    int second;
};

int main(void) {
    struct Pair pair;
    struct Pair *pointer;
    pair.first = 3;
    pair.second = 9;
    pointer = &pair;
    return pointer->first + pointer->second;
}


