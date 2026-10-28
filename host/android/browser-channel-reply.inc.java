    private void reply(MdoBrowserChannel<WebMessagePort>.Lease lease,String id,Object result,String error){
        if(stopped||port==null||channel.current()!=lease||!channel.active(lease,host.getUrl()))return;reply(id,result,error);
    }
    void detach(){
        if(stopped)return;channel.invalidate();WebMessagePort previous=port;port=null;
        cancelDialogs();
        if(previous!=null)previous.close();views.stopAll();blockDocuments(MdoBrowserDocument.CHANNEL,true);
    }
