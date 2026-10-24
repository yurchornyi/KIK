package kik.i18n;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.*;
import android.text.InputType;
import android.text.TextUtils;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.Random;
import java.util.concurrent.*;
import org.json.*;

/**
 * Mandatory sign-in, one question per screen for the remote: email, then the password (or, for an email the administrator
 * allowed or whose password was reset, a new password), then a profile and its avatar when the account has none.
 * The server decides which emails may enter. «Увійти з телефону» unfolds a code and a QR code instead: the viewer confirms
 * it on a phone already signed in to the website, and the TV signs in without a password typed on the remote.
 */
public final class SignInActivity extends Activity {
 private static final int LOADING=0,EMAIL=1,PASSWORD=2,CREATE=3,PICK=4,NAME=5,AVATAR=6;
 private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newSingleThreadExecutor();
 private volatile boolean destroyed;private boolean working;private ScrollView scroll;private LinearLayout content;private volatile int pairing;private TextView error;private int step;private boolean adding;
 private String email="",name="";private JSONArray remote=new JSONArray();

 /** "+ Add profile" for a signed-in TV: the same name and avatar screens, made on the account. */
 public static void addProfile(Activity a){a.startActivity(new Intent(a,SignInActivity.class).putExtra("add",true));}
 private int dp(float n){return Ui.dp(this,n);}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);Ui.screen(this);
  scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);scroll.setFillViewport(true);
  content=new LinearLayout(this);content.setOrientation(1);content.setClipChildren(false);content.setClipToPadding(false);content.setGravity(Gravity.CENTER_HORIZONTAL);content.setPadding(dp(48),dp(40),dp(48),dp(32));
  scroll.addView(content,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);
  email=Account.email(this);adding=getIntent().getBooleanExtra("add",false)&&!Account.token(this).isEmpty();
  if(adding)show(NAME);else if(!Account.token(this).isEmpty())resume();else show(EMAIL);
 }
 @Override protected void onDestroy(){destroyed=true;main.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}

 // ---------- steps ----------
 private void show(int target){
  step=target;error=null;pairing++;
  // A step without a text field must not keep the previous step's keyboard over its tiles.
  ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(content.getWindowToken(),0);
  content.removeAllViews();
  content.addView(Brand.logo(this,96,44));space(26);
  if(step==LOADING){head("Вхід у KIK","Завантаження облікового запису…");return;}
  if(step==EMAIL){
   head("Увійдіть у KIK","Введіть пошту, яку адміністратор додав до KIK.");
   final EditText field=input("Пошта",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,EditorInfo.IME_ACTION_NEXT);field.setText(email);field.setSelection(field.length());
   final Button next=wide("Далі",true);next.setOnClickListener(v->checkEmail(field,next));submit(field,next);
   // Focus starts on a button, so no keyboard covers the phone option; OK on the field opens it.
   Button phone=phoneOption();(email.isEmpty()?phone:next).requestFocus();
  }else if(step==PASSWORD){
   head("Введіть пароль",email);
   final EditText field=input("Пароль",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,EditorInfo.IME_ACTION_DONE);
   LinearLayout row=actions();final Button login=button(row,"Увійти",true);button(row,"Інша пошта",false).setOnClickListener(v->show(EMAIL));
   login.setOnClickListener(v->login(field,login));submit(field,login);type(field);
  }else if(step==CREATE){
   head("Створіть пароль","Для "+email+" ще немає пароля. Придумайте пароль — щонайменше 6 символів.");
   final EditText field=input("Новий пароль",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,EditorInfo.IME_ACTION_NEXT);
   final EditText again=input("Повторіть пароль",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,EditorInfo.IME_ACTION_DONE);
   LinearLayout row=actions();final Button create=button(row,"Створити пароль",true);button(row,"Інша пошта",false).setOnClickListener(v->show(EMAIL));
   create.setOnClickListener(v->register(field,again,create));field.setOnEditorActionListener((v,a,e)->{again.requestFocus();return true;});submit(again,create);type(field);
  }else if(step==PICK){
   head("Хто дивиться?","Виберіть свій профіль. Історія й місце, де ви зупинилися, підтягнуться з облікового запису.");
   LinearLayout row=new LinearLayout(this);row.setOrientation(0);row.setGravity(Gravity.CENTER);row.setClipChildren(false);
   HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.setClipToPadding(false);hs.setPadding(dp(12),dp(12),dp(12),dp(12));hs.addView(row);content.addView(hs,new LinearLayout.LayoutParams(-2,-2));
   View first=null;
   for(int i=0;i<remote.length();i++){final JSONObject p=remote.optJSONObject(i);if(p==null)continue;View tile=tile(row,p.optString("avatar"),p.optString("name"),150);tile.setOnClickListener(v->pick(p));if(first==null)first=tile;}
   
   LinearLayout actions=actions();button(actions,"+ Новий профіль",false).setOnClickListener(v->show(NAME));
   if(first!=null)first.requestFocus();
  }else if(step==NAME){
   head(adding?"Новий профіль":"Створіть профіль","Як звати того, хто дивиться? У кожного профілю своя історія та «Мій список».");
   final EditText field=input("Ім’я",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS,EditorInfo.IME_ACTION_NEXT);field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});field.setText(name);field.setSelection(field.length());
   LinearLayout row=actions();final Button next=button(row,"Далі",true);if(adding)button(row,"Скасувати",false).setOnClickListener(v->finish());
   next.setOnClickListener(v->{String n=field.getText().toString().trim();if(n.isEmpty()){error("Введіть ім’я профілю");return;}for(ProfileStore.Profile p:ProfileStore.list(this))if(p.name.equalsIgnoreCase(n)&&!CloudSync.remoteId(this,p.id).isEmpty()){error("Профіль з таким ім’ям уже є");return;}for(int i=0;i<remote.length();i++)if(n.equalsIgnoreCase(remote.optJSONObject(i).optString("name"))){error("Профіль з таким ім’ям уже є");return;}name=n;show(AVATAR);});
   submit(field,next);type(field);
  }else{
   head("Оберіть аватарку","Для профілю «"+name+"». Можна пропустити — KIK вибере випадкову.");
   View first=null;LinearLayout row=null;
   for(int i=0;i<Avatars.count();i++){
    if(i%6==0){row=new LinearLayout(this);row.setOrientation(0);row.setGravity(Gravity.CENTER);row.setClipChildren(false);row.setClipToPadding(false);content.addView(row,new LinearLayout.LayoutParams(-2,-2));}
    final String id=Avatars.idAt(i);View tile=tile(row,id,Avatars.label(id),88);tile.setOnClickListener(v->createProfile(id));if(first==null)first=tile;
   }
   
   LinearLayout actions=actions();button(actions,"Пропустити",false).setOnClickListener(v->createProfile(Avatars.idAt(new Random().nextInt(Avatars.count()))));
   if(first!=null)first.requestFocus();
  }
 }
 @Override public boolean dispatchKeyEvent(KeyEvent e){
  if(e.getKeyCode()==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP)onBackPressed();return true;}
  return super.dispatchKeyEvent(e);
 }
 @Override public void onBackPressed(){
  if(step==PASSWORD||step==CREATE)show(EMAIL);
  else if(step==AVATAR)show(NAME);
  else if(step==NAME&&adding)finish();
  else if(step==NAME&&remote.length()>0)show(PICK);
  else finishAffinity();
 }

 // ---------- sign-in from a phone ----------
 /** «або» and a folded card: opening it asks the server for a code; closing it stops waiting. */
 private Button phoneOption(){
  space(22);LinearLayout or=new LinearLayout(this);or.setOrientation(0);or.setGravity(Gravity.CENTER_VERTICAL);
  for(int i=0;i<3;i++){if(i==1){TextView t=text("або",15);t.setTextColor(0xff8a8592);t.setPadding(dp(16),0,dp(16),0);or.addView(t);continue;}View line=new View(this);line.setBackgroundColor(0x26ffffff);or.addView(line,new LinearLayout.LayoutParams(0,dp(1),1));}
  content.addView(or,new LinearLayout.LayoutParams(dp(480),-2));space(22);
  final Button toggle=wide("Увійти з телефону",false);
  final LinearLayout card=new LinearLayout(this);card.setOrientation(0);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(28),dp(24),dp(28),dp(24));card.setBackground(Ui.shape(0xff18181d,dp(18),dp(1),0x22ffffff));card.setVisibility(View.GONE);
  final ImageView qr=new ImageView(this);qr.setContentDescription("QR-код для входу з телефону");qr.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(dp(176),dp(176));qp.rightMargin=dp(28);card.addView(qr,qp);
  LinearLayout copy=new LinearLayout(this);copy.setOrientation(1);card.addView(copy,new LinearLayout.LayoutParams(dp(360),-2));
  TextView kicker=text("ВХІД З ТЕЛЕФОНУ",12);kicker.setGravity(Gravity.START);kicker.setTextColor(0xffef5a62);kicker.setLetterSpacing(.12f);copy.addView(kicker);
  final TextView code=text("",36);code.setGravity(Gravity.START);code.setTypeface(Typeface.MONOSPACE,Typeface.BOLD);code.setPadding(0,dp(6),0,dp(6));copy.addView(code);
  final TextView steps=text("Отримання коду…",15);steps.setGravity(Gravity.START);steps.setTextColor(0xffb5b1be);steps.setLineSpacing(0,1.25f);copy.addView(steps);
  LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.topMargin=dp(18);content.addView(card,cp);
  toggle.setOnClickListener(v->{boolean open=card.getVisibility()!=View.VISIBLE;card.setVisibility(open?View.VISIBLE:View.GONE);toggle.setText(open?"Сховати код":"Увійти з телефону");final int gen=++pairing;
   if(open){startPair(gen,qr,code,steps);scroll.post(()->scroll.smoothScrollTo(0,card.getBottom()));}});
  return toggle;
 }
 private void startPair(int gen,ImageView qr,TextView code,TextView steps){
  code.setText("");qr.setImageDrawable(null);steps.setText("Отримання коду…");
  io.execute(()->{try{JSONObject r=CloudSync.request("/api/pair/start",new JSONObject(),null);final String user=r.getString("user_code"),secret=r.getString("device_code");final long until=SystemClock.elapsedRealtime()+r.optInt("expires_in",600)*1000L;
    main.post(()->{if(destroyed||gen!=pairing)return;code.setText(user.substring(0,4)+" "+user.substring(4));qr.setImageDrawable(SyncActivity.qr(getResources(),UpdateConfig.WEB_ORIGIN+"/pair?code="+user));
     steps.setText("1. Наведіть камеру телефона на QR-код.\n2. Увійдіть на сайті, виберіть профіль і підтвердьте.\nБез камери: "+UpdateConfig.WEB_ORIGIN.replaceFirst("^https?://","")+" → «Прив’язати TV» і цей код.");pollPair(gen,secret,until,qr,code,steps);});}
   catch(Exception e){main.post(()->{if(!destroyed&&gen==pairing)steps.setText("Не вдалося отримати код. Перевірте інтернет і відкрийте ще раз.");});}});
 }
 /** Asks every 3 s; an expired code is replaced by a fresh one while the card stays open. */
 private void pollPair(int gen,String secret,long until,ImageView qr,TextView code,TextView steps){
  if(destroyed||gen!=pairing)return;if(SystemClock.elapsedRealtime()>until){startPair(gen,qr,code,steps);return;}
  io.execute(()->{try{JSONObject r=CloudSync.request("/api/pair/poll",new JSONObject().put("device_code",secret),null);if(!r.optBoolean("pending")){main.post(()->{if(!destroyed&&gen==pairing)paired(r);});return;}}
   catch(CloudSync.Failure e){if(e.code==410){main.post(()->{if(!destroyed&&gen==pairing)startPair(gen,qr,code,steps);});return;}}catch(Exception ignored){}
   main.postDelayed(()->pollPair(gen,secret,until,qr,code,steps),3000);});
 }
 /** The phone approved this TV for one profile: keep the session, bind that profile and the account's others, open it. */
 private void paired(JSONObject result){
  final String token=result.optString("token");final JSONObject chosen=result.optJSONObject("profile");if(!token.matches("[a-f0-9]{64}")||chosen==null){error("Не вдалося завершити вхід. Спробуйте ще раз.");return;}
  show(LOADING);
  io.execute(()->{try{JSONObject me=request("/api/me",token);main.post(()->{if(destroyed)return;JSONObject user=me.optJSONObject("user");if(user==null){show(EMAIL);error("Вхід скасовано. Спробуйте ще раз.");return;}
    email=user.optString("email");Account.save(this,email,token);remote=me.optJSONArray("profiles");if(remote==null)remote=new JSONArray();
    JSONObject match=chosen;for(int i=0;i<remote.length();i++){JSONObject p=remote.optJSONObject(i);if(p!=null&&p.optString("id").equals(chosen.optString("id")))match=p;}pick(match);});}
   catch(Exception e){main.post(()->{if(destroyed)return;show(EMAIL);error("Немає зв’язку з KIK. Перевірте інтернет і спробуйте ще раз.");});}});
 }

 // ---------- requests ----------
 private void checkEmail(EditText field,Button button){
  String value=field.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
  if(!value.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")){error("Введіть коректну пошту, наприклад name@gmail.com");return;}
  email=value;busy(button,"Перевірка…");
  call(button,"/api/auth/check",json("email",value),null,result->show("create".equals(result.optString("state"))?CREATE:PASSWORD),null);
 }
 private void login(EditText field,Button button){
  String password=field.getText().toString();if(password.isEmpty()){error("Введіть пароль");return;}
  busy(button,"Вхід…");
  call(button,"/api/auth/login",json("email",email,"password",password,"device",true),null,this::signedIn,()->{field.setText("");field.requestFocus();error("Неправильний пароль. Спробуйте ще раз.");});
 }
 private void register(EditText field,EditText again,Button button){
  String password=field.getText().toString();
  if(password.length()<6){error("Пароль — щонайменше 6 символів");field.requestFocus();return;}
  if(password.length()>256){error("Пароль задовгий");field.requestFocus();return;}
  if(!password.equals(again.getText().toString())){error("Паролі не збігаються");again.setText("");again.requestFocus();return;}
  busy(button,"Створення…");
  call(button,"/api/auth/register",json("email",email,"password",password,"profile",false,"device",true),null,this::signedIn,null);
 }
 private void signedIn(JSONObject result)throws Exception{
  String token=result.getString("token");if(!token.matches("[a-f0-9]{64}"))throw new java.io.IOException("Token");
  Account.save(this,email,token);remote=result.optJSONArray("profiles");if(remote==null)remote=new JSONArray();
  profiles();
 }
 /** One profile on the account: no «Хто дивиться?», it is simply used. None: create one. Several: the viewer picks. */
 private void profiles(){JSONObject only=remote.length()==1?remote.optJSONObject(0):null;if(only!=null){pick(only);return;}show(remote.length()==0?NAME:PICK);}
 /** Reopened after sign-in but before a profile was chosen: continue from the account's profiles. */
 private void resume(){
  show(LOADING);final String token=Account.token(this);
  io.execute(()->{try{JSONObject me=request("/api/me",token);main.post(()->{if(destroyed)return;if(me.isNull("user")){Account.signOut(this);show(EMAIL);return;}remote=me.optJSONArray("profiles");if(remote==null)remote=new JSONArray();profiles();});}
   catch(Exception e){main.post(()->{if(destroyed)return;show(EMAIL);error(e instanceof CloudSync.Failure?e.getMessage():"Немає зв’язку з KIK. Перевірте інтернет і спробуйте ще раз.");});}});
 }
 private void pick(JSONObject chosen){
  try{String local=Account.adopt(this,chosen,true);for(int i=0;i<remote.length();i++){JSONObject p=remote.optJSONObject(i);if(p!=null&&p!=chosen)Account.adopt(this,p,false);}ProfileStore.choose(this,local);}
  catch(Exception e){error("Не вдалося відкрити профіль. Спробуйте ще раз.");}
 }
 private void createProfile(String avatar){
  if(working)return;error("Створення профілю…");
  call(null,"/api/profiles",json("name",name,"avatar",avatar),Account.token(this),p->{String local=Account.adopt(this,p,!adding);ProfileStore.requireTasteSetup(this,local);ProfileStore.choose(this,local);},()->{Account.signOut(this);show(EMAIL);error("Сеанс завершився. Увійдіть ще раз.");});
 }

 private interface Done{void run(JSONObject result)throws Exception;}
 /** Runs a request off the main thread; a refusal shows the server's reason, a 401 goes to {@code denied}. */
 private void call(Button button,String path,JSONObject data,String token,Done done,Runnable denied){
  final int at=step;final CharSequence label=button==null?null:button.getTag() instanceof CharSequence?(CharSequence)button.getTag():button.getText();working=true;
  io.execute(()->{
   String failure;Runnable fallback=null;
   try{final JSONObject result=CloudSync.request(path,data,token);main.post(()->{working=false;if(destroyed)return;try{done.run(result);}catch(Exception e){show(at);error("Не вдалося завершити вхід. Спробуйте ще раз.");}});return;}
   catch(SecurityException e){failure=denied==null?"Пошта або пароль не підходять":null;fallback=denied;}
   catch(CloudSync.Failure e){failure=e.code==429?"Забагато спроб. Зачекайте хвилину.":e.getMessage();}
   catch(Exception e){failure="Немає зв’язку з KIK. Перевірте інтернет і спробуйте ще раз.";}
   final String message=failure;final Runnable then=fallback;
   main.post(()->{working=false;if(destroyed)return;if(step!=at)show(at);else if(button!=null){button.setEnabled(true);button.setText(label);}if(then!=null)then.run();else error(message);});
  });
 }
 /** /api/me is a GET; it answers with the account's profiles for this TV's session. */
 private static JSONObject request(String path,String token)throws Exception{
  java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(UpdateConfig.WEB_ORIGIN+path).openConnection();c.setConnectTimeout(12000);c.setReadTimeout(15000);c.setRequestProperty("Authorization","Bearer "+token);
  try(java.io.InputStream in=c.getInputStream()){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1&&out.size()<1024*1024)out.write(b,0,n);return new JSONObject(out.toString("UTF-8"));}finally{c.disconnect();}
 }
 private static JSONObject json(Object... pairs){try{JSONObject o=new JSONObject();for(int i=0;i<pairs.length;i+=2)o.put((String)pairs[i],pairs[i+1]);return o;}catch(JSONException e){throw new IllegalStateException(e);}}

 // ---------- views ----------
 private void head(String title,String sub){
  TextView t=text(title,34);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);content.addView(t);space(8);
  TextView s=text(sub,17);s.setTextColor(0xffb5b1be);content.addView(s,new LinearLayout.LayoutParams(dp(640),-2));
  // Errors sit above the fields: the on-screen keyboard covers the lower half of the screen.
  space(10);error=text("",16);error.setTextColor(0xffff7a7f);error.setMinHeight(dp(24));content.addView(error,new LinearLayout.LayoutParams(dp(640),-2));space(10);
 }
 private EditText input(String hint,int type,int action){
  EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(0xff8a8592);e.setTextColor(Color.WHITE);e.setTextSize(19);e.setSingleLine(true);e.setPadding(dp(16),0,dp(16),0);e.setBackground(fieldBackground());
  e.setInputType(type);e.setTypeface(Typeface.DEFAULT);e.setImeOptions(action|EditorInfo.IME_FLAG_NO_EXTRACT_UI);content.addView(e,new LinearLayout.LayoutParams(dp(480),dp(58)));space(16);return e;
 }
 /** The keyboard's Enter submits the step, so a remote never has to leave the field. */
 private void submit(EditText field,Button button){field.setOnEditorActionListener((v,a,e)->{if(button.isEnabled())button.performClick();return true;});}
 /** Focus a field and open the on-screen keyboard at once: the remote should not need an extra OK. */
 private void type(final EditText field){field.requestFocus();field.postDelayed(()->{if(destroyed||!field.isAttachedToWindow())return;((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(field,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);},250);}
 private void error(String message){if(error!=null)error.setText(message);}
 private void busy(Button b,String label){if(b.isEnabled())b.setTag(b.getText());b.setEnabled(false);b.setText(label);error("");}
 private Button primary(String label){LinearLayout row=actions();return button(row,label,true);}
 /** A button as wide as the text field above it: the email screen's two ways in line up in one column. */
 private Button wide(String label,boolean primary){Button b=Ui.pill(this,label,primary);content.addView(b,new LinearLayout.LayoutParams(dp(480),dp(54)));return b;}
 private LinearLayout actions(){LinearLayout row=new LinearLayout(this);row.setOrientation(0);row.setGravity(Gravity.CENTER);row.setClipChildren(false);row.setClipToPadding(false);content.addView(row);return row;}
 private Button button(LinearLayout row,String label,boolean primary){Button b=Ui.pill(this,label,primary);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(52));lp.setMargins(dp(8),0,dp(8),0);b.setMinWidth(dp(200));row.addView(b,lp);return b;}
 private View tile(LinearLayout row,String avatar,String label,int size){
  LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);tile.setGravity(Gravity.CENTER_HORIZONTAL);tile.setClipChildren(false);tile.setClipToPadding(false);tile.setPadding(0,dp(6),0,dp(6));
  ImageView image=Avatars.view(this,Avatars.valid(avatar)?avatar:Avatars.idAt(0),size);image.setContentDescription(label);tile.addView(image,new LinearLayout.LayoutParams(dp(size),dp(size)));
  TextView name=text(label,size>100?19:14);name.setTextColor(0xffb3b3b3);name.setPadding(0,dp(8),0,0);name.setSingleLine(true);name.setEllipsize(TextUtils.TruncateAt.END);tile.addView(name,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(size+40),-2);lp.setMargins(dp(8),0,dp(8),0);row.addView(tile,lp);return image;
 }
 private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER);return t;}
 private void space(int size){content.addView(new View(this),new LinearLayout.LayoutParams(1,dp(size)));}
 private StateListDrawable fieldBackground(){StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_focused},Ui.shape(Ui.CARD,dp(9),dp(2),Ui.RED));s.addState(new int[]{},Ui.shape(0xff1f1f23,dp(9),dp(2),0xff2e2e33));return s;}
}
