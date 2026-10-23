package kik.i18n;

/** Decoder memory stays bounded and TV thumbnails are not over-decoded. */
public final class ImageDecodeCheck {
 public static void main(String[] args){
  int checks=0;
  for(int[] image:new int[][]{{800,1200},{3840,2160},{12000,18000},{100,200000},{200000,100},{1,1},{Integer.MAX_VALUE,Integer.MAX_VALUE}}){
   for(int[] target:new int[][]{{136,204},{960,324},{640,259}}){
    int sample=ImageDecodePolicy.sample(image[0],image[1],target[0],target[1]);
    if(sample<1||(sample&(sample-1))!=0)throw new AssertionError("Invalid decoder sampling");
    if((long)Math.max(1,image[0]/sample)*Math.max(1,image[1]/sample)>4L*1024*1024)throw new AssertionError("Unbounded bitmap allocation");
    checks++;
   }
  }
  if(ImageDecodePolicy.sample(800,1200,136,204)!=4)throw new AssertionError("Poster is over-decoded");
  try{ImageDecodePolicy.sample(0,100,136,204);throw new AssertionError("Invalid dimensions accepted");}catch(IllegalArgumentException expected){}
  System.out.println("Image decoding passed: "+(checks+2)+" memory and resolution checks");
 }
}
