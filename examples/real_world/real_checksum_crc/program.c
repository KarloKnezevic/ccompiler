// EXPECT: 142, FAIL!!!

char data[12] = {16, 35, 127, 0, 90, 195, 17, 34, 51, 68, 85, 102};

int main(void) {
  int crc = 171;
  int i;
  int bit;

  for (i = 0; i < 12; i++) {
    int b = data[i];
    crc = crc ^ b;
    for (bit = 0; bit < 8; bit++) {
      if (crc % 2 == 1) {
        crc = (crc / 2) ^ 140;
      } else {
        crc = crc / 2;
      }
      crc = crc % 256;
    }
  }

  return crc;
}
