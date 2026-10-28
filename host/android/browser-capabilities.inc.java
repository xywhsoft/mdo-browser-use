    private JSONObject browserUseSupport;
    private JSONObject browserUseSupport() throws JSONException {
        if(browserUseSupport!=null)return browserUseSupport;
        boolean isolated=false,profiles=false;String reason="provider_loader_unsupported";
        if(android.os.Build.VERSION.SDK_INT>=28)try{
            MdoBrowserWorld.Features features=MdoBrowserWorld.inspect(WebView.getWebViewClassLoader());
            isolated=features.isolatedWorld;profiles=features.profiles;
            reason=isolated?"android_adapter_incomplete":"isolated_world_unsupported";
        }catch(MdoBrowserWorld.Failure failure){reason="world_provider_unavailable";}
        // Feature support is not product readiness. Do not enable automation
        // until native input/capture/navigation/transfer contracts are integrated.
        browserUseSupport=new JSONObject().put("available",false).put("reason",reason)
            .put("isolated_world_feature",isolated).put("profile_feature",profiles);
        return browserUseSupport;
    }
    JSONObject capabilities() throws JSONException {
        return new JSONObject().put("native",true).put("snapshot",true).put("screenshot",true)
            .put("input",true).put("profile_isolation",false).put("browser_use",browserUseSupport());
    }
    void automationAdmission(JSONObject request) throws JSONException {
        String command=request.optString("command");
        boolean agent=request.optBoolean("agent")&&!Arrays.asList("capabilities","state","bounds","stop","close","takeover","release","capture_cancel").contains(command);
        if(agent||"create".equals(command)&&(request.optBoolean("automation")||request.optBoolean("intercept_downloads")))
            throw new UnsupportedOperationException(browserUseSupport().getString("reason"));
    }

