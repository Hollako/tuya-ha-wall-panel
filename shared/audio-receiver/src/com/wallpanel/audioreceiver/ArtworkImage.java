package com.wallpanel.audioreceiver;
import android.graphics.*;
import java.io.ByteArrayOutputStream;

/** Bounded decoding off the UI/audio threads; preserve the sender's aspect ratio. */
public final class ArtworkImage {
    public final byte[] jpeg,rgb;
    private ArtworkImage(byte[] jpeg,byte[] rgb){this.jpeg=jpeg;this.rgb=rgb;}
    public static ArtworkImage decode(byte[] data){
        if(data==null||data.length==0||data.length>4*1024*1024)return null;
        Bitmap decoded=null,thumb=null,knob=null;
        try{
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,options);
            if(options.outWidth<1||options.outHeight<1||(long)options.outWidth*options.outHeight>16777216)return null;
            options.inSampleSize=1;while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>512)options.inSampleSize*=2;
            options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;decoded=BitmapFactory.decodeByteArray(data,0,data.length,options);if(decoded==null)return null;
            float scale=Math.min(1f,256f/Math.max(decoded.getWidth(),decoded.getHeight()));
            thumb=Bitmap.createScaledBitmap(decoded,Math.max(1,Math.round(decoded.getWidth()*scale)),Math.max(1,Math.round(decoded.getHeight()*scale)),true);
            ByteArrayOutputStream output=new ByteArrayOutputStream();thumb.compress(Bitmap.CompressFormat.JPEG,80,output);
            if(output.size()<4||output.size()>65536)return null;
            knob=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(knob);canvas.drawColor(Color.BLACK);
            scale=80f/Math.max(thumb.getWidth(),thumb.getHeight());float width=thumb.getWidth()*scale,height=thumb.getHeight()*scale;
            canvas.drawBitmap(thumb,null,new RectF((80-width)/2,(80-height)/2,(80+width)/2,(80+height)/2),new Paint(Paint.FILTER_BITMAP_FLAG));
            int[] pixels=new int[6400];knob.getPixels(pixels,0,80,0,0,80,80);byte[] rgb=new byte[19200];
            for(int i=0;i<pixels.length;i++){rgb[i*3]=(byte)(pixels[i]>>16);rgb[i*3+1]=(byte)(pixels[i]>>8);rgb[i*3+2]=(byte)pixels[i];}
            return new ArtworkImage(output.toByteArray(),rgb);
        }catch(RuntimeException|OutOfMemoryError invalid){return null;}
        finally{if(knob!=null)knob.recycle();if(thumb!=null&&thumb!=decoded)thumb.recycle();if(decoded!=null)decoded.recycle();}
    }
}
