package com.wallpanel.audioreceiver;

/** Fixed private protocol; commands are tied to the exact native source session. */
public final class RemoteProtocol {
    public static String verb(String command){
        if("play".equals(command))return "PLAY";
        if("pause".equals(command))return "PAUSE";
        if("play_pause".equals(command))return "TOGGLE";
        if("next".equals(command))return "NEXT";
        if("previous".equals(command))return "PREVIOUS";
        throw new IllegalArgumentException("Unknown source control command");
    }
    public static final class Status {
        public final long session;public final String playback;
        Status(long token,String state){session=token;playback=state;}
    }
    public static Status status(String reply){
        if(reply==null||!reply.matches("OK AP2 [0-9]{1,10} (playing|paused)"))return null;
        String[] parts=reply.split(" ");long token=Long.parseLong(parts[2]);
        return token>2147483647L?null:new Status(token,parts[3]);
    }
    public static boolean acknowledged(String reply,long session){return ("OK ACK "+session).equals(reply);}
    public static String error(String reply){
        if("ERR STALE".equals(reply))return "The AirPlay source changed; command cancelled";
        if("ERR BUSY".equals(reply))return "AirPlay source control is busy";
        if("ERR REJECTED".equals(reply))return "The AirPlay source did not acknowledge the command";
        return "No controllable AirPlay 2 source is connected";
    }
    private RemoteProtocol(){}
}
