// EXPECT: 14, FAIL!!!

int grid[64] = {
  0, 0, 0, 0, 0, 1, 0, 0,
  1, 1, 0, 1, 0, 1, 0, 1,
  0, 0, 0, 1, 0, 0, 0, 0,
  0, 1, 1, 1, 1, 1, 0, 1,
  0, 0, 0, 0, 0, 0, 0, 0,
  1, 1, 0, 1, 1, 0, 1, 0,
  0, 0, 0, 0, 1, 0, 0, 0,
  0, 1, 1, 0, 0, 0, 1, 0
};

int dist[64];
int qx[64];
int qy[64];

int main(void) {
  int i;
  int head = 0;
  int tail = 0;

  for (i = 0; i < 64; i++) {
    dist[i] = -1;
  }

  if (grid[0] == 0) {
    dist[0] = 0;
    qx[tail] = 0;
    qy[tail] = 0;
    tail++;
  }

  while (head < tail) {
    int x;
    int y;
    int d;
    int nx;
    int ny;
    int idx;

    x = qx[head];
    y = qy[head];
    d = dist[x * 8 + y];
    head++;

    if (x > 0) {
      nx = x - 1;
      ny = y;
      idx = nx * 8 + ny;
      if (grid[idx] == 0 && dist[idx] == -1) {
        dist[idx] = d + 1;
        qx[tail] = nx;
        qy[tail] = ny;
        tail++;
      }
    }
    if (x < 7) {
      nx = x + 1;
      ny = y;
      idx = nx * 8 + ny;
      if (grid[idx] == 0 && dist[idx] == -1) {
        dist[idx] = d + 1;
        qx[tail] = nx;
        qy[tail] = ny;
        tail++;
      }
    }
    if (y > 0) {
      nx = x;
      ny = y - 1;
      idx = nx * 8 + ny;
      if (grid[idx] == 0 && dist[idx] == -1) {
        dist[idx] = d + 1;
        qx[tail] = nx;
        qy[tail] = ny;
        tail++;
      }
    }
    if (y < 7) {
      nx = x;
      ny = y + 1;
      idx = nx * 8 + ny;
      if (grid[idx] == 0 && dist[idx] == -1) {
        dist[idx] = d + 1;
        qx[tail] = nx;
        qy[tail] = ny;
        tail++;
      }
    }
  }

  return dist[7 * 8 + 7];
}
