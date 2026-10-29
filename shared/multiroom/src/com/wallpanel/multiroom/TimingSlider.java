package com.wallpanel.multiroom;
import android.content.Context;
import android.widget.*;

/** Signed millisecond slider with fine adjustments, shared by settings screens only. */
public final class TimingSlider extends LinearLayout {
 public interface Changed {void saved(int milliseconds);}
 private final SeekBar slider;private final TextView value;private final Changed changed;private boolean editing;
 public TimingSlider(Context context,String title,int initial,Changed listener){
  super(context);changed=listener;setOrientation(VERTICAL);
  TextView heading=new TextView(context);heading.setText(title);heading.setPadding(0,16,0,4);addView(heading);
  value=new TextView(context);addView(value);
  LinearLayout row=new LinearLayout(context);addView(row);
  Button minus=new Button(context);minus.setText("-5");row.addView(minus);
  slider=new SeekBar(context);slider.setMax(1000);row.addView(slider,new LinearLayout.LayoutParams(0,-2,1));
  Button plus=new Button(context);plus.setText("+5");row.addView(plus);
  Button reset=new Button(context);reset.setText("Reset");row.addView(reset);
  slider.setProgress(Math.max(-500,Math.min(500,initial))+500);label();
  slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
   public void onProgressChanged(SeekBar bar,int progress,boolean user){label();}
   public void onStartTrackingTouch(SeekBar bar){editing=true;}
   public void onStopTrackingTouch(SeekBar bar){editing=false;commit();}
  });
  minus.setOnClickListener(v->set(value()-5));plus.setOnClickListener(v->set(value()+5));reset.setOnClickListener(v->set(0));
 }
 public int value(){return slider.getProgress()-500;}
 public void refresh(int ms){if(!editing&&ms!=value())slider.setProgress(Math.max(-500,Math.min(500,ms))+500);}
 private void label(){int ms=value();value.setText((ms>0?"+":"")+ms+" ms"+(ms>0?" (later)":ms<0?" (earlier)":" (no offset)"));slider.setContentDescription("Audio timing "+value.getText());}
 private void set(int ms){slider.setProgress(Math.max(-500,Math.min(500,ms))+500);commit();}
 private void commit(){if(changed!=null)changed.saved(value());}
}
