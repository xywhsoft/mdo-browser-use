    void pause(){browserPaused=true;blockDocuments(MdoBrowserDocument.BACKGROUND,true);views.background(true);}
    void resume(){browserPaused=false;views.background(false);blockDocuments(MdoBrowserDocument.BACKGROUND,false);}
    void destroy(){
        stopped=true;channel.close();if(port!=null)port.close();
        List<WebView> owned=views.values();views.close();
        for(MdoBrowserDocument document:new ArrayList<>(documents.values()))document.close();documents.clear();documentFailures.clear();
        for(WebView web:owned){root.removeView(web);web.destroy();}
        manual.clear();loading.clear();errors.clear();errorCodes.clear();crashed.clear();locations.clear();
    }
