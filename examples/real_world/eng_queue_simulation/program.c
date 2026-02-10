// EXPECT: 0

int arrivals[8] = {3, 1, 0, 4, 2, 1, 0, 1};

int main(void) {
  int queue = 0;
  int i;

  for (i = 0; i < 8; i++) {
    queue = queue + arrivals[i] - 2;
    if (queue < 0) {
      queue = 0;
    }
  }

  return queue;
}
