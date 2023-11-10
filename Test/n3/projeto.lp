/* Objective function */
min:    11 h1 + 10 h2 + 7 h3;

/* Variable bounds */
restricao1: 11 h1 + 10 h2 + 7 h3=99;
restricao2: h2 <=1;
restricao3: h3 <=10;

int h1;
int h2;
int h3;