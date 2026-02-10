// EXPECT: 10

int W[25] = {
  0, 4, 2, 0, 0,
  4, 0, 1, 5, 0,
  2, 1, 0, 8, 10,
  0, 5, 8, 0, 2,
  0, 0, 10, 2, 0
};

int main(void) {
  int dist[5];
  int visited[5];
  int i;
  int j;

  for (i = 0; i < 5; i++) {
    dist[i] = 100000;
    visited[i] = 0;
  }
  dist[0] = 0;

  for (i = 0; i < 5; i++) {
    int u = -1;
    int best = 100000;
    for (j = 0; j < 5; j++) {
      if (!visited[j] && dist[j] < best) {
        best = dist[j];
        u = j;
      }
    }
    if (u == -1) {
      break;
    }
    visited[u] = 1;
    for (j = 0; j < 5; j++) {
      int w = W[u * 5 + j];
      if (w > 0 && dist[u] + w < dist[j]) {
        dist[j] = dist[u] + w;
      }
    }
  }

  return dist[4];
}
