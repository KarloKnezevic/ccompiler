// EXPECT: 2

int data[10] = {10, 12, 11, 30, 13, 14, 40, 15, 16, 17};

int main(void) {
  int threshold = 10;
  int count = 0;
  int i;

  for (i = 2; i < 10; i++) {
    int avg = (data[i - 2] + data[i - 1] + data[i]) / 3;
    if (data[i] > avg + threshold) {
      count++;
    }
  }

  return count;
}
