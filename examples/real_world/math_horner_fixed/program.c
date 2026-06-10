// EXPECT: 4.3125
float coeff[4] = {1.5, -2.0, 0.5, 3.0};

float main(void) {
  float x = 1.5;
  float result = coeff[0];
  int i;
  for (i = 1; i < 4; i++) {
    result = result * x + coeff[i];
  }
  return result;
}
