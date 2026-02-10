// EXPECT: 5,5
// Q16.16: 360448

float main(void) {
  float x0 = 0.5;
  float v0 = 1.5;
  float a = 1.0;
  float t = 2.0;
  float x;

  x = x0 + v0 * t + 0.5 * a * t * t;
  return x;
}
