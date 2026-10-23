package kik.i18n;
import android.net.Uri;
import android.util.Log;
import androidx.media3.common.C;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.TransferListener;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.*;

/**
 * Bypass for providers that block HDRezka in their DNS: a name the system cannot resolve is looked up over HTTPS at
 * Cloudflare, and the request goes to that address with the real name in the Host header, TLS SNI and certificate
 * check. Names the system resolves are left alone, so nothing changes on networks without a block.
 */
final class Doh {
 private static final String[] RESOLVERS={"https://1.1.1.1/dns-query","https://1.0.0.1/dns-query","https://cloudflare-dns.com/dns-query"};
 private static final class Entry{final String ip;final long until;Entry(String ip,long until){this.ip=ip;this.until=until;}}
 /** ip==null: the system resolves the name. */
 private static final Map<String,Entry> HOSTS=new ConcurrentHashMap<String,Entry>();

 /** The address to use instead of the name, or null when the system resolves it. */
 static String address(String host)throws IOException{
  if(!DohPolicy.eligible(host))return null;String key=host.toLowerCase(Locale.ROOT);long now=System.currentTimeMillis();
  Entry e=HOSTS.get(key);if(e!=null&&e.until>now)return e.ip;
  try{InetAddress.getAllByName(host);HOSTS.put(key,new Entry(null,now+10*60_000));return null;}
  catch(UnknownHostException blocked){
   DohPolicy.Answer a=lookup(host);
   if(a.addresses.isEmpty())throw blocked;
   String ip=a.addresses.get(new Random().nextInt(a.addresses.size()));HOSTS.put(key,new Entry(ip,now+a.ttlSeconds*1000));
   Log.i("KIK","DNS blocks "+host+"; using "+ip+" from Cloudflare DoH");return ip;
  }
 }
 /** True when this TV reaches the name only through the bypass. */
 static boolean used(String host){Entry e=host==null?null:HOSTS.get(host.toLowerCase(Locale.ROOT));return e!=null&&e.ip!=null&&e.until>System.currentTimeMillis();}
 private static DohPolicy.Answer lookup(String host){
  for(String resolver:RESOLVERS){
   HttpURLConnection c=null;
   try{
    c=(HttpURLConnection)new URL(resolver+"?type=A&name="+URLEncoder.encode(host,"UTF-8")).openConnection();
    c.setConnectTimeout(4000);c.setReadTimeout(4000);c.setInstanceFollowRedirects(false);c.setRequestProperty("Accept","application/dns-json");
    if(c.getResponseCode()!=200)continue;
    DohPolicy.Answer a=DohPolicy.parse(Site.read(c.getInputStream()));if(!a.addresses.isEmpty())return a;
   }catch(IOException e){Log.w("KIK","DoH "+resolver+": "+e);}
   finally{if(c!=null)c.disconnect();}
  }
  return new DohPolicy.Answer();
 }

 /** URL.openConnection() that survives a DNS block. Keep redirects manual or same-host: a new name goes through here again. */
 static HttpURLConnection open(String url)throws IOException{
  String host=new URL(url).getHost(),ip=address(host);
  if(ip==null)return (HttpURLConnection)new URL(url).openConnection();
  HttpURLConnection c=(HttpURLConnection)new URL(DohPolicy.rewrite(url,ip)).openConnection();c.setRequestProperty("Host",DohPolicy.hostHeader(url));
  if(c instanceof HttpsURLConnection){HttpsURLConnection s=(HttpsURLConnection)c;NamedFactory f=factory(host);s.setSSLSocketFactory(f);s.setHostnameVerifier(f.verifier);}
  return c;
 }

