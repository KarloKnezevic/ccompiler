// EXPECT: 6765

int main(void) {
  int n = 20;
  int a = 0;
  int b = 1;
  int i;
  int t;

  for (i = 0; i < n; i++) {
    t = a + b;
    a = b;
    b = t;
  }

  return a;
}
