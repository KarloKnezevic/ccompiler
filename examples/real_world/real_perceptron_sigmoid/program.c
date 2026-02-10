// EXPECT: 1, FAIL!!!

float X[4] = {1.0, 2.0, -1.0, -2.0};
float Y[4] = {1.0, 0.0, -1.0, 0.0};
int LABEL[4] = {1, 1, 0, 0};

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

float sigmoid(float t, float a) {
  return 1.0 / (1.0 + exp_approx(-a * t));
}

int main(void) {
  float w0 = 0.1;
  float w1 = 0.1;
  float b = 0.0;
  float lr = 0.2;
  float a = 1.0;
  int epoch;
  int i;

  for (epoch = 0; epoch < 8; epoch++) {
    for (i = 0; i < 4; i++) {
      float z = w0 * X[i] + w1 * Y[i] + b;
      float s = sigmoid(z, a);
      float err = (float)LABEL[i] - s;
      w0 = w0 + lr * err * X[i];
      w1 = w1 + lr * err * Y[i];
      b = b + lr * err;
    }
  }

  {
    float xt = 1.0;
    float yt = 0.5;
    int true_label = 1;
    float z = w0 * xt + w1 * yt + b;
    float s = sigmoid(z, a);
    int predicted;
    if (s >= 0.5) {
      predicted = 1;
    } else {
      predicted = 0;
    }
    if (predicted == true_label) {
      return 1;
    }
    return 0;
  }
}
