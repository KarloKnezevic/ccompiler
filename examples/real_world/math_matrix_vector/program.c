// EXPECT: 18

int M[9] = {
  2, 0, -1,
  1, 3, 2,
  0, -2, 4
};

int V[3] = {3, -1, 2};

int main(void) {
  int r0;
  int r1;
  int r2;
  int sum;

  r0 = M[0] * V[0];
  r0 = r0 + M[1] * V[1];
  r0 = r0 + M[2] * V[2];

  r1 = M[3] * V[0];
  r1 = r1 + M[4] * V[1];
  r1 = r1 + M[5] * V[2];

  r2 = M[6] * V[0];
  r2 = r2 + M[7] * V[1];
  r2 = r2 + M[8] * V[2];

  sum = r0 + r1 + r2;
  return sum;
}
