// EXPECT OK
// Complex example mixing all features

struct Point {
    float x;
    float y;
};

float distance(struct Point *p1, struct Point *p2) {
    float dx = (*p1).x - (*p2).x;
    float dy = (*p1).y - (*p2).y;
    return dx * dx + dy * dy;
}

int main(void) {
    struct Point p1;
    struct Point p2;
    float dist;
    int arr[10];
    int *p = arr;
    int i;
    int count = 10;
    
    p1.x = 1.0;
    p1.y = 2.0;
    p2.x = 3.0;
    p2.y = 4.0;
    
    dist = distance(&p1, &p2);
    
    for (i = 0; i < count; i = i + 1) {
        arr[i] = (int)dist + i;
        if (arr[i] > 100) {
            break;
        }
    }
    
    return 0;
}