 /** The player's HTTP source with the same bypass. A blocked name is read through {@link #open}, which the player's own HTTP stack cannot do. */
 static DataSource.Factory media(DataSource.Factory upstream){return ()->new Media(upstream.createDataSource());}
 private static final class Media implements DataSource{
  private final DataSource up;private final List<TransferListener> listeners=new ArrayList<TransferListener>();private boolean own,started;private DataSpec spec;private HttpURLConnection c;private InputStream in;private Uri uri;private long left;
  Media(DataSource up){this.up=up;}
  @Override public void addTransferListener(TransferListener l){listeners.add(l);up.addTransferListener(l);}
  @Override public long open(DataSpec spec)throws IOException{
   String scheme=spec.uri.getScheme(),host=spec.uri.getHost();own=false;
   if(host==null||!("http".equals(scheme)||"https".equals(scheme))||address(host)==null)return up.open(spec);
   own=true;this.spec=spec;for(TransferListener l:listeners)l.onTransferInitializing(this,spec,true);
   try{return connect(spec);}catch(HttpDataSource.HttpDataSourceException e){throw e;}catch(IOException e){throw HttpDataSource.HttpDataSourceException.createForIOException(e,spec,HttpDataSource.HttpDataSourceException.TYPE_OPEN);}
  }
  private long connect(DataSpec spec)throws IOException{
   String url=spec.uri.toString();int code=0;
   // Redirects by hand: the next name may be blocked too.
   for(int hop=0;;hop++){
    if(hop>5)throw new HttpDataSource.HttpDataSourceException("Too many redirects",spec,androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,HttpDataSource.HttpDataSourceException.TYPE_OPEN);
    c=Doh.open(url);c.setInstanceFollowRedirects(false);c.setConnectTimeout(12000);c.setReadTimeout(20000);
    for(Map.Entry<String,String> h:spec.httpRequestHeaders.entrySet())if(!"Host".equalsIgnoreCase(h.getKey()))c.setRequestProperty(h.getKey(),h.getValue());
    c.setRequestProperty("User-Agent",Site.UA);c.setRequestProperty("Accept-Encoding","identity");
    if(spec.position>0||spec.length!=C.LENGTH_UNSET)c.setRequestProperty("Range","bytes="+spec.position+"-"+(spec.length==C.LENGTH_UNSET?"":String.valueOf(spec.position+spec.length-1)));
    code=c.getResponseCode();String next=c.getHeaderField("Location");
    if(code>=300&&code<400&&next!=null){url=new URL(new URL(url),next).toString();c.disconnect();continue;}
    break;
   }
   uri=Uri.parse(url);
   if(code<200||code>299){Map<String,List<String>> headers=c.getHeaderFields();String message=c.getResponseMessage();c.disconnect();c=null;
    throw new HttpDataSource.InvalidResponseCodeException(code,message,code==416?new IOException("Range"):null,headers,spec,new byte[0]);}
   in=c.getInputStream();long skip=code==200?spec.position:0;while(skip>0){long n=in.skip(skip);if(n<=0)throw new EOFException();skip-=n;}
   long length=c.getContentLength();if(length>=0&&code==200)length-=spec.position;
   left=spec.length!=C.LENGTH_UNSET?spec.length:length>=0?length:C.LENGTH_UNSET;
   started=true;for(TransferListener l:listeners)l.onTransferStart(this,spec,true);return left;
  }
  @Override public int read(byte[] b,int off,int len)throws IOException{
   if(!own)return up.read(b,off,len);
   if(len==0)return 0;if(left==0)return C.RESULT_END_OF_INPUT;
   int n;try{n=in.read(b,off,left==C.LENGTH_UNSET?len:(int)Math.min(len,left));}catch(IOException e){throw HttpDataSource.HttpDataSourceException.createForIOException(e,spec,HttpDataSource.HttpDataSourceException.TYPE_READ);}if(n<0)return C.RESULT_END_OF_INPUT;
   if(left!=C.LENGTH_UNSET)left-=n;for(TransferListener l:listeners)l.onBytesTransferred(this,spec,true,n);return n;
  }
  @Override public Uri getUri(){return own?uri:up.getUri();}
  @Override public Map<String,List<String>> getResponseHeaders(){return own?(c==null?Collections.<String,List<String>>emptyMap():c.getHeaderFields()):up.getResponseHeaders();}
  @Override public void close()throws IOException{
   if(!own){up.close();return;}
   try{if(in!=null)in.close();}finally{in=null;if(c!=null)c.disconnect();c=null;
    // The bandwidth meter counts open streams: end only what started.
    if(started)for(TransferListener l:listeners)l.onTransferEnd(this,spec,true);started=own=false;}
  }
 }

