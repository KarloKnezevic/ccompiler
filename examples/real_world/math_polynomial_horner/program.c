// EXPECT: 116

int coeff[5] = {2, -3, 5, -7, 11};

int main(void) {
  int x = 3;
  int result = coeff[0];
  int i;

  for (i = 1; i < 5; i++) {
    result = result * x + coeff[i];
  }

  return result;
}
