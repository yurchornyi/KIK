import kik.i18n.Holidays;
/** The TV counts the same holiday days as the web (web/holidays.test.mjs). */
public final class HolidayCheck {
 static void check(boolean v,String what){if(!v)throw new AssertionError(what);}
 static boolean md(int[] got,int m,int d){return got[0]==m&&got[1]==d;}
 static boolean eq(String a,String b){return a==null?b==null:a.equals(b);}
 public static void main(String[] args){
  check(md(Holidays.westernEaster(2026),4,5)&&md(Holidays.orthodoxEaster(2026),4,12),"Easter 2026");
  check(md(Holidays.westernEaster(2027),3,28)&&md(Holidays.orthodoxEaster(2027),5,2),"Easter 2027");
  check(md(Holidays.westernEaster(2028),4,16)&&md(Holidays.orthodoxEaster(2028),4,16),"Easter 2028, one day");
  check(md(Holidays.westernEaster(2025),4,20)&&md(Holidays.orthodoxEaster(2025),4,20),"Easter 2025, one day");
  check(md(Holidays.westernEaster(2029),4,1)&&md(Holidays.orthodoxEaster(2029),4,8),"Easter 2029");
  check(Holidays.dayNo(1970,1,1)==0&&Holidays.dayNo(2027,1,1)-Holidays.dayNo(2026,12,31)==1&&Holidays.dayNo(2028,3,1)-Holidays.dayNo(2028,2,28)==2,"day numbers");
  // On the day: logo, intro, decorations.
  check(eq(Holidays.on(2026,12,6),Holidays.NICK)&&Holidays.on(2026,12,5)==null,"St Nicholas");
  for(int d=24;d<=26;d++)check(eq(Holidays.on(2026,12,d),Holidays.XMAS),"Christmas "+d);
  check(Holidays.on(2026,12,27)==null,"after Christmas");
  check(eq(Holidays.on(2026,12,31),Holidays.NEW_YEAR)&&eq(Holidays.on(2027,1,1),Holidays.NEW_YEAR)&&Holidays.on(2027,1,2)==null,"New Year across the years");
  check(eq(Holidays.on(2027,2,14),Holidays.VALENTINE),"Valentine's");
  check(eq(Holidays.on(2027,3,28),Holidays.EASTER)&&eq(Holidays.on(2027,5,2),Holidays.EASTER)&&Holidays.on(2027,4,15)==null,"two Easters");
  check(eq(Holidays.on(2028,4,16),Holidays.EASTER),"one Easter");
  check(eq(Holidays.on(2027,8,24),Holidays.INDEPENDENCE)&&Holidays.on(2027,8,23)==null,"Independence Day");
  check(eq(Holidays.on(2026,10,31),Holidays.HALLOWEEN)&&Holidays.on(2026,11,13)==null,"Halloween");
  // The film shelf: a week before to the last day; December is shared.
  check(Holidays.shelfOn(2026,11,30)==null&&eq(Holidays.shelfOn(2026,12,1),Holidays.NICK)&&eq(Holidays.shelfOn(2026,12,6),Holidays.NICK),"St Nicholas films");
  check(eq(Holidays.shelfOn(2026,12,7),Holidays.XMAS)&&eq(Holidays.shelfOn(2026,12,26),Holidays.XMAS),"Christmas films");
  check(eq(Holidays.shelfOn(2026,12,27),Holidays.NEW_YEAR)&&eq(Holidays.shelfOn(2027,1,7),Holidays.NEW_YEAR)&&Holidays.shelfOn(2027,1,8)==null,"New Year films");
  check(eq(Holidays.shelfOn(2027,2,7),Holidays.VALENTINE)&&Holidays.shelfOn(2027,2,6)==null&&Holidays.shelfOn(2027,2,15)==null,"Valentine's films");
  check(eq(Holidays.shelfOn(2027,3,21),Holidays.EASTER)&&Holidays.shelfOn(2027,4,1)==null&&eq(Holidays.shelfOn(2027,4,25),Holidays.EASTER)&&Holidays.shelfOn(2027,5,3)==null,"Easter films twice");
  check(eq(Holidays.shelfOn(2027,8,17),Holidays.INDEPENDENCE)&&eq(Holidays.shelfOn(2026,10,24),Holidays.HALLOWEEN)&&Holidays.shelfOn(2026,10,23)==null,"Ukrainian and Halloween films");
  check(Holidays.greeting(Holidays.NEW_YEAR,2026,12).equals("З Новим 2027 роком")&&Holidays.greeting(Holidays.NEW_YEAR,2027,1).equals("З Новим 2027 роком"),"New Year greeting");
  for(String id:new String[]{Holidays.NICK,Holidays.XMAS,Holidays.NEW_YEAR,Holidays.VALENTINE,Holidays.EASTER,Holidays.INDEPENDENCE,Holidays.HALLOWEEN})
   check(!Holidays.greeting(id,2027,1).isEmpty()&&!Holidays.shelfTitle(id).isEmpty()&&Holidays.accent(id)!=0,"words for "+id);
  check(Holidays.greeting(null,2027,1).isEmpty()&&Holidays.shelfTitle(null).isEmpty()&&Holidays.accent(null)==0,"no holiday");
  System.out.println("Holiday days, Easter dates, shelves and greetings passed");
 }
}