 // ---------- TLS under the real name ----------
 private static final SSLSocketFactory BASE=HttpsURLConnection.getDefaultSSLSocketFactory();private static final HostnameVerifier BASE_VERIFIER=HttpsURLConnection.getDefaultHostnameVerifier();
 /** One factory per name: the HTTP stack reuses a connection only with the same factory, so video segments skip a new handshake. */
 private static final Map<String,NamedFactory> FACTORIES=new ConcurrentHashMap<String,NamedFactory>();
 private static NamedFactory factory(String host){String key=host.toLowerCase(Locale.ROOT);NamedFactory f=FACTORIES.get(key);if(f==null){f=new NamedFactory(key,BASE);NamedFactory old=FACTORIES.putIfAbsent(key,f);if(old!=null)f=old;}return f;}
 /** Opens TLS with SNI for the real name; the wrapper hides the socket from code that would reset SNI to the address. */
 private static final class NamedFactory extends SSLSocketFactory{
  private final String name;private final SSLSocketFactory base;final HostnameVerifier verifier;
  NamedFactory(String name,SSLSocketFactory base){this.name=name;this.base=base;verifier=(h,session)->BASE_VERIFIER.verify(name,session);}
  @Override public String[] getDefaultCipherSuites(){return base.getDefaultCipherSuites();}
  @Override public String[] getSupportedCipherSuites(){return base.getSupportedCipherSuites();}
  @Override public Socket createSocket(Socket s,String host,int port,boolean autoClose)throws IOException{if(name.equalsIgnoreCase(host))return base.createSocket(s,host,port,autoClose);return new Named((SSLSocket)base.createSocket(s,name,port,autoClose),name);}
  @Override public Socket createSocket(String host,int port)throws IOException{return base.createSocket(host,port);}
  @Override public Socket createSocket(String host,int port,InetAddress local,int localPort)throws IOException{return base.createSocket(host,port,local,localPort);}
  @Override public Socket createSocket(InetAddress host,int port)throws IOException{return base.createSocket(host,port);}
  @Override public Socket createSocket(InetAddress a,int port,InetAddress local,int localPort)throws IOException{return base.createSocket(a,port,local,localPort);}
 }
 /** Plain delegate: without the platform's private TLS methods nobody can change SNI or ALPN after we set them. */
 private static final class Named extends SSLSocket{
  private final SSLSocket s;private final String name;Named(SSLSocket s,String name){this.s=s;this.name=name;}
  @Override public String[] getSupportedCipherSuites(){return s.getSupportedCipherSuites();}
  @Override public String[] getEnabledCipherSuites(){return s.getEnabledCipherSuites();}
  @Override public void setEnabledCipherSuites(String[] v){s.setEnabledCipherSuites(v);}
  @Override public String[] getSupportedProtocols(){return s.getSupportedProtocols();}
  @Override public String[] getEnabledProtocols(){return s.getEnabledProtocols();}
  @Override public void setEnabledProtocols(String[] v){s.setEnabledProtocols(v);}
  @Override public SSLSession getSession(){return s.getSession();}
  @Override public SSLSession getHandshakeSession(){return s.getHandshakeSession();}
  @Override public void addHandshakeCompletedListener(HandshakeCompletedListener l){s.addHandshakeCompletedListener(l);}
  @Override public void removeHandshakeCompletedListener(HandshakeCompletedListener l){s.removeHandshakeCompletedListener(l);}
  @Override public void startHandshake()throws IOException{s.startHandshake();}
  @Override public void setUseClientMode(boolean v){s.setUseClientMode(v);}
  @Override public boolean getUseClientMode(){return s.getUseClientMode();}
  @Override public void setNeedClientAuth(boolean v){s.setNeedClientAuth(v);}
  @Override public boolean getNeedClientAuth(){return s.getNeedClientAuth();}
  @Override public void setWantClientAuth(boolean v){s.setWantClientAuth(v);}
  @Override public boolean getWantClientAuth(){return s.getWantClientAuth();}
  @Override public void setEnableSessionCreation(boolean v){s.setEnableSessionCreation(v);}
  @Override public boolean getEnableSessionCreation(){return s.getEnableSessionCreation();}
  @Override public SSLParameters getSSLParameters(){return s.getSSLParameters();}
  /** The HTTP stack names the address as the TLS server name; keep the real one. */
  @Override public void setSSLParameters(SSLParameters p){p.setServerNames(Collections.<SNIServerName>singletonList(new SNIHostName(name)));s.setSSLParameters(p);}
  @Override public void connect(SocketAddress a)throws IOException{s.connect(a);}
  @Override public void connect(SocketAddress a,int timeout)throws IOException{s.connect(a,timeout);}
  @Override public void bind(SocketAddress a)throws IOException{s.bind(a);}
  @Override public InetAddress getInetAddress(){return s.getInetAddress();}
  @Override public InetAddress getLocalAddress(){return s.getLocalAddress();}
  @Override public int getPort(){return s.getPort();}
  @Override public int getLocalPort(){return s.getLocalPort();}
  @Override public SocketAddress getRemoteSocketAddress(){return s.getRemoteSocketAddress();}
  @Override public SocketAddress getLocalSocketAddress(){return s.getLocalSocketAddress();}
  @Override public InputStream getInputStream()throws IOException{return s.getInputStream();}
  @Override public OutputStream getOutputStream()throws IOException{return s.getOutputStream();}
  @Override public void setTcpNoDelay(boolean v)throws SocketException{s.setTcpNoDelay(v);}
  @Override public boolean getTcpNoDelay()throws SocketException{return s.getTcpNoDelay();}
  @Override public void setSoLinger(boolean on,int linger)throws SocketException{s.setSoLinger(on,linger);}
  @Override public int getSoLinger()throws SocketException{return s.getSoLinger();}
  @Override public void setSoTimeout(int v)throws SocketException{s.setSoTimeout(v);}
  @Override public int getSoTimeout()throws SocketException{return s.getSoTimeout();}
  @Override public void setSendBufferSize(int v)throws SocketException{s.setSendBufferSize(v);}
  @Override public int getSendBufferSize()throws SocketException{return s.getSendBufferSize();}
  @Override public void setReceiveBufferSize(int v)throws SocketException{s.setReceiveBufferSize(v);}
  @Override public int getReceiveBufferSize()throws SocketException{return s.getReceiveBufferSize();}
  @Override public void setKeepAlive(boolean v)throws SocketException{s.setKeepAlive(v);}
  @Override public boolean getKeepAlive()throws SocketException{return s.getKeepAlive();}
  @Override public void close()throws IOException{s.close();}
  @Override public void shutdownInput()throws IOException{s.shutdownInput();}
  @Override public void shutdownOutput()throws IOException{s.shutdownOutput();}
  @Override public boolean isConnected(){return s.isConnected();}
  @Override public boolean isBound(){return s.isBound();}
  @Override public boolean isClosed(){return s.isClosed();}
  @Override public boolean isInputShutdown(){return s.isInputShutdown();}
  @Override public boolean isOutputShutdown(){return s.isOutputShutdown();}
  @Override public String toString(){return "KIK "+s;}
 }
}
