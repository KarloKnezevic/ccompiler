// EXPECT: 50

int A[20] = {12, -3, 45, 7, 7, 0, -50, 23, 5, -1, 45, 9, 16, -20, 33, 2, -8, 50, -4, 12};

void swap(int i, int j) {
  int t = A[i];
  A[i] = A[j];
  A[j] = t;
}

int partition(int low, int high) {
  int pivot = A[high];
  int i = low - 1;
  int j;
  for (j = low; j < high; j++) {
    if (A[j] <= pivot) {
      i++;
      swap(i, j);
    }
  }
  swap(i + 1, high);
  return i + 1;
}

void quicksort(int low, int high) {
  if (low < high) {
    int p = partition(low, high);
    quicksort(low, p - 1);
    quicksort(p + 1, high);
  }
}

int main(void) {
  quicksort(0, 19);
  return A[19];
}
