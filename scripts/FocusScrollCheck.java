package kik.i18n;

/** Focused cards stay visible without moving the whole shelf on every D-pad press. */
public final class FocusScrollCheck {
 public static void main(String[] args){
  int checks=0;
  for(int width:new int[]{640,960,1280,1920,3840}){
   int safe=32,card=148,margin=8;
   for(int item=0;item<5;item++){
    int left=safe+item*card,right=left+card;
    int target=FocusScrollPolicy.target(0,width,safe,safe,left,right,margin);
    if(right+margin<=width-safe&&target!=0)throw new AssertionError("Visible card caused a scroll");
    if(target+width-safe<right+margin)throw new AssertionError("Focused card was clipped");
    checks++;
   }
  }
  if(FocusScrollPolicy.target(500,960,32,32,100,248,8)!=60)throw new AssertionError("Focus did not return left");
  if(FocusScrollPolicy.target(0,0,32,32,100,248,8)!=0)throw new AssertionError("Unlaid-out shelf moved");
  System.out.println("Focus scrolling passed: "+(checks+2)+" viewport checks");
 }
}
