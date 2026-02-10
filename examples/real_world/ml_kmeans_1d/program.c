// EXPECT: 110

int data[4] = {1, 2, 10, 11};

int main(void) {
  int c1 = 1;
  int c2 = 10;
  int iter;

  for (iter = 0; iter < 2; iter++) {
    int sum1 = 0;
    int sum2 = 0;
    int count1 = 0;
    int count2 = 0;
    int i;

    for (i = 0; i < 4; i++) {
      int d1 = data[i] - c1;
      int d2 = data[i] - c2;
      if (d1 < 0) {
        d1 = -d1;
      }
      if (d2 < 0) {
        d2 = -d2;
      }
      if (d1 <= d2) {
        sum1 = sum1 + data[i];
        count1++;
      } else {
        sum2 = sum2 + data[i];
        count2++;
      }
    }

    if (count1 > 0) {
      c1 = sum1 / count1;
    }
    if (count2 > 0) {
      c2 = sum2 / count2;
    }
  }

  return c1 * 100 + c2;
}
