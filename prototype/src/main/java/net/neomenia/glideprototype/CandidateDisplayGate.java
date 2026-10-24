package net.neomenia.glideprototype;

import android.os.Handler;

/** Coalesce dictionary and AI results without blocking input or inference. */
final class CandidateDisplayGate {
    private final Handler handler;
    private final Runnable render;
    private final Runnable timeout=this::release;
    private boolean open,cancelled;

    static int waitMillis(boolean enabled,boolean variable,String reading){
        return !enabled?0:20+(variable?reading.codePointCount(0,reading.length())*2:0);
    }
    CandidateDisplayGate(Handler handler,int waitMillis,Runnable render){
        this.handler=handler;this.render=render;open=waitMillis<=0;
        if(!open)handler.postDelayed(timeout,waitMillis);
    }
    void update(){if(open&&!cancelled)render.run();}
    void release(){
        if(cancelled||open)return;
        handler.removeCallbacks(timeout);open=true;render.run();
    }
    void cancel(){cancelled=true;handler.removeCallbacks(timeout);}
}
