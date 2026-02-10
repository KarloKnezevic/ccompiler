// EXPECT: 0,5499
// Q16.16: 36041

float main(void) {
  float x = 0.0;
  float y = 0.0;
  float vx = 1.0;
  float vy = 1.0;
  float dt = 0.1;
  float g = 1.0;
  int i;

  for (i = 0; i < 10; i++) {
    x = x + vx * dt;
    y = y + vy * dt;
    vy = vy - g * dt;
  }

  return y;
}
