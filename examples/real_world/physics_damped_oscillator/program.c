// EXPECT: -0,3791
// Q16.16: -24845

float main(void) {
  float x = 1.0;
  float v = 0.0;
  float k = 1.0;
  float c = 0.1;
  float dt = 0.1;
  float a;
  int i;

  for (i = 0; i < 20; i++) {
    a = -(k * x) - c * v;
    v = v + a * dt;
    x = x + v * dt;
  }

  return x;
}
