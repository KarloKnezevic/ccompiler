struct S {
    char t;
    int x;
};

/*
ne radi nista
*/
void fun(int xYz) {
    return;
}

// glavni program "testira osnovne kljucne rijeci i operatore za lekser"
int main(void) {
    int A[512];
    int t[] = {1,2,3};
    char tmp[] = "te\nst";
    const char *x = "\"tes\"t2\"";
    int xYz, *abc;
    float a_B1=10.23 + .12 + 1.43e-3 + .43E3 + .46E+134 + 0.47E-123;
    int i;
    struct S strct;
    strct.t = 'b';
    strct.x = 4321;
    
    xYz = 12345; // nekakav komentar
    abc = &xYz;
    abc = (&xYz);
    *abc = *abc+++xYz;
    *abc = 054 % 5;
    *abc = 0xaafff;
    i = 3*2+5-3|3&3^3;
    
    tmp[1] = 'b';
    tmp[2] = '\n';
    tmp[3] = '''; // greska
    tmp[0] = '\'';
    for (i=0; i<4; ++i) {
        tmp[i] = (char)*abc; /* komentar
                                komentar
                                komentar */
        break;
        continue;
        return *&xYz;
    }
    
    if (1>=3 && i>2 || i<=12) {
        fun(3);
    } else {
        fun(5);
    }
    
    while (1) {
        break;
    }
    
    return 0;
}

