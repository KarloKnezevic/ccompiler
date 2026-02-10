// EXPECT: 9

int grid[16] = {
  1, 3, 1, 2,
  2, 1, 8, 1,
  4, 2, 1, 3,
  5, 2, 1, 1
};

int dp[16];

int min2(int a, int b) {
  if (a < b) {
    return a;
  }
  return b;
}

int main(void) {
  int i;
  int j;

  for (i = 0; i < 4; i++) {
    for (j = 0; j < 4; j++) {
      int idx = i * 4 + j;
      int val = grid[idx];
      if (i == 0 && j == 0) {
        dp[idx] = val;
      } else if (i == 0) {
        dp[idx] = dp[idx - 1] + val;
      } else if (j == 0) {
        dp[idx] = dp[idx - 4] + val;
      } else {
        dp[idx] = min2(dp[idx - 4], dp[idx - 1]) + val;
      }
    }
  }

  return dp[15];
}
