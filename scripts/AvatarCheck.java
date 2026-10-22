package kik.i18n;
import java.util.*;

/** Checks the avatar catalog without starting Android or touching viewer data. */
public final class AvatarCheck {
 public static void main(String[] args){
  Set<String> ids=new HashSet<String>(),labels=new HashSet<String>();
  for(int i=0;i<Avatars.count();i++){String id=Avatars.idAt(i);if(!Avatars.valid(id)||!ids.add(id)||Avatars.label(id).isEmpty()||!labels.add(Avatars.label(id)))throw new AssertionError("Duplicate or unnamed avatar");}
  if(ids.size()!=12||Avatars.valid(null)||Avatars.valid("unknown"))throw new AssertionError("Invalid catalog");
  for(String profile:new String[]{null,"default","12345678","old-profile","профіль"})if(!Avatars.valid(Avatars.fallback(profile)))throw new AssertionError("Old profile has no fallback");
  if(!Avatars.valid(Avatars.idAt(Integer.MIN_VALUE)))throw new AssertionError("Negative identity is invalid");
  System.out.println("Avatar catalog passed: 12 distinct characters, labels and legacy fallbacks");
 }
}
