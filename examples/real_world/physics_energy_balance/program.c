// EXPECT: 13
// Q16.16: 851968

float main(void) {
  float m = 2.0;
  float g = 1.5;
  float h = 3.0;
  float v = 2.0;
  float energy;

  energy = m * g * h + 0.5 * m * v * v;
  return energy;
}
