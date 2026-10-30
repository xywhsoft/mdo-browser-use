package android.webkit;

import android.os.Looper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A narrow framework seam. It recognizes only the fixed native housekeeping
 * forms. No DOM/browser/provider behavior is simulated as a qualification. */
public final class WebView {
    public static final String UNDEFINED="__mdo_test_undefined";
    public final Map<String,String> lexical=new ConcurrentHashMap<>();
    public final List<ValueCallback<String>> pending=new ArrayList<>();
    private final WebSettings settings=new WebSettings();
    public boolean defer,throwAfterRetain,badReply,shown=true;public int evaluations,cleanup;
    public boolean isShown(){return shown;}
    public Runnable beforeReply;
    public WebSettings getSettings(){return settings;}
    public void evaluateJavascript(String source,ValueCallback<String> reply) {
        if(Looper.myLooper()!=Looper.getMainLooper())throw new AssertionError("UI expression ran on worker");
        Matcher declaration=Pattern.compile("let (__mdo_cdp_[0-9a-f]{32})=\"([0-9a-f]{32})\";\\1\\n//# sourceURL=mdo-native-binding\\.js").matcher(source);
        Matcher clear=Pattern.compile("if\\(typeof (__mdo_cdp_[0-9a-f]{32})!==\"undefined\"\\)\\1=void 0\\n//# sourceURL=mdo-native-binding\\.js").matcher(source);
        if(declaration.matches()) {
            evaluations++;lexical.put(declaration.group(1),declaration.group(2));
            if(beforeReply!=null)beforeReply.run();
            if(reply!=null) {
                if(defer||throwAfterRetain)pending.add(reply);
                else reply.onReceiveValue(badReply?"null":"\""+declaration.group(2)+"\"");
            }
            if(throwAfterRetain)throw new IllegalStateException("Synthetic retained-callback failure");
        } else if(clear.matches()) {cleanup++;if(lexical.containsKey(clear.group(1)))lexical.put(clear.group(1),UNDEFINED);}
        else throw new AssertionError("Unexpected native housekeeping syntax");
    }
    public void finish(String value){List<ValueCallback<String>> callbacks=new ArrayList<>(pending);pending.clear();for(ValueCallback<String> callback:callbacks)callback.onReceiveValue(value);}
}
