// EXPECT: 15

int weights[5] = {2, 3, 4, 5, 9};
int values[5] = {3, 4, 5, 8, 10};
int dp[11];

int main(void) {
  int i;
  int cap;

  for (cap = 0; cap <= 10; cap++) {
    dp[cap] = 0;
  }

  for (i = 0; i < 5; i++) {
    int w = weights[i];
    int v = values[i];
    for (cap = 10; cap >= w; cap--) {
      int candidate = dp[cap - w] + v;
      if (candidate > dp[cap]) {
        dp[cap] = candidate;
      }
    }
  }

  return dp[10];
}
