package kik.i18n;
/** Minimal QR Code encoder (ISO/IEC 18004): byte mode, error correction level M, versions 1-10. Pure Java so it runs on the TV and in scripts/QrCheck.java. */
public final class QrCode {
 private static final int[] ECC_PER_BLOCK={0,10,16,26,18,24,16,18,22,22,26},BLOCKS={0,1,1,1,2,2,4,4,4,5,5};
 public final int size;private final boolean[][] modules,function;
 private QrCode(int version){size=version*4+17;modules=new boolean[size][size];function=new boolean[size][size];}
 public boolean dark(int x,int y){return modules[y][x];}

 /** Encodes UTF-8 text in the smallest version that fits; throws when it does not fit version 10. */
 public static QrCode encode(String text){
  byte[] data;try{data=text.getBytes("UTF-8");}catch(java.io.UnsupportedEncodingException e){throw new IllegalStateException(e);}
  for(int v=1;v<=10;v++){int capacity=dataCodewords(v)*8,countBits=v<10?8:16;if(4+countBits+data.length*8<=capacity)return build(v,data,countBits,capacity);}
  throw new IllegalArgumentException("Text too long for QR");
 }
 static int rawModules(int v){int r=(16*v+128)*v+64;if(v>=2){int n=v/7+2;r-=(25*n-10)*n-55;if(v>=7)r-=36;}return r;}
 static int dataCodewords(int v){return rawModules(v)/8-ECC_PER_BLOCK[v]*BLOCKS[v];}

 private static QrCode build(int v,byte[] data,int countBits,int capacity){
  int[] bits=new int[capacity];int n=0;n=put(bits,n,4,4);n=put(bits,n,data.length,countBits);for(byte b:data)n=put(bits,n,b&0xff,8);
  n=Math.min(capacity,n+4);n=(n+7)/8*8;
  byte[] codewords=new byte[capacity/8];for(int i=0;i<n;i++)codewords[i>>>3]|=bits[i]<<(7-(i&7));
  for(int i=n/8,pad=0xEC;i<codewords.length;i++,pad^=0xEC^0x11)codewords[i]=(byte)pad;
  QrCode q=new QrCode(v);q.drawFunctions(v);q.drawCodewords(q.interleave(v,codewords));
  int best=0;long min=Long.MAX_VALUE;for(int m=0;m<8;m++){q.mask(m);q.drawFormat(m);long p=q.penalty();if(p<min){min=p;best=m;}q.mask(m);}
  q.mask(best);q.drawFormat(best);return q;
 }
 private static int put(int[] bits,int n,int value,int count){for(int i=count-1;i>=0;i--)bits[n++]=(value>>>i)&1;return n;}

