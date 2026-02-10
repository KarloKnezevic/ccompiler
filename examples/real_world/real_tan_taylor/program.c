// EXPECT: 0,5463
// Q16.16: 35802

float X = 0.5;

float sin_taylor(float x) {
  float term = x;
  float sum = x;
  int k;
  for (k = 1; k <= 5; k++) {
    float denom = (float)((2 * k) * (2 * k + 1));
    term = term * (-x * x) / denom;
    sum = sum + term;
  }
  return sum;
}

float cos_taylor(float x) {
  float term = 1.0;
  float sum = 1.0;
  int k;
  for (k = 1; k <= 5; k++) {
    float denom = (float)((2 * k - 1) * (2 * k));
    term = term * (-x * x) / denom;
    sum = sum + term;
  }
  return sum;
}

float main(void) {
  float s = sin_taylor(X);
  float c = cos_taylor(X);
  float t = s / c;
  return t;
}
