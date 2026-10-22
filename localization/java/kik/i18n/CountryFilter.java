package kik.i18n;
import java.util.*;

/** Hides catalogue titles from countries the viewer excluded in settings. Source country names are Russian. */
public final class CountryFilter {
 /** {source name, Ukrainian label, aliases...}; the first four are excluded by default. */
 static final String[][] CHOICES={
  {"Россия","Росія","Российская Федерация"},{"СССР","СРСР"},{"Беларусь","Білорусь","Белоруссия"},{"Казахстан","Казахстан"},
  {"Узбекистан","Узбекистан"},{"Индия","Індія"},{"Турция","Туреччина"},{"Китай","Китай"},{"Южная Корея","Південна Корея","Корея Южная"},{"Япония","Японія"}};
 static final String DEFAULT="Россия,СССР,Беларусь,Казахстан";
 private static volatile Set<String> excluded;
 static Set<String> parse(String value){Set<String> out=new LinkedHashSet<String>();if(value!=null)for(String s:value.split(","))if(!s.trim().isEmpty())out.add(s.trim());return out;}
 static Set<String> excluded(){
  Set<String> e=excluded;if(e!=null)return e;
  String value=DEFAULT;try{if(UiRuntime.app!=null)value=UiRuntime.app.getSharedPreferences("settings",0).getString("excluded_countries",DEFAULT);}catch(Throwable ignored){}
  return excluded=parse(value);
 }
 static void set(Set<String> value){excluded=new LinkedHashSet<String>(value);}
 static String signature(){return String.join(",",excluded());}
 /** Card info looks like "2014, США, Фантастика"; film pages give "США, Великобритания". */
 static boolean blocked(String countries){return blocked(countries,excluded());}
 static boolean blocked(String countries,Set<String> excluded){
  if(countries==null||excluded.isEmpty())return false;
  for(String part:countries.split(",")){String p=part.trim();for(String[] c:CHOICES)if(excluded.contains(c[0]))for(int i=0;i<c.length;i++)if(i!=1&&c[i].equalsIgnoreCase(p))return true;if(excluded.contains(p))return true;}
  return false;
 }
}
