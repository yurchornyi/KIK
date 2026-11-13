package kik.i18n;

/**
 * The holidays KIK dresses up for, counted on the TV itself, the same days as the web (web/public/holidays.js). On the day:
 * the holiday logo, the holiday intro before a video and a few quiet touches on Home. A week or so before and on the day: a
 * shelf of films for it. Easter comes twice when the Western and the Orthodox dates differ. Everyone gets them; only the
 * owner's admin panel turns them off for all. No Android here, so scripts/check-tv.sh can test it (scripts/HolidayCheck.java).
 */
public final class Holidays {
 public static final String NICK="nick",XMAS="xmas",NEW_YEAR="ny",VALENTINE="val",EASTER="easter",INDEPENDENCE="ind",HALLOWEEN="hw";
 static final String[] ALL={NICK,XMAS,NEW_YEAR,VALENTINE,EASTER,INDEPENDENCE,HALLOWEEN};
 /** Days before a holiday its film shelf comes; St Nicholas, Christmas and New Year share December between them. */
 public static final int SHELF_DAYS=7;
 private Holidays(){}

 /** Days since 1970-01-01 for a calendar date (proleptic Gregorian), no time zones involved. */
 public static long dayNo(int y,int m,int d){y-=m<=2?1:0;long era=(y>=0?y:y-399)/400;long yoe=y-era*400;long doy=(153L*(m+(m>2?-3:9))+2)/5+d-1;long doe=yoe*365+yoe/4-yoe/100+doy;return era*146097+doe-719468;}

 /** Western (Gregorian) Easter Sunday: {month, day}. */
 public static int[] westernEaster(int y){int a=y%19,b=y/100,c=y%100,d=b/4,e=b%4,f=(b+8)/25,g=(b-f+1)/3,h=(19*a+b-d-g+15)%30,i=c/4,k=c%4,l=(32+2*e+2*i-h-k)%7,m=(a+11*h+22*l)/451,n=h+l-7*m+114;return new int[]{n/31,n%31+1};}
 /** Orthodox (Julian) Easter Sunday in the Gregorian calendar, 1900–2099: {month, day}. */
 public static int[] orthodoxEaster(int y){int a=y%4,b=y%7,c=y%19,d=(19*c+15)%30,e=(2*a+4*b-d+34)%7,n=d+e+114;long day=dayNo(y,n/31,n%31+1)+13;return monthDay(day);}
 private static int[] monthDay(long z){z+=719468;long era=(z>=0?z:z-146096)/146097;long doe=z-era*146097;long yoe=(doe-doe/1460+doe/36524-doe/146096)/365;long doy=doe-(365*yoe+yoe/4-yoe/100);long mp=(5*doy+2)/153;int d=(int)(doy-(153*mp+2)/5+1);int m=(int)(mp<10?mp+3:mp-9);return new int[]{m,d};}

 /** The days of a holiday whose first day falls in year y, as {first, last} day numbers. */
 private static long[][] days(String id,int y){
  switch(id){
   case NICK:return new long[][]{{dayNo(y,12,6),dayNo(y,12,6)}};
   case XMAS:return new long[][]{{dayNo(y,12,24),dayNo(y,12,26)}};
   case NEW_YEAR:return new long[][]{{dayNo(y,12,31),dayNo(y+1,1,1)}};
   case VALENTINE:return new long[][]{{dayNo(y,2,14),dayNo(y,2,14)}};
   case EASTER:{int[] w=westernEaster(y),o=orthodoxEaster(y);long a=dayNo(y,w[0],w[1]),b=dayNo(y,o[0],o[1]);return a==b?new long[][]{{a,a}}:new long[][]{{a,a},{b,b}};}
   case INDEPENDENCE:return new long[][]{{dayNo(y,8,24),dayNo(y,8,24)}};
   case HALLOWEEN:return new long[][]{{dayNo(y,10,31),dayNo(y,10,31)}};
   default:return new long[0][];
  }
 }
 /** When a holiday's film shelf shows. */
 private static long[][] shelfDays(String id,int y){
  if(NICK.equals(id))return new long[][]{{dayNo(y,12,1),dayNo(y,12,6)}};
  if(XMAS.equals(id))return new long[][]{{dayNo(y,12,7),dayNo(y,12,26)}};
  if(NEW_YEAR.equals(id))return new long[][]{{dayNo(y,12,27),dayNo(y+1,1,7)}};
  long[][] d=days(id,y);for(long[] r:d)r[0]-=SHELF_DAYS;return d;
 }
 private static String find(boolean shelf,int y,int m,int d){
  long t=dayNo(y,m,d);
  for(String id:ALL)for(int yy=y-1;yy<=y;yy++)for(long[] r:shelf?shelfDays(id,yy):days(id,yy))if(t>=r[0]&&t<=r[1])return id;
  return null;
 }
 /** Today's holiday (logo, intro, decorations), or null. */
 public static String on(int y,int m,int d){return find(false,y,m,d);}
 /** The holiday whose films the home shelf offers today, or null. */
 public static String shelfOn(int y,int m,int d){return find(true,y,m,d);}

 /** The greeting over the hero on the day. */
 public static String greeting(String id,int year,int month){
  if(id==null)return "";
  switch(id){
   case NICK:return "Зі святом Миколая";
   case XMAS:return "З Різдвом Христовим";
   case NEW_YEAR:return "З Новим "+(month==12?year+1:year)+" роком";
   case VALENTINE:return "З Днем закоханих";
   case EASTER:return "Христос воскрес!";
   case INDEPENDENCE:return "З Днем Незалежності";
   case HALLOWEEN:return "Щасливого Хелловіну";
   default:return "";
  }
 }
 /** The film shelf's title. */
 public static String shelfTitle(String id){
  if(id==null)return "";
  switch(id){
   case NICK:return "Фільми на Миколая";
   case XMAS:return "Різдвяні фільми";
   case NEW_YEAR:return "Новорічні фільми";
   case VALENTINE:return "Фільми про кохання";
   case EASTER:return "Фільми на Великдень";
   case INDEPENDENCE:return "Українське кіно";
   case HALLOWEEN:return "Фільми на Хелловін";
   default:return "";
  }
 }
 /** The holiday's accent, 0xAARRGGBB: the greeting and the focus ring. 0 for none. */
 public static int accent(String id){
  if(id==null)return 0;
  switch(id){
   case NICK:return 0xFFF0B54A;
   case XMAS:return 0xFFF6D37A;
   case NEW_YEAR:return 0xFFF5C451;
   case VALENTINE:return 0xFFFF4D6D;
   case EASTER:return 0xFFE8B25C;
   case INDEPENDENCE:return 0xFFFFD500;
   case HALLOWEEN:return 0xFFFF7A1A;
   default:return 0;
  }
 }
}
