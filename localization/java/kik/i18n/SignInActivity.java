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
 * Mandatory sign-in, one question per screen for the remote: email, then the password (or, for an invited address,
 * the administrator's invitation code and a new password), then a profile and its avatar when the account has none.
 * The server decides which emails may enter.
 */
public final class SignInActivity extends Activity {
 private static final int LOADING=0,EMAIL=1,PASSWORD=2,CREATE=3,PICK=4,NAME=5,AVATAR=6;
 private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService io=Executors.newSingleThreadExecutor();
 private volatile boolean destroyed;private boolean working;private LinearLayout content;private TextView error;private int step;private boolean adding;
 private String email="",name="";private JSONArray remote=new JSONArray();

 /** "+ Add profile" for a signed-in TV: the same name and avatar screens, made on the account. */
 public static void addProfile(Activity a){a.startActivity(new Intent(a,SignInActivity.class).putExtra("add",true));}
 private int dp(float n){return Ui.dp(this,n);}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);Ui.screen(this);
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(Ui.BG);scroll.setFillViewport(true);
  content=new LinearLayout(this);content.setOrientation(1);content.setClipChildren(false);content.setClipToPadding(false);content.setGravity(Gravity.CENTER_HORIZONTAL);content.setPadding(dp(48),dp(40),dp(48),dp(32));
  scroll.addView(content,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);
  email=Account.email(this);adding=getIntent().getBooleanExtra("add",false)&&!Account.token(this).isEmpty();
  if(adding)show(NAME);else if(!Account.token(this).isEmpty())resume();else show(EMAIL);
 }
 @Override protected void onDestroy(){destroyed=true;main.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}

 // ---------- steps ----------
 private void show(int target){
  step=target;error=null;
  // A step without a text field must not keep the previous step's keyboard over its tiles.
  ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(content.getWindowToken(),0);
  content.removeAllViews();
  content.addView(Brand.logo(this,96,44));space(26);
  if(step==LOADING){head("Вхід у KIK","Завантаження облікового запису…");return;}
  if(step==EMAIL){
   head("Увійдіть у KIK","Введіть пошту, яку адміністратор додав до KIK.");
   final EditText field=input("Пошта",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,EditorInfo.IME_ACTION_NEXT);field.setText(email);field.setSelection(field.length());
   final Button next=primary("Далі");next.setOnClickListener(v->checkEmail(field,next));submit(field,next);type(field);
  }else if(step==PASSWORD){
   head("Введіть пароль",email);
   final EditText field=input("Пароль",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,EditorInfo.IME_ACTION_DONE);
   LinearLayout row=actions();final Button login=button(row,"Увійти",true);button(row,"Інша пошта",false).setOnClickListener(v->show(EMAIL));
   login.setOnClickListener(v->login(field,login));submit(field,login);type(field);
  }else if(step==CREATE){
   head("Створіть пароль","Для "+email+" ще немає пароля. Введіть код запрошення від адміністратора і новий пароль — щонайменше 6 символів.");
   final EditText code=input("Код запрошення, наприклад K7M2-9QXA",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,EditorInfo.IME_ACTION_NEXT);code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(9),new android.text.InputFilter.AllCaps()});
   final EditText field=input("Новий пароль",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,EditorInfo.IME_ACTION_NEXT);
   final EditText again=input("Повторіть пароль",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD,EditorInfo.IME_ACTION_DONE);
   LinearLayout row=actions();final Button create=button(row,"Створити пароль",true);button(row,"Інша пошта",false).setOnClickListener(v->show(EMAIL));
   create.setOnClickListener(v->register(code,field,again,create));code.setOnEditorActionListener((v,a,e)->{field.requestFocus();return true;});field.setOnEditorActionListener((v,a,e)->{again.requestFocus();return true;});submit(again,create);type(code);
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
 private void register(EditText code,EditText field,EditText again,Button button){
  String invitation=code.getText().toString().toUpperCase(java.util.Locale.ROOT).replaceAll("[\\s-]","");
  if(!invitation.matches("[A-HJ-NP-Z2-9]{8}")){error("Введіть 8 символів коду запрошення від адміністратора");code.requestFocus();return;}
  String password=field.getText().toString();
  if(password.length()<6){error("Пароль — щонайменше 6 символів");field.requestFocus();return;}
  if(password.length()>256){error("Пароль задовгий");field.requestFocus();return;}
  if(!password.equals(again.getText().toString())){error("Паролі не збігаються");again.setText("");again.requestFocus();return;}
  busy(button,"Створення…");
  call(button,"/api/auth/register",json("email",email,"password",password,"invitation",invitation,"profile",false,"device",true),null,this::signedIn,null);
 }
 private void signedIn(JSONObject result)throws Exception{
  String token=result.getString("token");if(!token.matches("[a-f0-9]{64}"))throw new java.io.IOException("Token");
  Account.save(this,email,token);remote=result.optJSONArray("profiles");if(remote==null)remote=new JSONArray();
  show(remote.length()==0?NAME:PICK);
 }
 /** Reopened after sign-in but before a profile was chosen: continue from the account's profiles. */
 private void resume(){
  show(LOADING);final String token=Account.token(this);
  io.execute(()->{try{JSONObject me=request("/api/me",token);main.post(()->{if(destroyed)return;if(me.isNull("user")){Account.signOut(this);show(EMAIL);return;}remote=me.optJSONArray("profiles");if(remote==null)remote=new JSONArray();show(remote.length()==0?NAME:PICK);});}
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
