// EXPECT: 4

int start[6] = {1, 3, 0, 5, 8, 5};
int finish[6] = {2, 4, 6, 7, 9, 9};

int main(void) {
  int count = 1;
  int last = 0;
  int i;

  for (i = 1; i < 6; i++) {
    if (start[i] >= finish[last]) {
      count++;
      last = i;
    }
  }

  return count;
}
