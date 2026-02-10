// EXPECT: 0

float W[3] = {0.5, -0.25, 0.75};
float X[3] = {1.0, 2.0, -1.0};

float exp_approx(float u) {
  float term = 1.0;
  float sum = 1.0;
  int k;
  for (k = 1; k <= 10; k++) {
    term = term * u / (float)k;
    sum = sum + term;
  }
  return sum;
}

float sigmoid(float t) {
  return 1.0 / (1.0 + exp_approx(-t));
}

int main(void) {
  float z = W[0] * X[0] + W[1] * X[1] + W[2] * X[2] + 0.1;
  float s = sigmoid(z);
  int pred;

  if (s >= 0.5) {
    pred = 1;
  } else {
    pred = 0;
  }

  return pred;
}
