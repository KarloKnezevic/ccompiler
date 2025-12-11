// EXPECT OK
// Struct with char field

struct CharStruct {
    char c;
    int x;
};

int main(void) {
    struct CharStruct s;
    s.c = 'A';
    s.x = 65;
    return s.x;
}
