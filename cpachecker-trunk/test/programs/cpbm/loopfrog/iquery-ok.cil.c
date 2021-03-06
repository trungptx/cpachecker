/* Generated by CIL v. 1.3.7 */
/* print_CIL_Input is true */

#line 211 "/usr/lib/gcc/x86_64-linux-gnu/4.4.3/include/stddef.h"
typedef unsigned long size_t;
#line 31 "/usr/include/bits/types.h"
typedef unsigned char __u_char;
#line 141 "/usr/include/bits/types.h"
typedef long __off_t;
#line 142 "/usr/include/bits/types.h"
typedef long __off64_t;
#line 45 "/usr/include/stdio.h"
struct _IO_FILE;
#line 45
struct _IO_FILE;
#line 49 "/usr/include/stdio.h"
typedef struct _IO_FILE FILE;
#line 170 "/usr/include/libio.h"
struct _IO_FILE;
#line 180 "/usr/include/libio.h"
typedef void _IO_lock_t;
#line 186 "/usr/include/libio.h"
struct _IO_marker {
   struct _IO_marker *_next ;
   struct _IO_FILE *_sbuf ;
   int _pos ;
};
#line 271 "/usr/include/libio.h"
struct _IO_FILE {
   int _flags ;
   char *_IO_read_ptr ;
   char *_IO_read_end ;
   char *_IO_read_base ;
   char *_IO_write_base ;
   char *_IO_write_ptr ;
   char *_IO_write_end ;
   char *_IO_buf_base ;
   char *_IO_buf_end ;
   char *_IO_save_base ;
   char *_IO_backup_base ;
   char *_IO_save_end ;
   struct _IO_marker *_markers ;
   struct _IO_FILE *_chain ;
   int _fileno ;
   int _flags2 ;
   __off_t _old_offset ;
   unsigned short _cur_column ;
   signed char _vtable_offset ;
   char _shortbuf[1] ;
   _IO_lock_t *_lock ;
   __off64_t _offset ;
   void *__pad1 ;
   void *__pad2 ;
   void *__pad3 ;
   void *__pad4 ;
   size_t __pad5 ;
   int _mode ;
   char _unused2[(15UL * sizeof(int ) - 4UL * sizeof(void *)) - sizeof(size_t )] ;
};
#line 35 "/usr/include/sys/types.h"
typedef __u_char u_char;
#line 202 "/usr/include/sys/types.h"
typedef unsigned short u_int16_t;
#line 48 "/usr/include/arpa/nameser_compat.h"
struct __anonstruct_HEADER_19 {
   unsigned int id : 16 ;
   unsigned int rd : 1 ;
   unsigned int tc : 1 ;
   unsigned int aa : 1 ;
   unsigned int opcode : 4 ;
   unsigned int qr : 1 ;
   unsigned int rcode : 4 ;
   unsigned int cd : 1 ;
   unsigned int ad : 1 ;
   unsigned int unused : 1 ;
   unsigned int ra : 1 ;
   unsigned int qdcount : 16 ;
   unsigned int ancount : 16 ;
   unsigned int nscount : 16 ;
   unsigned int arcount : 16 ;
};
#line 48 "/usr/include/arpa/nameser_compat.h"
typedef struct __anonstruct_HEADER_19 HEADER;
#line 83 "iquery-ok.c"
enum req_action {
    Finish = 0,
    Refuse = 1,
    Return = 2
} ;
#line 214 "/usr/include/stdio.h"
extern int fclose(FILE *__stream ) ;
#line 249
extern FILE *fopen(char const   * __restrict  __filename , char const   * __restrict  __modes ) ;
#line 339
extern int printf(char const   * __restrict  __format  , ...) ;
#line 407
extern int fscanf(FILE * __restrict  __stream , char const   * __restrict  __format 
                  , ...)  __asm__("__isoc99_fscanf")  ;
#line 513
extern int fgetc(FILE *__stream ) ;
#line 471 "/usr/include/stdlib.h"
extern  __attribute__((__nothrow__)) void *malloc(size_t __size )  __attribute__((__malloc__)) ;
#line 43 "/usr/include/string.h"
extern  __attribute__((__nothrow__)) void *memcpy(void * __restrict  __dest , void const   * __restrict  __src ,
                                                  size_t __n )  __attribute__((__nonnull__(1,2))) ;
#line 337 "/usr/include/resolv.h"
extern  __attribute__((__nothrow__)) int __dn_skipname(u_char const   * , u_char const   * ) ;
#line 71 "/usr/include/assert.h"
extern  __attribute__((__nothrow__, __noreturn__)) void __assert_fail(char const   *__assertion ,
                                                                      char const   *__file ,
                                                                      unsigned int __line ,
                                                                      char const   *__function ) ;
