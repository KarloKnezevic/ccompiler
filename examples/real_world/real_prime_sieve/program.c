// EXPECT: 46, FAIL!!!

char isPrime[201];

int main(void) {
  int i;
  int p;
  int count = 0;

  for (i = 0; i <= 200; i++) {
    isPrime[i] = 1;
  }
  isPrime[0] = 0;
  isPrime[1] = 0;

  p = 2;
  while (p * p <= 200) {
    if (isPrime[p]) {
      int j = p * p;
      while (j <= 200) {
        isPrime[j] = 0;
        j = j + p;
      }
    }
    p++;
  }

  for (i = 2; i <= 200; i++) {
    if (isPrime[i]) {
      count++;
    }
  }

  return count;
}
