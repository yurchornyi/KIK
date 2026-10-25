package kik.i18n;
import java.util.*;

/** Regression checks for the markup variants observed during projector testing. */
public final class CatalogueCheck {
 public static void main(String[] args){
  String table="<h1>Test series</h1><table class=\"b-post__info\">"
   +"<tr><td class=\"l\"><h2>Жанр</h2>:</td><td><span itemprop=\"genre\">Драмы</span>, <span itemprop=\"genre\">Триллеры</span></td></tr>"
   +"<tr><td class=\"l\"><h2>В переводе</h2>:</td><td>AniDUB</td></tr>"
   +"<tr><td class=\"l\"><h2>Время</h2>:</td><td itemprop=\"duration\">60 мин.</td></tr>"
   +"<tr><td class=\"l\"><h2>Из серии</h2>:</td><td>Collection tags</td></tr></table>";
  Site.Film series=Site.parseFilm("https://example.com/series/test",table
   +"<script>initCDNSeriesEvents(93305, 18, 1, 3, false, 'example.com');</script>"
   +"<li data-tab_id=\"1\">Season 1</li><li data-season_id=\"1\" data-episode_id=\"1\">Episode 1</li>");
  require(series.series&&"93305".equals(series.id)&&"18".equals(series.defaultVoice),"Series init containing a boolean");
  require(series.voices.size()==1&&"AniDUB".equals(series.voices.get(0).name),"Single-voice fallback");
  require("60 мин.".equals(series.duration)&&series.genres.size()==2&&!series.genres.contains("Collection tags"),"Attributed duration cell and genre isolation");
  require(series.seasons.size()==1&&series.episodes.size()==1,"Series episodes");
  Site.Film movie=Site.parseFilm("https://example.com/films/test",table+"initCDNMoviesEvents(55, 7, 1, 0, 1);");
  require(!movie.series&&"7".equals(movie.defaultVoice)&&movie.defaultCamrip&&movie.defaultDirector&&!movie.defaultAds,"Movie flags");
  Site.Film unavailable=Site.parseFilm("https://example.com/films/empty","<h1>Unavailable</h1>");
  require(unavailable.voices.isEmpty(),"No fabricated stream for unavailable titles");
  Site.Film linked=Site.parseFilm("https://example.com/series/test",table+"initCDNSeriesEvents(646, 565, 1, 1, false);"
   +"<ul id=\"translators-list\"><li><a title=\"Dub\" data-translator_id=\"56\">Dub</a></li><li><a title=\"Dub\" data-translator_id=\"56\">Dub</a></li><li><a title=\"UA\" data-translator_id=\"359\">UA</a></li></ul>"
   +"<a data-tab_id=\"1\">Season 1</a><a data-tab_id=\"2\">Season 2</a><a data-season_id=\"1\" data-episode_id=\"1\">Episode 1</a><a data-season_id=\"2\" data-episode_id=\"1\">Episode 1</a>");
  require(linked.voices.size()==2&&"359".equals(linked.voices.get(1).id),"Linked translators and deduplication");
  require(linked.seasons.size()==2&&linked.episodes.size()==2,"Linked seasons and episodes");
  Site.Film people=Site.parseFilm("/films/drama/test.html","<table class=\"b-post__info\"><tr><td class=\"l\"><h2>Режиссер</h2>:</td><td><a href=\"/person/1\">Director One</a></td></tr><tr><td colspan=\"2\"><span itemprop=\"actor\" itemscope><a><span itemprop=\"name\">Actor One</span></a></span><span itemprop=\"actor\" itemscope><a><span itemprop=\"name\">Actor Two</span></a></span></td></tr></table>");
  require(people.people.contains("Director One")&&people.people.contains("Actor One")&&people.people.contains("Actor Two"),"Director and colspan actor metadata");
  String card="<div class=\"b-content__inline_item\" data-id=\"%s\" data-url=\"https://mirror.example/films/fiction/%s-x.html\"> <div class=\"b-content__inline_item-link\"> <a href=\"#\">%s</a> <div class=\"misc\">2014, США, Фантастика</div></div></div>";
  Site.Film related=Site.parseFilm("https://hdrezka.sh/films/fiction/1-x.html","<h1>X</h1><ul id=\"translators-list\"><li title=\"Оригинал (+субтитры)\" data-translator_id=\"1\">o</li><li title=\"Дубляж\" data-translator_id=\"2\">d</li><li title=\"Украинский многоголосый \" data-translator_id=\"3\">u <img alt=\"Украинский\"></li></ul><div class=\"b-sidelist\">"+String.format(card,"5","5","Солярис")+String.format(card,"1","1","X")+"</div>");
  if(!"3".equals(related.voices.get(0).id)||!"2".equals(related.voices.get(1).id)||!"1".equals(related.voices.get(2).id))throw new AssertionError("Voices ordered Ukrainian, Russian, original");
  if(related.related.size()!=1||!related.related.get(0).link.startsWith("https://hdrezka.sh/")||!"2014, США, Фантастика".equals(related.related.get(0).info))throw new AssertionError("See-also titles parsed on a trusted host without the film itself");
  List<Site.Card> found=new ArrayList<Site.Card>();for(String t:new String[]{"Мир Гарри Поттера","Гарри Поттер и Тайная комната","Гарри Поттер"}){Site.Card c=new Site.Card();c.title=t;c.info="2001, США";found.add(c);}
  List<Site.Card> ranked=Site.relevant("гаррі поттер",found);
  if(!"Гарри Поттер".equals(ranked.get(0).title)||!"Мир Гарри Поттера".equals(ranked.get(2).title))throw new AssertionError("Search relevance: exact, prefix, then other matches");
  if(!"гарри поттер".equals(Site.searchForm("Гаррі Поттер")))throw new AssertionError("Ukrainian letters fallback");
  String page=String.format(card,"7","7","Брат")+String.format(card,"8","8","Interstellar");page=page.replaceFirst("2014, США","1997, Россия");
  List<Site.Card> visible=Site.cards(page);
  if(visible.size()!=1||!"Interstellar".equals(visible.get(0).title)||Site.lastRaw()!=2)throw new AssertionError("Default filter hides Russian titles but keeps paging info");
  if(!CountryFilter.blocked("СССР, Казахстан")||CountryFilter.blocked("Украина, США")||CountryFilter.blocked("Россия",new HashSet<String>()))throw new AssertionError("Country matching");
  require(Site.nameScore("шерлак","Шерлок")>=.6&&Site.nameScore("відьмак","Ведьмак")>=.6&&Site.nameScore("интерстелар","Интерстеллар")>=.6&&Site.nameScore("пиратыкарибского моря","Пираты Карибского моря: На краю Света")>=.6,"Typos, Ukrainian letters and joined words still match");
  require(Site.nameScore("брейкінг бед","Бедуин")<.6&&Site.nameScore("кот","Кит")<.6&&Site.distance("шерлко","шерлок",3)==1,"Unrelated titles that share a word do not count as a match");
  require(Site.blocked("<h1>ОШИБКА ДОСТУПА</h1>Нами была обнаружена подозрительная активность с вашего IP-адреса")&&!Site.blocked(table),"The catalogue's IP ban page is recognised, a film page is not");
  require(Site.blocked(new java.io.IOException(new java.io.IOException(Site.IP_BLOCKED)))&&!Site.blocked(new java.io.IOException("HTTP 500")),"A ban survives the film loader's exception wrapping");
  require(Site.pauseReason(403,"Нами была обнаружена подозрительная активность с вашего IP-адреса").equals(Site.IP_BLOCKED)
   &&Site.pauseReason(429,"Too many requests").equals(Site.ACCESS_PAUSED)
   &&Site.pauseReason(200,"anubis_challenge")==null&&Site.pauseReason(403,"<script id=\"anubis_challenge\">")==null
   &&Site.pauseReason(200,table)==null,"Bans and rate limits pause all mirrors; the routine bot check is solved, not a pause");
  require(Site.retryAfterMs("120")==120000&&Site.retryAfterMs("86401")==24*60*60_000L&&Site.retryAfterMs("-10")==0,"Retry-After seconds are bounded");
  System.out.println("Catalogue checks passed: 21");
 }
 private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