#line 81 "iquery-ok.c"
#pragma ccuredvararg("scanf",printf(1))
#line 85 "iquery-ok.c"
int something  ;
#line 88 "iquery-ok.c"
static enum req_action req_iquery(HEADER *hp , u_char **cpp , u_char *eom , int *buflenp ,
                                  u_char *msg ) 
{ int dlen ;
  int alen ;
  int n ;
  int type ;
  int class ;
  int count ;
  char anbuf[2] ;
  char *data ;
  char *fname ;
  register u_char const   *t_cp ;
  register u_char const   *t_cp___0 ;
  register u_char const   *t_cp___1 ;
  u_char *__cil_tmp18 ;
  u_char const   *__cil_tmp19 ;
  u_char const   *__cil_tmp20 ;
  char const   * __restrict  __cil_tmp21 ;
  unsigned long __cil_tmp22 ;
  unsigned long __cil_tmp23 ;
  u_char *__cil_tmp24 ;
  u_char *__cil_tmp25 ;
  u_char const   *__cil_tmp26 ;
  u_char __cil_tmp27 ;
  u_int16_t __cil_tmp28 ;
  int __cil_tmp29 ;
  u_char const   *__cil_tmp30 ;
  u_char __cil_tmp31 ;
  u_int16_t __cil_tmp32 ;
  int __cil_tmp33 ;
  int __cil_tmp34 ;
  u_char *__cil_tmp35 ;
  u_char *__cil_tmp36 ;
  u_char const   *__cil_tmp37 ;
  u_char __cil_tmp38 ;
  u_int16_t __cil_tmp39 ;
  int __cil_tmp40 ;
  u_char const   *__cil_tmp41 ;
  u_char __cil_tmp42 ;
  u_int16_t __cil_tmp43 ;
  int __cil_tmp44 ;
  int __cil_tmp45 ;
  u_char *__cil_tmp46 ;
  u_char *__cil_tmp47 ;
  u_char *__cil_tmp48 ;
  u_char const   *__cil_tmp49 ;
  u_char __cil_tmp50 ;
  u_int16_t __cil_tmp51 ;
  int __cil_tmp52 ;
  u_char const   *__cil_tmp53 ;
  u_char __cil_tmp54 ;
  u_int16_t __cil_tmp55 ;
  int __cil_tmp56 ;
  int __cil_tmp57 ;
  u_char *__cil_tmp58 ;
  u_char *__cil_tmp59 ;
  unsigned long __cil_tmp60 ;
  u_char *__cil_tmp61 ;
  unsigned long __cil_tmp62 ;
  char const   * __restrict  __cil_tmp63 ;
  unsigned long __cil_tmp64 ;
  unsigned long __cil_tmp65 ;
  int *__cil_tmp66 ;
  int __cil_tmp67 ;
  char const   * __restrict  __cil_tmp68 ;
  char *__cil_tmp69 ;
  u_char *__cil_tmp70 ;
  char *__cil_tmp71 ;
  size_t __cil_tmp72 ;
  char const   * __restrict  __cil_tmp73 ;
  char const   * __restrict  __cil_tmp74 ;
  unsigned long __cil_tmp75 ;
  unsigned long __cil_tmp76 ;
  char *__cil_tmp77 ;
  void * __restrict  __cil_tmp78 ;
  void const   * __restrict  __cil_tmp79 ;
  size_t __cil_tmp80 ;
  unsigned long __cil_tmp81 ;
  unsigned long __cil_tmp82 ;
  char *__cil_tmp83 ;
  char *__cil_tmp84 ;
  int __cil_tmp85 ;

  {
  {
#line 97
  __cil_tmp18 = *cpp;
#line 97
  __cil_tmp19 = (u_char const   *)__cil_tmp18;
#line 97
  __cil_tmp20 = (u_char const   *)eom;
#line 97
  n = __dn_skipname(__cil_tmp19, __cil_tmp20);
  }
#line 97
  if (n < 0) {
    {
#line 98
    __cil_tmp21 = (char const   * __restrict  )"FORMERR IQuery packet name problem\n";
#line 98
    printf(__cil_tmp21);
#line 99
    __cil_tmp22 = (unsigned long )hp;
#line 99
    __cil_tmp23 = __cil_tmp22 + 3;
#line 99
    *((unsigned int *)__cil_tmp23) = 1U;
    }
#line 100
    return ((enum req_action )0);
  } else {

  }
#line 102
  __cil_tmp24 = *cpp;
#line 102
  *cpp = __cil_tmp24 + n;
  {
#line 103
  while (1) {
    while_continue: /* CIL Label */ ;
#line 103
    __cil_tmp25 = *cpp;
#line 103
    t_cp = (u_char const   *)__cil_tmp25;
#line 103
    __cil_tmp26 = t_cp + 1;
#line 103
    __cil_tmp27 = *__cil_tmp26;
#line 103
    __cil_tmp28 = (u_int16_t )__cil_tmp27;
#line 103
    __cil_tmp29 = (int )__cil_tmp28;
#line 103
    __cil_tmp30 = t_cp + 0;
#line 103
    __cil_tmp31 = *__cil_tmp30;
#line 103
    __cil_tmp32 = (u_int16_t )__cil_tmp31;
#line 103
    __cil_tmp33 = (int )__cil_tmp32;
#line 103
    __cil_tmp34 = __cil_tmp33 << 8;
#line 103
    type = __cil_tmp34 | __cil_tmp29;
#line 103
    __cil_tmp35 = *cpp;
#line 103
    *cpp = __cil_tmp35 + 2;
#line 103
    goto while_break;
  }
  while_break: /* CIL Label */ ;
  }
  {
#line 104
  while (1) {
    while_continue___0: /* CIL Label */ ;
#line 104
    __cil_tmp36 = *cpp;
#line 104
    t_cp___0 = (u_char const   *)__cil_tmp36;
#line 104
    __cil_tmp37 = t_cp___0 + 1;
#line 104
    __cil_tmp38 = *__cil_tmp37;
#line 104
    __cil_tmp39 = (u_int16_t )__cil_tmp38;
#line 104
    __cil_tmp40 = (int )__cil_tmp39;
#line 104
    __cil_tmp41 = t_cp___0 + 0;
#line 104
    __cil_tmp42 = *__cil_tmp41;
#line 104
    __cil_tmp43 = (u_int16_t )__cil_tmp42;
#line 104
    __cil_tmp44 = (int )__cil_tmp43;
#line 104
    __cil_tmp45 = __cil_tmp44 << 8;
#line 104
    class = __cil_tmp45 | __cil_tmp40;
#line 104
    __cil_tmp46 = *cpp;
#line 104
    *cpp = __cil_tmp46 + 2;
#line 104
    goto while_break___0;
  }
  while_break___0: /* CIL Label */ ;
  }
#line 105
  __cil_tmp47 = *cpp;
#line 105
  *cpp = __cil_tmp47 + 4;
  {
#line 106
  while (1) {
    while_continue___1: /* CIL Label */ ;
#line 106
    __cil_tmp48 = *cpp;
#line 106
    t_cp___1 = (u_char const   *)__cil_tmp48;
#line 106
    __cil_tmp49 = t_cp___1 + 1;
#line 106
    __cil_tmp50 = *__cil_tmp49;
#line 106
    __cil_tmp51 = (u_int16_t )__cil_tmp50;
#line 106
    __cil_tmp52 = (int )__cil_tmp51;
#line 106
    __cil_tmp53 = t_cp___1 + 0;
#line 106
    __cil_tmp54 = *__cil_tmp53;
#line 106
    __cil_tmp55 = (u_int16_t )__cil_tmp54;
#line 106
    __cil_tmp56 = (int )__cil_tmp55;
#line 106
    __cil_tmp57 = __cil_tmp56 << 8;
#line 106
    dlen = __cil_tmp57 | __cil_tmp52;
#line 106
    __cil_tmp58 = *cpp;
#line 106
    *cpp = __cil_tmp58 + 2;
#line 106
    goto while_break___1;
  }
  while_break___1: /* CIL Label */ ;
  }
#line 107
  __cil_tmp59 = *cpp;
#line 107
  *cpp = __cil_tmp59 + dlen;
  {
#line 108
  __cil_tmp60 = (unsigned long )eom;
#line 108
  __cil_tmp61 = *cpp;
#line 108
  __cil_tmp62 = (unsigned long )__cil_tmp61;
#line 108
  if (__cil_tmp62 != __cil_tmp60) {
    {
#line 109
    __cil_tmp63 = (char const   * __restrict  )"FORMERR IQuery message length off\n";
#line 109
    printf(__cil_tmp63);
#line 110
    __cil_tmp64 = (unsigned long )hp;
#line 110
    __cil_tmp65 = __cil_tmp64 + 3;
#line 110
    *((unsigned int *)__cil_tmp65) = 1U;
    }
#line 111
    return ((enum req_action )0);
  } else {

  }
  }
  {
#line 120
  if (type == 1) {
#line 120
    goto case_1;
  } else {

  }
#line 124
  goto switch_default;
  case_1: /* CIL Label */ 
  {
#line 121
  __cil_tmp66 = & something;
#line 121
  __cil_tmp67 = *__cil_tmp66;
#line 121
  if (__cil_tmp67 == 0) {
#line 122
    return ((enum req_action )1);
  } else {

  }
  }
#line 123
  goto switch_break;
  switch_default: /* CIL Label */ 
#line 125
  return ((enum req_action )1);
  switch_break: /* CIL Label */ ;
  }
  {
#line 127
  __cil_tmp68 = (char const   * __restrict  )"req: IQuery class %d type %d\n";
#line 127
  printf(__cil_tmp68, class, type);
#line 129
  __cil_tmp69 = (char *)msg;
#line 129
  fname = __cil_tmp69 + 12;
#line 130
  __cil_tmp70 = *cpp;
#line 130
  __cil_tmp71 = (char *)__cil_tmp70;
#line 130
  alen = __cil_tmp71 - fname;
  }
  {
#line 135
  __cil_tmp72 = (size_t )alen;
#line 135
  if (__cil_tmp72 > 2UL) {
    {
#line 136
    __cil_tmp73 = (char const   * __restrict  )"BUFFER OVERFLOW DETECTED!\n";
#line 136
    printf(__cil_tmp73);
    }
#line 137
    return ((enum req_action )1);
  } else {

  }
  }
  {
#line 139
  __cil_tmp74 = (char const   * __restrict  )"Copying %d bytes from fname to anbuf which can store %d bytes\n";
#line 139
  printf(__cil_tmp74, alen, 2UL);
  }
  {
#line 143
  __cil_tmp75 = 0 * 1UL;
#line 143
  __cil_tmp76 = (unsigned long )(anbuf) + __cil_tmp75;
#line 143
  __cil_tmp77 = (char *)__cil_tmp76;
#line 143
  __cil_tmp78 = (void * __restrict  )__cil_tmp77;
#line 143
  __cil_tmp79 = (void const   * __restrict  )fname;
#line 143
  __cil_tmp80 = (size_t )alen;
#line 143
  memcpy(__cil_tmp78, __cil_tmp79, __cil_tmp80);
#line 144
  __cil_tmp81 = 0 * 1UL;
#line 144
  __cil_tmp82 = (unsigned long )(anbuf) + __cil_tmp81;
#line 144
  __cil_tmp83 = (char *)__cil_tmp82;
#line 144
  __cil_tmp84 = __cil_tmp83 + alen;
#line 144
  data = __cil_tmp84 - dlen;
#line 145
  *cpp = (u_char *)fname;
#line 146
  __cil_tmp85 = *buflenp;
#line 146
  *buflenp = __cil_tmp85 - 12;
#line 147
  count = 0;
  }
#line 152
  return ((enum req_action )0);
}
}
#line 156 "iquery-ok.c"
int create_msg(u_char *msg , int len ) 
{ FILE *f ;
  int i ;
  int c ;
  u_char *tmp ;
  char const   * __restrict  __cil_tmp7 ;
  char const   * __restrict  __cil_tmp8 ;
  void *__cil_tmp9 ;
  unsigned long __cil_tmp10 ;
  unsigned long __cil_tmp11 ;
  char const   * __restrict  __cil_tmp12 ;

  {
  {
#line 159
  i = 0;
#line 162
  __cil_tmp7 = (char const   * __restrict  )"iquery-file";
#line 162
  __cil_tmp8 = (char const   * __restrict  )"r";
#line 162
  f = fopen(__cil_tmp7, __cil_tmp8);
  }
  {
#line 162
  __cil_tmp9 = (void *)0;
#line 162
  __cil_tmp10 = (unsigned long )__cil_tmp9;
#line 162
  __cil_tmp11 = (unsigned long )f;
#line 162
  if (__cil_tmp11 == __cil_tmp10) {
    {
#line 164
    __cil_tmp12 = (char const   * __restrict  )"iquery-file not found!\n";
#line 164
    printf(__cil_tmp12);
    }
#line 165
    return (-1);
  } else {

  }
  }
  {
#line 168
  while (1) {
    while_continue: /* CIL Label */ ;
    {
#line 168
    c = fgetc(f);
    }
#line 168
    if (c != -1) {
#line 168
      if (i < len) {

      } else {
#line 168
        goto while_break;
      }
    } else {
#line 168
      goto while_break;
    }
#line 169
    tmp = msg;
#line 169
    msg = msg + 1;
#line 169
    *tmp = (u_char )c;
#line 170
    i = i + 1;
  }
  while_break: /* CIL Label */ ;
  }
  {
#line 173
  fclose(f);
  }
#line 175
  return (i);
}
}
#line 179 "iquery-ok.c"
int main(int argc , char **argv ) 
{ HEADER *hp ;
  u_char *msg ;
  u_char *cp ;
  u_char *eom ;
  int msglen ;
  FILE *f ;
  int tmp ;
  void *tmp___0 ;
  char **__cil_tmp11 ;
  char *__cil_tmp12 ;
  char const   * __restrict  __cil_tmp13 ;
  char const   * __restrict  __cil_tmp14 ;
  void *__cil_tmp15 ;
  unsigned long __cil_tmp16 ;
  unsigned long __cil_tmp17 ;
  FILE * __restrict  __cil_tmp18 ;
  char const   * __restrict  __cil_tmp19 ;
  unsigned long __cil_tmp20 ;
  int *__cil_tmp21 ;
  u_char **__cil_tmp22 ;
  int *__cil_tmp23 ;
  int __cil_tmp24 ;
  char const   * __restrict  __cil_tmp25 ;
  unsigned int __cil_tmp26 ;
  unsigned int __cil_tmp27 ;
  char const   * __restrict  __cil_tmp28 ;

  {
#line 186
  if (argc == 2) {

  } else {
    {
#line 186
    __assert_fail("argc==2", "iquery-ok.c", 186U, "main");
    }
  }
  {
#line 187
  __cil_tmp11 = argv + 1;
#line 187
  __cil_tmp12 = *__cil_tmp11;
#line 187
  __cil_tmp13 = (char const   * __restrict  )__cil_tmp12;
#line 187
  __cil_tmp14 = (char const   * __restrict  )"r";
#line 187
  f = fopen(__cil_tmp13, __cil_tmp14);
  }
  {
#line 188
  __cil_tmp15 = (void *)0;
#line 188
  __cil_tmp16 = (unsigned long )__cil_tmp15;
#line 188
  __cil_tmp17 = (unsigned long )f;
#line 188
  if (__cil_tmp17 != __cil_tmp16) {

  } else {
    {
#line 188
    __assert_fail("f!=((void *)0)", "iquery-ok.c", 188U, "main");
    }
  }
  }
  {
#line 189
  __cil_tmp18 = (FILE * __restrict  )f;
#line 189
  __cil_tmp19 = (char const   * __restrict  )"%d";
#line 189
  tmp = fscanf(__cil_tmp18, __cil_tmp19, & something);
  }
#line 189
  if (tmp != 0) {

  } else {
    {
#line 189
    __assert_fail("(fscanf(f, \"%d\", &something)) != 0", "iquery-ok.c", 189U, "main");
    }
  }
  {
#line 191
  __cil_tmp20 = 10000UL * 1UL;
#line 191
  tmp___0 = malloc(__cil_tmp20);
#line 191
  msg = (u_char *)tmp___0;
#line 192
  __cil_tmp21 = & msglen;
#line 192
  *__cil_tmp21 = create_msg(msg, 10000);
#line 194
  hp = (HEADER *)msg;
#line 201
  __cil_tmp22 = & cp;
#line 201
  *__cil_tmp22 = msg + 12UL;
#line 202
  __cil_tmp23 = & msglen;
#line 202
  __cil_tmp24 = *__cil_tmp23;
#line 202
  eom = msg + __cil_tmp24;
#line 204
  __cil_tmp25 = (char const   * __restrict  )"opcode = %d\n";
#line 204
  __cil_tmp26 = hp->opcode;
#line 204
  printf(__cil_tmp25, __cil_tmp26);
  }
  {
#line 206
  __cil_tmp27 = hp->opcode;
  {
#line 207
  if ((int )__cil_tmp27 == 1) {
#line 207
    goto case_1;
  } else {

  }
#line 210
  goto switch_default;
  case_1: /* CIL Label */ 
  {
#line 208
  req_iquery(hp, & cp, eom, & msglen, msg);
  }
#line 209
  goto switch_break;
  switch_default: /* CIL Label */ 
  {
#line 211
  __cil_tmp28 = (char const   * __restrict  )"We only support inverse queries!\n";
#line 211
  printf(__cil_tmp28);
  }
  switch_break: /* CIL Label */ ;
  }
  }
#line 214
  return (0);
}
}
