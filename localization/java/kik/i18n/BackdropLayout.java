package kik.i18n;

/** Fits the complete artwork to the right of the text without cropping or stretching it. */
final class BackdropLayout {
 final float left,top,width,height;
 private BackdropLayout(float left,float top,float width,float height){this.left=left;this.top=top;this.width=width;this.height=height;}
 static BackdropLayout fit(int sourceWidth,int sourceHeight,int outputWidth,int outputHeight){
  if(sourceWidth<=0||sourceHeight<=0||outputWidth<=0||outputHeight<=0)throw new IllegalArgumentException("Invalid artwork dimensions");
  float areaLeft=outputWidth*0.36f,areaRight=outputWidth*0.98f,areaTop=outputHeight*0.04f,areaBottom=outputHeight*0.98f;
  float scale=Math.min((areaRight-areaLeft)/sourceWidth,(areaBottom-areaTop)/sourceHeight);
  float width=sourceWidth*scale,height=sourceHeight*scale;
  return new BackdropLayout(areaRight-width,areaTop+(areaBottom-areaTop-height)/2f,width,height);
 }
}
