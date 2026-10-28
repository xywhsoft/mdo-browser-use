    void pause(){views.background(true);}
    void resume(){views.background(false);}
    void destroy(){
        stopped=true;channel.close();if(port!=null)port.close();
        List<WebView> owned=views.values();views.close();
        for(WebView web:owned){root.removeView(web);web.destroy();}
        manual.clear();loading.clear();errors.clear();errorCodes.clear();crashed.clear();locations.clear();
    }
