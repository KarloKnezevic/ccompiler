// EXPECT: 100

int errors[6] = {5, 4, 3, 2, 1, 0};

int main(void) {
  int kp = 2;
  int ki = 1;
  int kd = 1;
  int prev = 0;
  int sum = 0;
  int total = 0;
  int i;

  for (i = 0; i < 6; i++) {
    int e = errors[i];
    int u;
    sum = sum + e;
    u = kp * e + ki * sum + kd * (e - prev);
    total = total + u;
    prev = e;
  }

  return total;
}
