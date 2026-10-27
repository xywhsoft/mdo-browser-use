"""Fault every result allocation in the actual stream commit fragment."""
import os
from pathlib import Path
import subprocess
import sys
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'))
import dev


class StreamAllocationTests(unittest.TestCase):
    def test_result_allocation_failures_never_consume_append_close_or_release(self):
        fragment=(dev.ROOT/'host/stream-exchange.inc.c').read_text(encoding='utf-8')
        program=r'''
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
typedef const char* cstr;
typedef struct xvalue {unsigned fields;} xvalue;
typedef struct mdo_error_v1 {bool MayHaveCompleted;} mdo_error_v1;
typedef struct SdkStream {
  char Id[37];xvalue* Scope;unsigned char* Bytes;size_t Capacity,Start,Size;
  uint64_t Expires;bool Closed;
} SdkStream;
#define XRT_STR_LITERAL(S) S
static long FailAt,Attempts,Live;static bool Triggered;static unsigned Released;
static unsigned char Returned[16384];static size_t ReturnedSize;
static void* alloc(size_t n){
  if(Attempts++==FailAt){Triggered=true;return NULL;}
  void* p=calloc(1,n);if(p)Live++;return p;
}
static xvalue* xrtValueObject(void){return alloc(sizeof(xvalue));}
static xvalue* xrtValueBool(bool value){(void)value;return xrtValueObject();}
static void xrtValueRelease(xvalue* value){if(value){Live--;free(value);}}
static bool xrtValueObjectSetNew(xvalue* object,cstr key,xvalue* value){
  char* owned=object&&value?alloc(strlen(key)+1):NULL;
  bool ok=owned!=NULL;
  if(ok){object->fields++;Live--;free(owned);}
  xrtValueRelease(value);return ok;
}
static bool SdkPut(xvalue* object,cstr key,cstr text){(void)text;return xrtValueObjectSetNew(object,key,xrtValueObject());}
static bool SdkPutUInt(xvalue* object,cstr key,uint64_t number){(void)number;return xrtValueObjectSetNew(object,key,xrtValueObject());}
static bool SdkByteValue(xvalue* object,const void* bytes,size_t size){
  char* encoded=alloc(size*2+1);if(!encoded)return false;
  bool ok=SdkPut(object,"base64",encoded)&&SdkPutUInt(object,"bytes",size);
  Live--;free(encoded);if(ok){memcpy(Returned,bytes,size);ReturnedSize=size;}return ok;
}
static void xrtSecureZero(void* bytes,size_t size){volatile unsigned char* p=bytes;while(size--)*p++=0;}
static void SdkStreamFree(SdkStream* stream){
  Released++;xrtSecureZero(stream->Bytes,stream->Capacity);memset(stream,0,sizeof(*stream));
}
static bool MdoApplicationError(mdo_error_v1* e,cstr code,cstr message,cstr stage,unsigned status,bool uncertain){
  (void)code;(void)message;(void)stage;(void)status;e->MayHaveCompleted=uncertain;return false;
}
'''+fragment+r'''
int main(void){
  const char* methods[]={"stream.read","stream.write","stream.close","stream.stat","stream.release"};
  unsigned failures=0,successes=0;
  for(unsigned m=0;m<5;m++){
    bool completed=false;
    for(long fault=0;fault<64;fault++){
      unsigned char ring[32],before[32],input[]={42,43,255};
      for(size_t i=0;i<sizeof(ring);i++)ring[i]=(unsigned char)(i+128);memcpy(before,ring,sizeof(ring));
      SdkStream stream={0};strcpy(stream.Id,"str-01234567890123456789012345678901");
      stream.Scope=(void*)1;stream.Bytes=ring;stream.Capacity=32;stream.Start=27;stream.Size=13;
      SdkStream initial=stream;xvalue* result=NULL;mdo_error_v1 error={0};
      FailAt=fault;Attempts=0;Live=0;Triggered=false;Released=0;ReturnedSize=0;
      bool ok=BuStreamExchange(&stream,methods[m],m==0,m==1,input,sizeof(input),12,&result,&error);
      if(!ok){
        failures++;
        if(!Triggered||result||error.MayHaveCompleted||Released||memcmp(&stream,&initial,sizeof(stream))||memcmp(ring,before,sizeof(ring))){fprintf(stderr,"mutation before failed acknowledgement: method=%u fault=%ld\n",m,fault);return 1;}
      }else{
        successes++;if(!result)return 2;
        if(m==0){
          if(result->fields!=7||ReturnedSize!=12||stream.Size!=1||stream.Start!=7)return 3;
          for(size_t i=0;i<12;i++)if(Returned[i]!=before[(27+i)%32]||ring[(27+i)%32])return 4;
        }else if(m==1){
          if(result->fields!=6||stream.Size!=16||stream.Start!=27)return 5;
          for(size_t i=0;i<3;i++)if(ring[(27+13+i)%32]!=input[i])return 6;
        }else if(m==2){if(!stream.Closed||stream.Size!=13||result->fields!=5)return 7;}
        else if(m==3){if(memcmp(&stream,&initial,sizeof(stream))||result->fields!=5)return 8;}
        else {SdkStream zero={0};if(Released!=1||memcmp(&stream,&zero,sizeof(stream)))return 9;}
        if(Triggered)return 10;completed=true;
      }
      xrtValueRelease(result);if(Live)return 11;
      if(completed)break;
    }
    if(!completed)return 12;
  }
  if(failures<40||successes!=5)return 13;
  printf("%u allocation failure positions preserved stream state; %u successful operations\n",failures,successes);
  return 0;
}
'''
        directory=dev.ENV/'qa';directory.mkdir(exist_ok=True)
        source=directory/'stream-allocations.c';binary=directory/'stream-allocations.exe'
        source.write_text(program,encoding='utf-8',newline='\n')
        compiler=Path('E:/software/w64devkit/bin/gcc.exe')
        subprocess.run([str(compiler),'-std=c11','-O2','-Wall',str(source),'-o',str(binary)],check=True,capture_output=True)
        result=subprocess.run([str(binary)],env=dict(os.environ,PATH=str(compiler.parent)+os.pathsep+os.environ.get('PATH','')),
                              check=True,capture_output=True,text=True)
        self.assertIn('allocation failure positions preserved stream state',result.stdout)


if __name__=='__main__':unittest.main()
