// EXPECT: 420

int a = 84;
int b = 30;

int gcd(int x, int y) {
  int t;
  while (y != 0) {
    t = x % y;
    x = y;
    y = t;
  }
  return x;
}

int main(void) {
  int g = gcd(a, b);
  int l = (a / g) * b;
  return l;
}
