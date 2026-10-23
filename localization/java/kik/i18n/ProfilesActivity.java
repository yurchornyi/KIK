package kik.i18n;
import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/** "Who is watching" screen: pick a profile without a password; the password guards changes. */
public final class ProfilesActivity extends Activity {
 private LinearLayout content;
 private boolean manage;
 private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
 @Override public void onCreate(Bundle state){
  super.onCreate(state);Ui.screen(this);
  getWindow().setFlags(1024,1024);
  getWindow().getDecorView().setSystemUiVisibility(5894);
  ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xff0d0d0f);scroll.setFillViewport(true);
  content=new LinearLayout(this);content.setOrientation(1);content.setClipChildren(false);content.setClipToPadding(false);content.setGravity(Gravity.CENTER_HORIZONTAL);content.setPadding(dp(48),dp(64),dp(48),dp(32));
  scroll.addView(content,new ScrollView.LayoutParams(-1,-2));setContentView(scroll);
  render();
 }
 private void render(){
  content.removeAllViews();
  List<ProfileStore.Profile> all=ProfileStore.list(this);
  TextView title=text(all.isEmpty()?"Створіть профіль":manage?"Керування профілями":"Хто дивиться?",34);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);content.addView(title);space(8);
  TextView sub=text(all.isEmpty()?"Наявна історія переглядів перейде в цей профіль.":manage?"Виберіть профіль, щоб змінити його. Потрібен пароль профілю.":"Кожен профіль має свою історію та місце, де ви зупинилися.",16);sub.setTextColor(0xffb5b1be);content.addView(sub);space(26);
  if(all.isEmpty()){createForm(true);return;}
  LinearLayout row=new LinearLayout(this);row.setOrientation(0);row.setGravity(Gravity.CENTER);
  HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.setClipToPadding(false);hs.setPadding(dp(12),dp(12),dp(12),dp(12));row.setClipChildren(false);hs.addView(row);content.addView(hs,new LinearLayout.LayoutParams(-2,-2));
  String current=ProfileStore.current(this);View focus=null;
  for(final ProfileStore.Profile p:all){
   ImageView b=Avatars.view(this,p.avatar,150);b.setContentDescription(p.name+", "+Avatars.label(p.avatar)+(manage?", редагувати профіль":", дивитися"));
   LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);tile.setClipChildren(false);tile.setClipToPadding(false);tile.setPadding(0,dp(6),0,dp(6));tile.setGravity(Gravity.CENTER_HORIZONTAL);
   tile.addView(b,new LinearLayout.LayoutParams(dp(150),dp(150)));
   TextView label=text((p.id.equals(current)?"● ":"")+p.name,19);label.setTextColor(0xffb3b3b3);label.setPadding(0,dp(10),0,0);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);tile.addView(label,new LinearLayout.LayoutParams(-1,-2));
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(190),-2);lp.setMargins(dp(10),0,dp(10),0);row.addView(tile,lp);
   b.setOnClickListener(v->{if(manage)unlock(p);else ProfileStore.choose(this,p.id);});
   if(p.id.equals(current)||focus==null)focus=b;
  }
  space(30);
  LinearLayout actions=new LinearLayout(this);actions.setOrientation(0);actions.setClipChildren(false);actions.setClipToPadding(false);actions.setGravity(Gravity.CENTER);content.addView(actions,new LinearLayout.LayoutParams(-2,-2));
  if(!manage){
   Button add=button("+ Додати профіль");actions.addView(add,params(260));add.setOnClickListener(v->{if(!Account.token(this).isEmpty()){SignInActivity.addProfile(this);return;}content.removeAllViews();TextView t=text("Новий профіль",30);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);content.addView(t);space(20);createForm(false);});
   Button edit=button("Керувати профілями");actions.addView(edit,params(280));edit.setOnClickListener(v->{manage=true;render();});
  }else{
   Button done=button("Готово");actions.addView(done,params(200));done.setOnClickListener(v->{manage=false;render();});
  }
  if(focus!=null)focus.requestFocus();
 }
 private void createForm(final boolean first){
  final String[] avatar={Avatars.idAt(ProfileStore.list(this).size())};
  LinearLayout avatarRow=new LinearLayout(this);avatarRow.setGravity(Gravity.CENTER);avatarRow.setClipChildren(false);avatarRow.setClipToPadding(false);
  ImageView preview=Avatars.view(this,avatar[0],64);preview.setFocusable(false);preview.setClickable(false);avatarRow.addView(preview,new LinearLayout.LayoutParams(dp(64),dp(64)));
  Button choose=button("Обрати аватарку");LinearLayout.LayoutParams chooseLp=new LinearLayout.LayoutParams(-2,dp(48));chooseLp.leftMargin=dp(16);avatarRow.addView(choose,chooseLp);content.addView(avatarRow);space(18);
  choose.setOnClickListener(v->pickAvatar(avatar[0],chosen->{avatar[0]=chosen;Avatars.bind(preview,chosen);}));
  final EditText name=input("Ім'я",false);content.addView(name,new LinearLayout.LayoutParams(Ui.contentWidth(this,460,112),dp(56)));space(10);
  final EditText pass=input("Пароль (для зміни чи видалення профілю)",true);content.addView(pass,new LinearLayout.LayoutParams(Ui.contentWidth(this,460,112),dp(56)));space(18);
  LinearLayout actions=new LinearLayout(this);actions.setOrientation(0);actions.setClipChildren(false);actions.setClipToPadding(false);actions.setGravity(Gravity.CENTER);content.addView(actions,new LinearLayout.LayoutParams(-2,-2));
  Button save=button("Створити");actions.addView(save,params(200));
  save.setOnClickListener(v->{
   String n=name.getText().toString().trim(),pw=pass.getText().toString();
   if(n.isEmpty()){toast("Введіть ім'я");return;}
   if(pw.length()<4){toast("Пароль — щонайменше 4 символи");return;}
   for(ProfileStore.Profile p:ProfileStore.list(this))if(p.name.equalsIgnoreCase(n)){toast("Профіль з таким ім'ям уже є");return;}
   try{ProfileStore.Profile p=ProfileStore.create(this,n,pw,avatar[0]);ProfileStore.choose(this,p.id);}
   catch(Exception e){toast("Не вдалося зберегти профіль. Спробуйте ще раз.");}
  });
  if(!first){Button cancel=button("Скасувати");actions.addView(cancel,params(200));cancel.setOnClickListener(v->render());}
  name.requestFocus();
 }
 private void unlock(final ProfileStore.Profile p){
  // Profiles made at sign-in have no password of their own: the account password protects them.
  if(ProfileStore.checkPassword(p,"")){edit(p);return;}
  final EditText pass=input("Пароль профілю",true);
  Ui.dialog(this).setTitle("Профіль «"+p.name+"»").setView(pass).setNegativeButton("Скасувати",null)
   .setPositiveButton("Далі",(d,w)->{if(ProfileStore.checkPassword(p,pass.getText().toString()))edit(p);else toast("Неправильний пароль");}).show();
 }
 private void edit(final ProfileStore.Profile p){
  String[] items={"Змінити аватарку","Перейменувати","Змінити пароль","Видалити профіль та його історію"};
  Ui.dialog(this).setTitle(p.name).setItems(items,(d,which)->{
   if(which==0)pickAvatar(p.avatar,chosen->{if(ProfileStore.setAvatar(this,p.id,chosen)){toast("Аватарку збережено");render();}else toast("Не вдалося зберегти аватарку");});
   else if(which==1){final EditText n=input("Нове ім'я",false);n.setText(p.name);Ui.dialog(this).setTitle("Перейменувати").setView(n).setNegativeButton("Скасувати",null).setPositiveButton("Зберегти",(x,y)->{String wanted=n.getText().toString().trim();if(wanted.isEmpty()){toast("Введіть ім’я");return;}for(ProfileStore.Profile other:ProfileStore.list(this))if(!other.id.equals(p.id)&&other.name.equalsIgnoreCase(wanted)){toast("Профіль з таким ім’ям уже є");return;}ProfileStore.rename(this,p.id,wanted);render();}).show();}
   else if(which==2){final EditText n=input("Новий пароль",true);Ui.dialog(this).setTitle("Змінити пароль").setView(n).setNegativeButton("Скасувати",null).setPositiveButton("Зберегти",(x,y)->{if(n.getText().length()>=4){ProfileStore.changePassword(this,p.id,n.getText().toString());toast("Пароль змінено");}else toast("Пароль — щонайменше 4 символи");}).show();}
   else Ui.dialog(this).setTitle("Видалити «"+p.name+"»?").setMessage("Історія переглядів цього профілю буде видалена.").setNegativeButton("Скасувати",null).setPositiveButton("Видалити",(x,y)->{
    boolean active=p.id.equals(ProfileStore.loaded());
    ProfileStore.delete(this,p.id);
    if(active)ProfileStore.choose(this,ProfileStore.current(this)+"");else render();
   }).show();
  }).show();
 }
 private interface AvatarChoice{void chosen(String id);}
 private void pickAvatar(String initial,AvatarChoice choice){
  final String[] pending={Avatars.valid(initial)?initial:Avatars.idAt(0)};
  LinearLayout grid=new LinearLayout(this);grid.setOrientation(1);grid.setPadding(dp(6),dp(4),dp(6),dp(4));grid.setClipChildren(false);grid.setClipToPadding(false);
  ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
  int maxHeight=Math.max(dp(100),getResources().getDisplayMetrics().heightPixels-dp(180));
  // AlertDialog's custom container uses the scroll view's own height; it never replaces the native actions.
  FrameLayout holder=new FrameLayout(this);holder.setPadding(dp(12),0,dp(12),dp(8));holder.addView(scroll,new FrameLayout.LayoutParams(-1,Math.min(dp(304),maxHeight)));
  List<ImageView> icons=new ArrayList<ImageView>();LinearLayout row=null;ImageView first=null;
  for(int i=0;i<Avatars.count();i++){
   if(i%4==0){row=new LinearLayout(this);row.setClipChildren(false);row.setClipToPadding(false);grid.addView(row,new LinearLayout.LayoutParams(-1,-2));}
   final String id=Avatars.idAt(i);LinearLayout tile=new LinearLayout(this);tile.setOrientation(1);tile.setGravity(Gravity.CENTER_HORIZONTAL);tile.setPadding(dp(2),dp(6),dp(2),dp(6));tile.setClipChildren(false);tile.setClipToPadding(false);
   ImageView image=Avatars.view(this,id,64);image.setId(View.generateViewId());image.setTag(id);image.setSelected(id.equals(pending[0]));icons.add(image);tile.addView(image,new LinearLayout.LayoutParams(dp(64),dp(64)));
   TextView label=text(Avatars.label(id),13);label.setSingleLine(true);label.setEllipsize(TextUtils.TruncateAt.END);label.setTextColor(Ui.GREY);label.setPadding(0,dp(4),0,0);tile.addView(label,new LinearLayout.LayoutParams(-1,-2));row.addView(tile,new LinearLayout.LayoutParams(0,-2,1));
   image.setOnClickListener(v->{pending[0]=id;for(ImageView icon:icons)icon.setSelected(id.equals(icon.getTag()));});
   if(id.equals(pending[0]))first=image;
  }
  for(int i=0;i<icons.size();i++){ImageView icon=icons.get(i);icon.setNextFocusLeftId(icons.get(i%4==0?i:i-1).getId());icon.setNextFocusRightId(icons.get(i%4==3?i:i+1).getId());if(i>=4)icon.setNextFocusUpId(icons.get(i-4).getId());if(i+4<icons.size())icon.setNextFocusDownId(icons.get(i+4).getId());}
  AlertDialog dialog=Ui.dialog(this).setTitle("Оберіть аватарку").setView(holder).setNegativeButton("Скасувати",null).setPositiveButton("Обрати",(d,w)->choice.chosen(pending[0])).create();
  dialog.show();if(first!=null){ImageView selected=first;selected.post(selected::requestFocus);}
 }
 private LinearLayout.LayoutParams params(int w){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(w),dp(52));lp.setMargins(dp(8),0,dp(8),0);return lp;}
 private EditText input(String hint,boolean password){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(0xff8a8592);e.setTextColor(Color.WHITE);e.setTextSize(18);e.setSingleLine(true);e.setPadding(dp(14),0,dp(14),0);e.setBackground(focusBackground());if(!password)e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(24)});e.setInputType(password?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_WORDS);return e;}
 private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
 private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER);return t;}
 private void space(int size){View v=new View(this);content.addView(v,new LinearLayout.LayoutParams(1,dp(size)));}
 private Button button(String text){return Ui.pill(this,text,false);}
 private StateListDrawable focusBackground(){StateListDrawable s=new StateListDrawable();s.addState(new int[]{android.R.attr.state_focused},shape(Ui.CARD,Ui.RED));s.addState(new int[]{},shape(0xff1f1f23,0xff2e2e33));return s;}
 private GradientDrawable shape(int fill,int stroke){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(9));d.setStroke(dp(2),stroke);return d;}
}
