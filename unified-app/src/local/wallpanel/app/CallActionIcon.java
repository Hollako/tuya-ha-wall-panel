package local.wallpanel.app;
import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small native vector icons; no font glyph or downloaded image dependency. */
final class CallActionIcon extends Drawable {
    static final int ANSWER=0,HANGUP=1,MIC=2,MIC_OFF=3,DOOR=4;
    private final int kind;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    CallActionIcon(int type,int size){kind=type;setBounds(0,0,size,size);paint.setColor(Color.WHITE);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
    @Override public void draw(Canvas canvas){Rect b=getBounds();int save=canvas.save();canvas.translate(b.left,b.top);canvas.scale(b.width()/24f,b.height()/24f);
        if(kind==ANSWER||kind==HANGUP){paint.setStyle(Paint.Style.FILL);if(kind==HANGUP)canvas.rotate(135,12,12);Path p=new Path();p.moveTo(6.62f,10.79f);p.cubicTo(8.06f,13.62f,10.38f,15.94f,13.21f,17.38f);p.lineTo(15.41f,15.18f);p.cubicTo(15.68f,14.91f,16.08f,14.82f,16.43f,14.94f);p.cubicTo(17.55f,15.31f,18.76f,15.51f,20,15.51f);p.cubicTo(20.55f,15.51f,21,15.96f,21,16.51f);p.lineTo(21,20);p.cubicTo(21,20.55f,20.55f,21,20,21);p.cubicTo(10.61f,21,3,13.39f,3,4);p.cubicTo(3,3.45f,3.45f,3,4,3);p.lineTo(7.5f,3);p.cubicTo(8.05f,3,8.5f,3.45f,8.5f,4);p.cubicTo(8.5f,5.25f,8.7f,6.45f,9.07f,7.57f);p.cubicTo(9.18f,7.92f,9.1f,8.31f,8.82f,8.59f);p.close();canvas.drawPath(p,paint);
        }else if(kind==MIC||kind==MIC_OFF){paint.setStyle(Paint.Style.STROKE);canvas.drawRoundRect(new RectF(9,3,15,14),3,3,paint);canvas.drawArc(new RectF(6,6,18,18),0,180,false,paint);canvas.drawLine(12,18,12,21,paint);canvas.drawLine(9,21,15,21,paint);if(kind==MIC_OFF)canvas.drawLine(3,3,21,21,paint);
        }else{paint.setStyle(Paint.Style.STROKE);canvas.drawArc(new RectF(8,3,16,13),180,180,false,paint);canvas.drawLine(8,8,8,10,paint);canvas.drawLine(16,8,16,10,paint);canvas.drawRoundRect(new RectF(6,10,18,21),2,2,paint);canvas.drawCircle(12,15,1,paint);canvas.drawLine(12,16,12,18,paint);}
        canvas.restoreToCount(save);
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
