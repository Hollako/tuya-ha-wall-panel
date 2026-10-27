package com.wallpanel.audioreceiver;

public final class AudioOutputGateTest {
    private static int checks;
    static void check(boolean value){checks++;if(!value)throw new AssertionError("check "+checks);}
    public static void main(String[] args)throws Exception{
        final AudioOutputGate gate=new AudioOutputGate();final boolean[] audible={true};
        AudioOutputGate.Sink speaker=new AudioOutputGate.Sink(){public void audible(boolean value){audible[0]=value;}};
        gate.bind(speaker);check(audible[0]);
        AudioOutputGate.Lease first=gate.hold();check(!audible[0]);
        AudioOutputGate.Lease second=gate.hold();first.close();check(!audible[0]);
        first.close();check(!audible[0]);second.close();check(audible[0]);
        gate.select(false);AudioOutputGate.Lease mutedCall=gate.hold();mutedCall.close();check(!audible[0]);
        final long[] notified={0};gate.sessions(new AudioOutputGate.Sessions(){public void started(long value){notified[0]=value;}public void ended(long value){notified[0]=-value;}});
        long old=gate.beginSession();check(notified[0]==old);check(!audible[0]);
        AudioOutputGate.Lease call=gate.hold();check(gate.selectSession(old));check(!audible[0]);
        // A new track/session arriving during ringing must also start silent.
        long next=gate.beginSession();check(!gate.selectSession(old));check(!audible[0]);
        gate.selectSession(next);check(!audible[0]);
        gate.endSession(old);check(gate.active(next));call.close();check(audible[0]);
        gate.endSession(next);check(!audible[0]);check(notified[0]==-next);
        gate.unbind(speaker);gate.select(true);check(!audible[0]);
        System.out.println("PASS: "+checks+" shared output checks: nested calls, fresh streams during ringing, stale generations, preserved pause and idempotent release");
    }
}
