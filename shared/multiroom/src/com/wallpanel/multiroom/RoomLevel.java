package com.wallpanel.multiroom;
/** One room percentage; software only fills the gap within an Android hardware step. */
public final class RoomLevel {
 public static int percent(double value){if(Double.isNaN(value)||Double.isInfinite(value)||value<0||value>1)throw new IllegalArgumentException("Volume must be 0 to 100%");return (int)Math.round(value*100);}
 public static int index(int percent,int maximum){return percent==0?0:Math.max(1,(int)Math.ceil(percent*maximum/100.0));}
 public static double gain(int percent,int maximum){int index=index(percent,maximum);return index==0?0:Math.min(1,percent*maximum/(100.0*index));}
 private RoomLevel(){}
}
