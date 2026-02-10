// EXPECT: 1.5
// Q16.16: 98304

float X[4] = {1.0, 2.0, 3.0, 4.0};
float Y[4] = {2.0, 4.0, 6.0, 8.0};

float main(void) {
  float w = 0.0;
  float b = 0.0;
  float lr = 0.1;
  float sum_err = 0.0;
  float sum_errx = 0.0;
  int i;

  for (i = 0; i < 4; i++) {
    float pred = w * X[i] + b;
    float err = pred - Y[i];
    sum_err = sum_err + err;
    sum_errx = sum_errx + err * X[i];
  }

  sum_err = sum_err / 4.0;
  sum_errx = sum_errx / 4.0;
  w = w - lr * sum_errx;
  b = b - lr * sum_err;

  return w;
}