 private void set(int x,int y,boolean dark){modules[y][x]=dark;function[y][x]=true;}
 private void drawFunctions(int v){
  for(int i=0;i<size;i++){set(6,i,i%2==0);set(i,6,i%2==0);}
  finder(3,3);finder(size-4,3);finder(3,size-4);
  int[] align=alignment(v);int last=align.length-1;
  for(int i=0;i<align.length;i++)for(int j=0;j<align.length;j++)if(!(i==0&&j==0||i==0&&j==last||i==last&&j==0))for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++)set(align[i]+dx,align[j]+dy,Math.max(Math.abs(dx),Math.abs(dy))!=1);
  drawFormat(0);
  if(v>=7){int rem=v;for(int i=0;i<12;i++)rem=(rem<<1)^((rem>>>11)*0x1F25);int bits=v<<12|rem;for(int i=0;i<18;i++){boolean b=((bits>>>i)&1)!=0;int a=size-11+i%3,c=i/3;set(a,c,b);set(c,a,b);}}
 }
 private void finder(int cx,int cy){for(int dy=-4;dy<=4;dy++)for(int dx=-4;dx<=4;dx++){int x=cx+dx,y=cy+dy,d=Math.max(Math.abs(dx),Math.abs(dy));if(x>=0&&x<size&&y>=0&&y<size)set(x,y,d!=2&&d!=4);}}
 private int[] alignment(int v){if(v==1)return new int[0];int n=v/7+2,step=(v*4+n*2+1)/(n*2-2)*2;int[] r=new int[n];r[0]=6;for(int i=n-1,pos=size-7;i>=1;i--,pos-=step)r[i]=pos;return r;}
 /** Level M has format indicator 00, so the 5 data bits are just the mask. */
 private void drawFormat(int mask){
  int rem=mask;for(int i=0;i<10;i++)rem=(rem<<1)^((rem>>>9)*0x537);int bits=(mask<<10|rem)^0x5412;
  for(int i=0;i<=5;i++)set(8,i,bit(bits,i));set(8,7,bit(bits,6));set(8,8,bit(bits,7));set(7,8,bit(bits,8));for(int i=9;i<15;i++)set(14-i,8,bit(bits,i));
  for(int i=0;i<8;i++)set(size-1-i,8,bit(bits,i));for(int i=8;i<15;i++)set(8,size-15+i,bit(bits,i));set(8,size-8,true);
 }
 private static boolean bit(int x,int i){return ((x>>>i)&1)!=0;}

 private byte[] interleave(int v,byte[] data){
  int blocks=BLOCKS[v],ecc=ECC_PER_BLOCK[v],raw=rawModules(v)/8,shortCount=blocks-raw%blocks,shortLen=raw/blocks;
  byte[] divisor=divisor(ecc);byte[][] all=new byte[blocks][];
  for(int i=0,k=0;i<blocks;i++){int len=shortLen-ecc+(i<shortCount?0:1);byte[] dat=java.util.Arrays.copyOfRange(data,k,k+len);k+=len;byte[] block=new byte[shortLen+1],e=remainder(dat,divisor);System.arraycopy(dat,0,block,0,len);System.arraycopy(e,0,block,shortLen+1-ecc,ecc);all[i]=block;}
  byte[] out=new byte[raw];int n=0;for(int i=0;i<=shortLen;i++)for(int j=0;j<blocks;j++)if(i!=shortLen-ecc||j>=shortCount)out[n++]=all[j][i];return out;
 }
 private static byte[] divisor(int degree){byte[] r=new byte[degree];r[degree-1]=1;int root=1;for(int i=0;i<degree;i++){for(int j=0;j<degree;j++){r[j]=(byte)mul(r[j]&0xff,root);if(j+1<degree)r[j]^=r[j+1];}root=mul(root,2);}return r;}
 private static byte[] remainder(byte[] data,byte[] divisor){byte[] r=new byte[divisor.length];for(byte b:data){int f=(b^r[0])&0xff;System.arraycopy(r,1,r,0,r.length-1);r[r.length-1]=0;for(int i=0;i<r.length;i++)r[i]^=(byte)mul(divisor[i]&0xff,f);}return r;}
 private static int mul(int x,int y){int z=0;for(int i=7;i>=0;i--){z=(z<<1)^((z>>>7)*0x11D);z^=((y>>>i)&1)*x;}return z;}

 private void drawCodewords(byte[] data){
  int i=0;for(int right=size-1;right>=1;right-=2){if(right==6)right=5;for(int vert=0;vert<size;vert++)for(int j=0;j<2;j++){int x=right-j,y=((right+1)&2)==0?size-1-vert:vert;if(!function[y][x]&&i<data.length*8){modules[y][x]=bit(data[i>>>3],7-(i&7));i++;}}}
 }
 private void mask(int m){for(int y=0;y<size;y++)for(int x=0;x<size;x++){if(function[y][x])continue;boolean flip;switch(m){case 0:flip=(x+y)%2==0;break;case 1:flip=y%2==0;break;case 2:flip=x%3==0;break;case 3:flip=(x+y)%3==0;break;case 4:flip=(x/3+y/2)%2==0;break;case 5:flip=x*y%2+x*y%3==0;break;case 6:flip=(x*y%2+x*y%3)%2==0;break;default:flip=((x+y)%2+x*y%3)%2==0;}if(flip)modules[y][x]=!modules[y][x];}}

 /** Standard mask penalty: long runs, 2x2 blocks, finder look-alikes and dark/light balance. */
 private long penalty(){
  long p=0;int dark=0;
  for(int a=0;a<size;a++)for(int pass=0;pass<2;pass++){int run=1;for(int b=1;b<=size;b++){boolean same=b<size&&at(pass,a,b)==at(pass,a,b-1);if(same)run++;else{if(run>=5)p+=run-2;run=1;}}
   for(int b=0;b+11<=size;b++){if(finderLike(pass,a,b,false)||finderLike(pass,a,b,true))p+=40;}}
  for(int y=0;y<size;y++)for(int x=0;x<size;x++){if(modules[y][x])dark++;if(x+1<size&&y+1<size){boolean c=modules[y][x];if(c==modules[y][x+1]&&c==modules[y+1][x]&&c==modules[y+1][x+1])p+=3;}}
  int total=size*size;p+=((Math.abs(dark*20L-total*10L)+total-1)/total-1)*10;return p;
 }
 private boolean at(int pass,int a,int b){return pass==0?modules[a][b]:modules[b][a];}
 private static final boolean[] FINDER={true,false,true,true,true,false,true,false,false,false,false};
 private boolean finderLike(int pass,int a,int b,boolean reversed){for(int i=0;i<11;i++)if(at(pass,a,b+i)!=FINDER[reversed?10-i:i])return false;return true;}
}
