import kik.i18n.FreshPolicy;
/** «Нове на KIK» keeps only new releases: this year's and last year's titles, a series by the year it began. */
public final class FreshCheck {
 static void check(boolean v,String what){if(!v)throw new AssertionError(what);}
 public static void main(String[] args){
  int y=2026;
  check(FreshPolicy.fresh("2026",y)&&FreshPolicy.fresh("2025",y),"this year and last year are new");
  check(!FreshPolicy.fresh("2024",y)&&!FreshPolicy.fresh("1996",y),"older titles the site has just added are not");
  check(FreshPolicy.fresh("2027",y)&&!FreshPolicy.fresh("2030",y),"next year's announced title counts, a wrong year does not");
  check(FreshPolicy.fresh("2025-...",y)&&FreshPolicy.fresh("2025-2026",y)&&FreshPolicy.fresh(" 2026 ",y),"a series that began last year is new");
  check(!FreshPolicy.fresh("2016-...",y)&&!FreshPolicy.fresh("2005-2026",y),"a long-running series with a new season is not");
  check(!FreshPolicy.fresh(null,y)&&!FreshPolicy.fresh("",y)&&!FreshPolicy.fresh("202",y)&&!FreshPolicy.fresh("abcd",y)&&!FreshPolicy.fresh("20261",y),"no year, no place");
  check(FreshPolicy.fresh("2026",2027)&&!FreshPolicy.fresh("2025",2027),"a new year moves the window");
  check(FreshPolicy.PAGES>=2&&FreshPolicy.PAGES<=3,"a row reads a few pages at most");
  System.out.println("New releases passed");
 }
}
