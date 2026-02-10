// EXPECT: 6

int truth[8] = {1, 0, 1, 1, 0, 0, 1, 0};
int pred[8] = {1, 1, 1, 0, 0, 0, 1, 0};

int main(void) {
  int correct = 0;
  int i;

  for (i = 0; i < 8; i++) {
    if (truth[i] == pred[i]) {
      correct++;
    }
  }

  return correct;
}
