package com.wallpanel.audioreceiver;

/** The same embedded receiver status in the dedicated and unified products. */
public final class ReceiverStatusText {
    public static String format(ReceiverState state){
        StringBuilder text=new StringBuilder();
        text.append("State: ").append(state.getState().name()).append('\n');
        text.append("PTP lease: ").append(state.isRootAvailable()?"\u2713":"\u2717").append('\n');
        text.append("IP: ").append(state.getLocalIp()!=null?state.getLocalIp():"unknown").append('\n');
        text.append("Multicast Lock: ").append(state.isMulticastLockHeld()?"\u2713":"\u2717").append('\n');
        if(state.isPlaying()){
            text.append("\n--- Streaming ---\n");
            if(state.getTitle()!=null)text.append("Title: ").append(state.getTitle()).append('\n');
            if(state.getArtist()!=null)text.append("Artist: ").append(state.getArtist()).append('\n');
            if(state.getClientModel()!=null)text.append("From: ").append(state.getClientModel()).append('\n');
            text.append("Underruns: ").append(state.getUnderruns()).append('\n');
            text.append("Drift Corrections: ").append(state.getDriftCorrections()).append('\n');
        }
        long start=state.getUptimeStartMs();
        text.append("\nUptime: ").append(start>0?Math.max(0,(System.currentTimeMillis()-start)/1000):0).append("s\n");
        java.util.Map<String,Object> timing=state.getAudioTiming();
        if(timing.containsKey("clock")){
            text.append("Playback timing: ").append(Boolean.TRUE.equals(timing.get("timestampValid"))?"Audio timestamp available":"Estimating; timestamp not stable").append('\n');
            text.append("Output underruns: ").append(timing.get("underruns")).append('\n');
        }
        text.append("Restarts: ").append(state.getRestarts()).append('\n');
        if(state.getErrorMessage()!=null)text.append("\nError: ").append(state.getErrorMessage()).append('\n');
        return text.toString();
    }
    private ReceiverStatusText(){}
}
