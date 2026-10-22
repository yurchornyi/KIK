package kik.i18n;

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
  System.out.println("Catalogue checks passed: 9");
 }
 private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
