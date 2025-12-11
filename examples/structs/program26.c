// EXPECT OK
// Struct with char array field

struct Buffer {
    char data[4];
    int size;
};

int main(void) {
    struct Buffer buf;
    buf.data[0] = 'A';
    buf.data[1] = 'B';
    buf.data[2] = 'C';
    buf.data[3] = 'D';
    buf.size = 4;
    return buf.size;
}
