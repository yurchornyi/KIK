package kik.i18n;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * KIK's titles for Android's search (the Android TV search screen, Assistant on TVs that still ask apps, any app
 * reading searchable providers): the catalogue's matches for the words, named in Ukrainian, with poster and year.
 * Choosing one opens its KIK page through OpenTitleActivity (kik.intent.action.OPEN_TITLE with the title's link).
 *   content://kik.app.search/search_suggest_query/<words>
 */
public final class SearchProvider extends ContentProvider {

 private static final String[] COLUMNS={"_id",SearchManager.SUGGEST_COLUMN_TEXT_1,SearchManager.SUGGEST_COLUMN_TEXT_2,
  SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,SearchManager.SUGGEST_COLUMN_ICON_1,SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR,
  SearchManager.SUGGEST_COLUMN_CONTENT_TYPE,SearchManager.SUGGEST_COLUMN_IS_LIVE,SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
  SearchManager.SUGGEST_COLUMN_INTENT_DATA,SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA,SearchManager.SUGGEST_COLUMN_QUERY};

 @Override public boolean onCreate(){return true;}

 @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
  MatrixCursor out=new MatrixCursor(COLUMNS);
  String words=uri.getPathSegments().size()>1?uri.getLastPathSegment():null;
  if(words==null||SearchManager.SUGGEST_URI_PATH_QUERY.equals(words))words=args!=null&&args.length>0?args[0]:null;
  words=words==null?"":words.trim();
  if(words.length()<2)return out;
  if(UiRuntime.app==null)UiRuntime.app=getContext().getApplicationContext();
  int limit=10;try{limit=Math.max(1,Math.min(20,Integer.parseInt(uri.getQueryParameter("limit"))));}catch(Exception ignored){}
  List<Site.Card> cards;
  try{cards=Site.search(words,1);}catch(Exception e){android.util.Log.w("KIK.Search","search failed: "+e);return out;}
  List<String> names=new ArrayList<String>();for(Site.Card c:cards)if(c.title!=null)names.add(c.title);
  Map<String,String> uk=TitleTranslate.resolve(names,Ui.TRANSLATE_WAIT);
  int id=0;
  for(Site.Card c:cards){
   if(id>=limit)break;
   if(c.title==null||!RecoveryPolicy.title(c.link))continue;
   String title=uk.get(c.title);if(title==null)title=TitleTranslate.shown(c.title);
   String year=c.year();String genre=c.genre();String g=genre==null?null:uk.get(genre);
   int y=0;try{if(year!=null)y=Integer.parseInt(year.substring(0,4));}catch(Exception ignored){}
   out.addRow(new Object[]{id++,title,Ui.join(year,g!=null?g:genre),c.poster,c.poster,y>0?y:null,"video/*",0,
    OpenTitleActivity.ACTION,c.link,title,words});
  }
  return out;
 }

 @Override public String getType(Uri uri){return SearchManager.SUGGEST_MIME_TYPE;}
 @Override public Uri insert(Uri uri,ContentValues v){return null;}
 @Override public int delete(Uri uri,String s,String[] a){return 0;}
 @Override public int update(Uri uri,ContentValues v,String s,String[] a){return 0;}
}
