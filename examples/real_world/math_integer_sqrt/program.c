// EXPECT: 111

int main(void) {
  int n = 12345;
  int low = 0;
  int high = 200;
  int ans = 0;

  while (low <= high) {
    int mid = (low + high) / 2;
    int sq = mid * mid;
    if (sq <= n) {
      ans = mid;
      low = mid + 1;
    } else {
      high = mid - 1;
    }
  }

  return ans;
}
