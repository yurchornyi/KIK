package kik.i18n;

/** Regression checks for complete hero artwork at both Home and Film sizes; no device needed. */
public final class BackdropCheck {
 public static void main(String[] args){
  int[][] sources={{740,1100},{300,450},{1920,1080},{3840,1600},{1000,1000},{100,2000},{2000,100}};
  int[][] outputs={{960,324},{960,388},{640,216},{640,259},{1920,648},{427,144}};
  int checks=0;
  for(int[] source:sources)for(int[] output:outputs){
   BackdropLayout art=BackdropLayout.fit(source[0],source[1],output[0],output[1]);
   float epsilon=0.001f;
   if(art.left<-epsilon||art.top<-epsilon||art.left+art.width>output[0]+epsilon||art.top+art.height>output[1]+epsilon)throw new AssertionError("Artwork edge was cropped");
   if(art.left<output[0]*0.36f-epsilon)throw new AssertionError("Artwork overlaps the reserved text area");
   if(Math.abs(art.width/source[0]-art.height/source[1])>epsilon)throw new AssertionError("Artwork was stretched");
   // These are the same four corners a canvas draws: the source's head/top cannot be discarded.
   if(art.width<=0||art.height<=0||art.top>=output[1]||art.left>=output[0])throw new AssertionError("Artwork disappeared");
   checks++;
  }
  System.out.println("Backdrop layouts passed: "+checks+" portrait/landscape and screen-size combinations");
 }
}
