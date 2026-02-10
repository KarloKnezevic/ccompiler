// EXPECT: 1
// Q16.16: 65536

int a = 1;
int b = -2;
int c = 0;

float main(void) {
  float x = 0.0;
  float psi = 0.5;
  int iter;

  for (iter = 0; iter < 20; iter++) {
    float grad = 2.0 * (float)a * x + (float)b;
    x = x - psi * grad;
  }

  return x;
}
