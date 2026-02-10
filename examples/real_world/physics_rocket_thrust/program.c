// EXPECT: 2,0
// Q16.16: 131072

float main(void) {
  float v = 0.0;
  float a = 0.8;
  float dt = 0.5;
  int i;

  for (i = 0; i < 5; i++) {
    v = v + a * dt;
  }

  return v;
}
