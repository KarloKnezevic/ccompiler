// EXPECT OK
// Strategy-like structure with different calculation modes

struct Context {
    int mode;
    int data;
};

int calculate(struct Context *ctx, int x, int y) {
    if ((*ctx).mode == 0) {
        return x + y;
    } else {
        return x * y;
    }
}

int main(void) {
    struct Context ctx;
    int result;
    ctx.mode = 0;
    ctx.data = 0;
    result = calculate(&ctx, 5, 3);
    return result;
}
