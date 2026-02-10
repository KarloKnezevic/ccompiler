// EXPECT: -5

int A[8] = {3, -1, 4, 1, 5, -9, 2, 6};
int B[8] = {2, 7, 1, 8, 2, 8, -1, 8};

int main(void) {
  int i;
  int sum = 0;
  for (i = 0; i < 8; i++) {
    sum = sum + A[i] * B[i];
  }
  return sum;
}
