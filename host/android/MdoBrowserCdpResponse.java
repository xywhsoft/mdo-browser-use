package org.xserver.android;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Bounded, immutable metadata from an original paused event. This is not a
 * request selector, stream claim or permission. It retains no URL, request,
 * headers, credentials, filename or caller-owned JSON. No content sniff/refetch.
 * Only explicit attachments/known binary Document responses are candidates;
 * native download-intent/provider qualification remains a separate gate. */
final class MdoBrowserCdpResponse {
    final String kind,reason;final int status;final long declaredLength;final boolean exactTextLength;
    private MdoBrowserCdpResponse(String kind,String reason,int status,long length,boolean exactTextLength){
        this.kind=kind;this.reason=reason;this.status=status;declaredLength=length;this.exactTextLength=exactTextLength;
    }
    private static MdoBrowserCdpResponse pass(String reason,int status){return new MdoBrowserCdpResponse("pass",reason,status,-1,false);}
    private static MdoBrowserCdpResponse invalid(){return new MdoBrowserCdpResponse("invalid","response_metadata_invalid",0,-1,false);}
    private static boolean token(String value){
        if(value.isEmpty())return false;
        for(int i=0;i<value.length();i++){char c=value.charAt(i);
            if(!(c>='a'&&c<='z'||c>='A'&&c<='Z'||c>='0'&&c<='9'||"!#$%&'*+-.^_`|~".indexOf(c)>=0))return false;
        }return true;
    }
    private static int whitespace(String value,int i){while(i<value.length()&&(value.charAt(i)==' '||value.charAt(i)=='\t'))i++;return i;}
    private static String trim(String value){int start=whitespace(value,0),end=value.length();while(end>start&&(value.charAt(end-1)==' '||value.charAt(end-1)=='\t'))end--;return value.substring(start,end);}
    private static int bytes(String value){
        int count=0;for(int i=0;i<value.length();i++){char c=value.charAt(i);
            if(c<32&&c!='\t'||c==127)return -1;
            if(Character.isHighSurrogate(c)){if(i+1==value.length()||!Character.isLowSurrogate(value.charAt(++i)))return -1;count+=4;}
            else if(Character.isLowSurrogate(c))return -1;else count+=c<128?1:c<2048?2:3;
        }return count;
    }
    /** Validate parameter syntax without decoding or retaining any value. */
    private static String parameterized(String value,boolean media){
        int semicolon=value.indexOf(';');String head=trim(semicolon<0?value:value.substring(0,semicolon));
        if(media){int slash=head.indexOf('/');if(slash<=0||!token(head.substring(0,slash))||!token(head.substring(slash+1)))return null;}
        else if(!token(head))return null;
        Set<String> names=new HashSet<>();int i=semicolon<0?value.length():semicolon;
        while(i<value.length()){
            if(value.charAt(i++)!=';')return null;i=whitespace(value,i);int start=i;
            while(i<value.length()&&value.charAt(i)!='='&&value.charAt(i)!=' '&&value.charAt(i)!='\t'&&value.charAt(i)!=';')i++;
            String name=value.substring(start,i);if(!token(name)||names.size()>=32||!names.add(name.toLowerCase(Locale.ROOT)))return null;
            i=whitespace(value,i);if(i==value.length()||value.charAt(i++)!='=')return null;i=whitespace(value,i);
            if(i==value.length())return null;
            if(value.charAt(i)=='"'){
                i++;boolean closed=false;while(i<value.length()){
                    char c=value.charAt(i++);if(c=='"'){closed=true;break;}
                    if(c=='\\'){if(i==value.length())return null;i++;}
                }if(!closed)return null;
            }else{
                start=i;while(i<value.length()&&value.charAt(i)!=';'&&value.charAt(i)!=' '&&value.charAt(i)!='\t')i++;
                if(!token(value.substring(start,i)))return null;
            }
            i=whitespace(value,i);if(i<value.length()&&value.charAt(i)!=';')return null;
        }
        return head.toLowerCase(Locale.ROOT);
    }
    private static long length(String value){
        value=trim(value);if(value.isEmpty())return -1;long result=0;
        for(int i=0;i<value.length();i++){int digit=value.charAt(i)-'0';
            if(digit<0||digit>9||result>(Long.MAX_VALUE-digit)/10)return -1;result=result*10+digit;
        }return result;
    }
    static MdoBrowserCdpResponse inspect(JSONObject event){
        if(event==null)return invalid();
        boolean hasStatus=event.has("responseStatusCode"),hasError=event.has("responseErrorReason");
        if(!hasStatus&&!hasError)return pass("request_stage",0);
        if(hasError){Object error=event.opt("responseErrorReason");
            return !hasStatus&&error instanceof String&&token((String)error)&&((String)error).length()<=64?pass("response_failed",0):invalid();
        }
        Object number=event.opt("responseStatusCode");if(!(number instanceof Integer)&&!(number instanceof Long))return invalid();
        long raw=((Number)number).longValue();if(raw<100||raw>599)return invalid();int status=(int)raw;
        if(status<200||status>=300)return pass("response_status",status);
        if(status==204||status==205||status==206)return pass("response_no_full_body",status);
        Object resource=event.opt("resourceType");if(!(resource instanceof String))return invalid();
        if(!"Document".equals(resource))return pass("subresource",status);
        Object request=event.opt("request");if(!(request instanceof JSONObject))return invalid();
        Object method=((JSONObject)request).opt("method");if(!(method instanceof String)||((String)method).length()>32||!token((String)method))return invalid();
        if("HEAD".equalsIgnoreCase((String)method)||"CONNECT".equalsIgnoreCase((String)method))return pass("response_no_full_body",status);
        Object headers=event.opt("responseHeaders");if(!(headers instanceof JSONArray)||((JSONArray)headers).length()>128)return invalid();
        JSONArray entries=(JSONArray)headers;String disposition=null,type=null;long declared=-1;
        boolean hasLength=false,hasEncoding=false,identityEncoding=true,transferEncoded=false;int total=0;
        for(int i=0;i<entries.length();i++){
            Object row=entries.opt(i);if(!(row instanceof JSONObject))return invalid();
            Object name=((JSONObject)row).opt("name"),value=((JSONObject)row).opt("value");
            if(!(name instanceof String)||!(value instanceof String))return invalid();
            String n=(String)name,v=(String)value;if(n.length()>256||!token(n)||v.length()>8192)return invalid();
            int size=bytes(v);if(size<0||size>65536-total-n.length())return invalid();total+=size+n.length();
            if(n.equalsIgnoreCase("Content-Disposition")){if(disposition!=null)return invalid();disposition=parameterized(v,false);if(disposition==null)return invalid();}
            else if(n.equalsIgnoreCase("Content-Type")){if(type!=null)return invalid();type=parameterized(v,true);if(type==null)return invalid();}
            else if(n.equalsIgnoreCase("Content-Length")){if(hasLength)return invalid();hasLength=true;declared=length(v);if(declared<0)return invalid();}
            else if(n.equalsIgnoreCase("Content-Encoding")){if(hasEncoding)return invalid();hasEncoding=true;String coding=trim(v);identityEncoding=token(coding)&&coding.equalsIgnoreCase("identity");}
            else if(n.equalsIgnoreCase("Transfer-Encoding"))transferEncoded=true;
        }
        boolean attachment="attachment".equals(disposition);
        boolean binary="application/octet-stream".equals(type)||"application/x-download".equals(type)||"application/force-download".equals(type);
        if(attachment||binary)return new MdoBrowserCdpResponse("candidate",attachment?"attachment":"binary_document",status,declared,hasLength&&identityEncoding&&!transferEncoded);
        return pass("document",status);
    }
}
