package kik.i18n;

/** Decoder sampling for posters and backdrops: never more pixels than shown, never over 4 megapixels, even for an extreme remote image. */
final class ImageDecodePolicy {
 private ImageDecodePolicy(){}
 static int sample(int sourceWidth,int sourceHeight,int targetWidth,int targetHeight){
  if(sourceWidth<=0||sourceHeight<=0||targetWidth<=0||targetHeight<=0)throw new IllegalArgumentException("Invalid image dimensions");
  int sample=1;
  while(sample<1<<29&&sourceWidth/(sample*2)>=targetWidth&&sourceHeight/(sample*2)>=targetHeight)sample*=2;
  while(sample<1<<29&&((sourceWidth+(long)sample-1)/sample)*((sourceHeight+(long)sample-1)/sample)>4L*1024*1024)sample*=2;
  return sample;
 }
}
