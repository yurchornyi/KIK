package kik.i18n;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Opens a film or series by its name, for other apps (the TV launcher's posters, Android's search): finds it in the
 * catalogue and goes straight to its page in the new KIK screens. The request carries the title, and may carry the
 * original title and the year, which pick the right one among titles that share a name. Without a sure match the
 * KIK search opens with the name already typed, so nothing ever lands in the old HDRezka screen.
 *   action kik.intent.action.OPEN_TITLE (or android.intent.action.SEARCH)
 *   extras query (SearchManager.QUERY), kik.original, kik.year
 * or, from Android's search (SearchProvider), the title's link as the intent data.
 */
public final class OpenTitleActivity extends Activity {

 public static final String ACTION="kik.intent.action.OPEN_TITLE",ORIGINAL="kik.original",YEAR="kik.year";
 /** From the home-screen channels: the exact title, its profile, and whether to start or resume playback. */
 public static final String LINK="kik.link",POSTER="kik.poster",PROFILE="kik.profile",PLAY="kik.play",SEASON="kik.season",EPISODE="kik.episode";
 private volatile boolean gone;

 @Override public void onCreate(Bundle state){
  super.onCreate(state);if(Account.gate(this))return;Ui.screen(this);
  Intent in=getIntent();
  String link=clean(in.getStringExtra(LINK));
  // Android's search (SearchProvider): the chosen title's link as the data, its name as the extra data.
  String chosen=null;if(link.isEmpty()&&in.getData()!=null){link=clean(in.getDataString());chosen=in.getStringExtra(SearchManager.EXTRA_DATA_KEY);}
  if(RecoveryPolicy.title(link)){
   ProfileStore.use(this,in.getStringExtra(PROFILE));
   startActivity(new Intent(this,FilmActivity.class).putExtra("link",link).putExtra("title",chosen!=null?chosen:in.getStringExtra(SearchManager.QUERY)).putExtra("poster",in.getStringExtra(POSTER))
    .putExtra("play",in.getBooleanExtra(PLAY,false)).putExtra("season",in.getStringExtra(SEASON)).putExtra("episode",in.getStringExtra(EPISODE)));
   overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);finish();return;
  }
  final String title=clean(in.getStringExtra(SearchManager.QUERY));
  final String original=clean(in.getStringExtra(ORIGINAL));
  final String year=clean(in.getStringExtra(YEAR));
  if(title.isEmpty()&&original.isEmpty()){startActivity(new Intent(this,HomeActivity.class));finish();return;}
  LinearLayout box=new LinearLayout(this);box.setOrientation(1);box.setGravity(Gravity.CENTER);box.setBackgroundColor(Ui.BG);
  box.addView(Brand.logo(this,96,44));
  TextView t=Ui.text(this,"Шукаю «"+(title.isEmpty()?original:title)+"»…",20,Ui.GREY);t.setGravity(Gravity.CENTER);t.setPadding(0,Ui.dp(this,22),0,0);box.addView(t);
  setContentView(box);
  new Thread(()->{
   Site.Card found=null;
   List<String> queries=new ArrayList<String>();
   if(!title.isEmpty())queries.add(title);
   if(!original.isEmpty()&&!same(original,title))queries.add(original);
   for(String q:queries){
    try{found=best(Site.search(q,1),title,original,year);}catch(Exception ignored){}
    if(found!=null)break;
   }
   final Site.Card hit=found;
   Ui.MAIN.post(()->{
    if(gone)return;
    if(hit!=null)FilmActivity.open(this,hit.link,hit.title,hit.poster);
    else startActivity(new Intent(this,BrowseActivity.class).putExtra(BrowseActivity.MODE,"search").putExtra(BrowseActivity.TITLE,"Пошук").putExtra(BrowseActivity.QUERY,title.isEmpty()?original:title));
    overridePendingTransition(android.R.anim.fade_in,android.R.anim.fade_out);
    finish();
   });
  },"kik-open-title").start();
 }

 @Override protected void onDestroy(){gone=true;super.onDestroy();}

 /**
  * The card that is surely the one asked for: the name matches (the title or the original, letters only), and the
  * year matches when one was given. The only result, with the right year, counts too, since the catalogue often
  * names a film in Russian while the launcher asks in Ukrainian. Otherwise none: a search with the name is better
  * than the wrong film.
  */
 static Site.Card best(List<Site.Card> cards,String title,String original,String year){
  if(cards==null||cards.isEmpty())return null;
  // The catalogue names titles in Russian; KIK shows them in Ukrainian through its translation cache. The name
  // asked for is Ukrainian, so it is matched against that translation too ("Мстители" is "Месники").
  java.util.Map<String,String> uk=new java.util.HashMap<String,String>();
  try{List<String> names=new ArrayList<String>();for(Site.Card c:cards)if(c.title!=null)names.add(c.title);uk=TitleTranslate.resolve(names);}catch(Exception ignored){}
  Site.Card byYear=null;
  for(Site.Card c:cards){
   boolean yearOk=year.isEmpty()||(c.year()!=null&&c.year().startsWith(year));
   boolean name=same(c.title,title)||same(c.title,original)||same(uk.get(c.title),title);
   if(name&&yearOk)return c;
   if(yearOk&&!year.isEmpty()&&byYear==null)byYear=c;
  }
  return cards.size()==1?byYear:null;
 }

 static boolean same(String a,String b){String x=key(a),y=key(b);return !x.isEmpty()&&x.equals(y);}

 /** Letters and digits only, lower case, ё as е and і/ї/є/ґ as their Russian look-alikes: "Надія" = "Надия". */
 static String key(String s){
  if(s==null)return "";
  String n=Normalizer.normalize(s.toLowerCase(Locale.ROOT),Normalizer.Form.NFKC)
   .replace('ё','е').replace('ї','и').replace('і','и').replace('є','е').replace('ґ','г');
  StringBuilder b=new StringBuilder();
  for(int i=0;i<n.length();i++){char ch=n.charAt(i);if(Character.isLetterOrDigit(ch))b.append(ch);}
  return b.toString();
 }

 private static String clean(String s){return s==null?"":s.trim();}
}
