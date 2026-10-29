package com.wallpanel.multiroom;
import android.content.Context;
import android.widget.*;
import java.util.Locale;
public final class LevelSlider extends LinearLayout {
 public interface Changed {void saved(double level);}
 private final SeekBar slider;private final TextView value;private boolean editing;
 public LevelSlider(Context context,double initial,Changed changed){
  super(context);setOrientation(VERTICAL);TextView title=new TextView(context);title.setText("Room volume");title.setPadding(0,16,0,4);addView(title);value=new TextView(context);addView(value);
  slider=new SeekBar(context);slider.setMax(100);addView(slider);slider.setProgress((int)Math.round(initial*100));label();
  slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int progress,boolean user){label();}public void onStartTrackingTouch(SeekBar bar){editing=true;}public void onStopTrackingTouch(SeekBar bar){editing=false;changed.saved(slider.getProgress()/100.0);}});
  LinearLayout buttons=new LinearLayout(context);addView(buttons);
  for(int delta:new int[]{-1,1,0}){Button button=new Button(context);button.setText(delta<0?"-1%":delta>0?"+1%":"Mute");buttons.addView(button);button.setOnClickListener(v->{slider.setProgress(delta==0?0:Math.max(0,Math.min(100,slider.getProgress()+delta)));changed.saved(slider.getProgress()/100.0);});}
 }
 private void label(){value.setText(slider.getProgress()+"%");}
 public void refresh(double level){if(!editing)slider.setProgress((int)Math.round(level*100));}
}
