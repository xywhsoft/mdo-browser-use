        this.activity=activity; this.root=root; this.host=host;
        this.views=new MdoBrowserLifecycle<>((view,epoch,admitted)->new MdoBrowserInput(
            new MdoBrowserInputTarget(view,epoch,()->!stopped&&admitted.getAsBoolean()&&channel.current()!=null&&
                channel.active(channel.current(),host.getUrl())),new MdoBrowserInput.Scheduler(){
                private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
                public long now(){return android.os.SystemClock.uptimeMillis();}
                public void post(Runnable task,long delay){if(!handler.postDelayed(task,delay))throw new IllegalStateException("Native input scheduler stopped");}
                public void remove(Runnable task){handler.removeCallbacks(task);}
            }),cdpViews::invalidate);
