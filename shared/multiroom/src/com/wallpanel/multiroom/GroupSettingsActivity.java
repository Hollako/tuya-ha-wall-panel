package com.wallpanel.multiroom;
import android.app.Activity;
import android.content.*;
import android.os.*;
import android.widget.*;
import android.text.InputType;

public final class GroupSettingsActivity extends Activity {
 private Spinner role;private EditText leader,source,buffer;private TimingSlider roomDelay;private LevelSlider roomVolume;private TextView status;private final Handler main=new Handler();
 private final Runnable refresh=new Runnable(){public void run(){status.setText(GroupRuntime.status());roomDelay.refresh(getSharedPreferences("wallpanel_groups",MODE_PRIVATE).getInt("room_delay_ms",0));roomVolume.refresh(GroupRuntime.volume(GroupSettingsActivity.this));main.postDelayed(this,1000);}};
 @Override public void onCreate(Bundle saved){super.onCreate(saved);getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
  LinearLayout page=new LinearLayout(this);page.setOrientation(1);page.setPadding(20,16,20,16);page.setFocusableInTouchMode(true);ScrollView scroll=new ScrollView(this);scroll.addView(page);setContentView(scroll);
  Button back=new Button(this);back.setText("Back");page.addView(back);back.setOnClickListener(v->finish());
  TextView title=new TextView(this);title.setText("Panel audio groups");title.setTextSize(24);page.addView(title);
  Button manage=new Button(this);manage.setText("Rooms and saved groups");page.addView(manage);manage.setOnClickListener(v->startActivity(new Intent(this,GroupManagerActivity.class)));
  TextView note=new TextView(this);note.setText("Choose one leader to supply a local audio file or direct audio URL. Other panels join using its IP address. The leader plays through the same group as the receivers. Stop grouping to return to other audio sources. Intercom has priority. Group buffering adds delay; this version is for music, not video lip-sync.");page.addView(note);
  SharedPreferences p=getSharedPreferences("wallpanel_groups",MODE_PRIVATE);
  roomVolume=new LevelSlider(this,GroupRuntime.volume(this),level->{try{GroupRuntime.volume(this,level);}catch(Exception error){Toast.makeText(this,"Could not save room volume",Toast.LENGTH_LONG).show();}});page.addView(roomVolume);
  role=new Spinner(this);role.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Receiver","Leader"}));role.setSelection(p.getString("role","receiver").equals("leader")?1:0);page.addView(role);
  leader=field(page,"Leader IP or hostname",p.getString("leader",""));source=field(page,"Leader audio URL or selected file",p.getString("source",""));
  buffer=field(page,"Group buffer in ms (leader only, 1000-5000)",Integer.toString(p.getInt("buffer_ms",2000)));buffer.setInputType(InputType.TYPE_CLASS_NUMBER);
  roomDelay=new TimingSlider(this,"Room timing (-500 to +500 ms)",p.getInt("room_delay_ms",0),ms->{try{GroupRuntime.roomTiming(this,ms);}catch(Exception error){Toast.makeText(this,"Could not save room timing",Toast.LENGTH_LONG).show();}});page.addView(roomDelay);
  TextView timingHelp=new TextView(this);timingHelp.setText("Timing saves when you release the slider. During playback it applies within a few seconds without restarting the room. Positive delays this panel; negative plays it earlier.");page.addView(timingHelp);
  Button file=new Button(this);file.setText("Choose local audio file");page.addView(file);file.setOnClickListener(v->{try{startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),81);}catch(ActivityNotFoundException unavailable){Toast.makeText(this,"This panel has no file picker. Enter a direct audio URL instead.",Toast.LENGTH_LONG).show();}});
  Button save=new Button(this);save.setText("Save source without playing");page.addView(save);save.setOnClickListener(v->{try{if(GroupRuntime.active())throw new IllegalStateException("Stop this room before changing its source");p.edit().putString("source",GroupPolicy.source(source.getText().toString())).putString("source_kind","file").remove("source_title").putInt("buffer_ms",GroupPolicy.buffer(Integer.parseInt(buffer.getText().toString().trim()))).apply();Toast.makeText(this,"Source saved",Toast.LENGTH_SHORT).show();}catch(Exception error){Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();}});
  Button start=new Button(this);start.setText("Start group playback on this panel");page.addView(start);start.setOnClickListener(v->{try{
   if(GroupRuntime.active())throw new IllegalStateException("Stop this panel's group before changing its configuration");
   String mode=role.getSelectedItemPosition()==1?"leader":"receiver";String host=mode.equals("receiver")?GroupPolicy.host(leader.getText().toString()):leader.getText().toString();String url=mode.equals("leader")?GroupPolicy.source(source.getText().toString()):source.getText().toString();
   int buffering=GroupPolicy.buffer(Integer.parseInt(buffer.getText().toString().trim())),timing=GroupPolicy.roomDelay(roomDelay.value());
   p.edit().remove("group_leader").putString("source_kind","file").remove("source_title").putString("role",mode).putString("leader",host).putString("source",url).putInt("buffer_ms",buffering).putInt("room_delay_ms",timing).apply();GroupRuntime.start(this);
  }catch(Exception error){Toast.makeText(this,error.getMessage(),Toast.LENGTH_LONG).show();}});
  Button pause=new Button(this);pause.setText("Play / pause (leader: group source; receiver: this room)");page.addView(pause);pause.setOnClickListener(v->GroupRuntime.transport("play_pause"));
  Button stop=new Button(this);stop.setText("Stop grouping on this panel");page.addView(stop);stop.setOnClickListener(v->{GroupRuntime.status("Group playback stopped");GroupRuntime.stop(this);});
  status=new TextView(this);status.setPadding(0,16,0,0);page.addView(status);page.requestFocus();
 }
 private EditText field(LinearLayout page,String label,String value){TextView name=new TextView(this);name.setText(label);name.setPadding(0,16,0,0);page.addView(name);EditText field=new EditText(this);field.setSingleLine(true);field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);field.setText(value);page.addView(field);return field;}
 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==81&&result==RESULT_OK&&data!=null&&data.getData()!=null){try{getContentResolver().takePersistableUriPermission(data.getData(),Intent.FLAG_GRANT_READ_URI_PERMISSION);source.setText(data.getData().toString());}catch(Exception error){Toast.makeText(this,"The selected file could not be retained",Toast.LENGTH_LONG).show();}}}
 @Override protected void onResume(){super.onResume();main.post(refresh);}
 @Override protected void onPause(){main.removeCallbacks(refresh);super.onPause();}
}
