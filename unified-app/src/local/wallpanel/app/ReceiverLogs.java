package local.wallpanel.app;
import java.io.*;
/** Recent log snapshot; the live Status panel is independent of these diagnostics. */
final class ReceiverLogs {
    static String tail(File file)throws IOException{
        if(!file.isFile())return "No receiver logs yet.";
        try(RandomAccessFile input=new RandomAccessFile(file,"r")){
            long size=input.length(),start=Math.max(0,size-65536);input.seek(start);
            byte[] bytes=new byte[(int)(size-start)];input.readFully(bytes);
            int at=0;if(start>0){while(at<bytes.length&&bytes[at]!='\n')at++;if(at<bytes.length)at++;}
            String[] lines=new String(bytes,at,bytes.length-at,"UTF-8").split("\n");StringBuilder result=new StringBuilder();
            for(int i=Math.max(0,lines.length-100);i<lines.length;i++)result.append(lines[i]).append('\n');
            return result.toString().trim().isEmpty()?"No recent complete log lines.":result.toString();
        }
    }
    private ReceiverLogs(){}
}
