import kik.i18n.QrCode;
/** Structural checks for the TV pairing QR encoder; `java QrCheck <text>` prints the matrix for a decoder round-trip. */
public final class QrCheck {
 public static void main(String[] args){
  if(args.length>0){QrCode q=QrCode.encode(args[0]);StringBuilder s=new StringBuilder();for(int y=0;y<q.size;y++){for(int x=0;x<q.size;x++)s.append(q.dark(x,y)?'1':'0');s.append('\n');}System.out.print(s);return;}
  String url="https://kik-web.yurchornyi.workers.dev/pair?code=ABCD2345";
  QrCode q=QrCode.encode(url);
  if(q.size!=33)throw new AssertionError("Pairing URL should fit version 4-M, got size "+q.size);
  for(int[] c:new int[][]{{0,0},{q.size-7,0},{0,q.size-7}})for(int dy=0;dy<7;dy++)for(int dx=0;dx<7;dx++){int d=Math.max(Math.abs(dx-3),Math.abs(dy-3));if(q.dark(c[0]+dx,c[1]+dy)!=(d!=2))throw new AssertionError("Finder pattern");}
  if(!q.dark(8,q.size-8))throw new AssertionError("Dark module");
  int a=0,b=0;for(int i=0;i<=5;i++)a|=bit(q,8,i)<<i;a|=bit(q,8,7)<<6|bit(q,8,8)<<7|bit(q,7,8)<<8;for(int i=9;i<15;i++)a|=bit(q,14-i,8)<<i;
  for(int i=0;i<8;i++)b|=bit(q,q.size-1-i,8)<<i;for(int i=8;i<15;i++)b|=bit(q,8,q.size-15+i)<<i;
  if(a!=b)throw new AssertionError("Format copies differ");
  int f=a^0x5412;if((f>>>13)!=0)throw new AssertionError("Format must be level M");int rem=f>>>10;for(int i=0;i<10;i++)rem=(rem<<1)^((rem>>>9)*0x537);if((f>>>10<<10|rem)!=f)throw new AssertionError("Format BCH");
  if(QrCode.encode("x").size!=21)throw new AssertionError("Short text should be version 1");
  if(QrCode.encode(repeat(200)).size!=57)throw new AssertionError("200 bytes should be version 10");
  try{QrCode.encode(repeat(300));throw new AssertionError("Overflow must be rejected");}catch(IllegalArgumentException expected){}
  System.out.println("QR checks passed");
 }
 private static int bit(QrCode q,int x,int y){return q.dark(x,y)?1:0;}
 private static String repeat(int n){StringBuilder s=new StringBuilder();for(int i=0;i<n;i++)s.append('a');return s.toString();}
}
