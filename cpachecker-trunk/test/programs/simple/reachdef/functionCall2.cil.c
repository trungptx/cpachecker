/* Generated by CIL v. 1.5.1 */
/* print_CIL_Input is true */

#line 1 "../../scopingTest4.c"
int main(void) 
{ 
  int i ;
  int i___0 ;
  int a ;

  {
#line 2
  i = 0;
  {
#line 4
  while (1) {
    while_continue: /* CIL Label */ ;
#line 4
    if (i) {

    } else {
#line 4
      goto while_break;
    }
#line 5
    i___0 = 5;
  }
  while_break: /* CIL Label */ ;
  }
#line 7
  a = func1();
#line 9
  return (0);
}
}
#line 12 "../../scopingTest4.c"
int func1(void) 
{ 
  int j ;

  {
#line 13
  j = 6;
#line 15
  return (j);
}
}