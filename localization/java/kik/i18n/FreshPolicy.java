package kik.i18n;

/**
 * Which titles «Нове на KIK», «Нові фільми» and «Нові серіали» on Home may show (14.11, owner: only new releases, not a
 * 1996 film the site has just added): one that came out this year or last year. A series counts by the year it began,
 * so a long-running show with a fresh season is not a new release. A card without a year is left out.
 */
public final class FreshPolicy {
 /** This year and the one before it. */
 public static final int YEARS=2;
 /** At most this many catalogue pages are read to fill one row with new releases. */
 public static final int PAGES=3;

 private FreshPolicy(){}

 /** {@code year} as a card gives it: «2025», «2024-2026», «2025-...», or null. Next year's announced titles count too. */
 public static boolean fresh(String year,int thisYear){
  if(year==null)return false;String y=year.trim();if(y.length()<4)return false;
  for(int i=0;i<4;i++)if(!Character.isDigit(y.charAt(i)))return false;
  if(y.length()>4&&Character.isDigit(y.charAt(4)))return false;
  int start=Integer.parseInt(y.substring(0,4));return start>thisYear-YEARS&&start<=thisYear+1;
 }
}
