package org.xserver.android;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.LongSupplier;

/** One worker owns all ticket state. Scope objects contain no Activity/View or
 * serializable authority; the UI retires them on exact native ownership loss. */
final class MdoBrowserUploadStore implements AutoCloseable {
    static final int TICKETS=64;
    static final class Scope {
        final long navigation;private volatile boolean cancelled;
        Scope(long navigation){if(navigation<=0)throw new IllegalArgumentException("Invalid upload epoch");this.navigation=navigation;}
        void cancel(){cancelled=true;}
        boolean live(){return !cancelled;}
    }
    static final class Spec {
        final String name,sha256;final long size;
        Spec(String name,long size,String sha256)throws MdoBrowserUploadFiles.Failure {
            if(!safeName(name)||size<0||size>MdoBrowserUploadFiles.FILE_BYTES||sha256==null||!sha256.matches("[a-f0-9]{64}"))throw fail("upload_metadata_invalid");
            this.name=name;this.size=size;this.sha256=sha256;
        }
        boolean same(Spec other){return other!=null&&size==other.size&&name.equals(other.name)&&sha256.equals(other.sha256);}
    }
    static boolean bounded(String text,int maximum){
        if(text==null||text.isEmpty())return false;int bytes=0;
        for(int at=0;at<text.length();){int code=text.codePointAt(at);if(code<32||code==127||code>=0xd800&&code<=0xdfff)return false;
            bytes+=code<128?1:code<2048?2:code<65536?3:4;if(bytes>maximum)return false;at+=Character.charCount(code);}
        return true;
    }
    static boolean safeName(String name){
        if(!bounded(name,240)||name.endsWith(".")||name.endsWith(" "))return false;
        for(int at=0;at<name.length();at++)if("<>:\"/\\|?*".indexOf(name.charAt(at))>=0)return false;
        String stem=name.split("\\.",2)[0].toUpperCase(java.util.Locale.ROOT);
        if(stem.endsWith(" ")||Arrays.asList("CON","PRN","AUX","NUL","CONIN$","CONOUT$","CLOCK$").contains(stem))return false;
        return !(stem.length()==4&&(stem.startsWith("COM")||stem.startsWith("LPT"))&&"0123456789\u00b9\u00b2\u00b3".indexOf(stem.charAt(3))>=0);
    }
    static void manifest(Spec[] specs)throws MdoBrowserUploadFiles.Failure {
        if(specs==null||specs.length<1||specs.length>16)throw fail("upload_metadata_invalid");
        long total=0;for(Spec spec:specs){if(spec==null)throw fail("upload_metadata_invalid");total+=spec.size;}
        if(total>67108864L)throw fail("upload_budget_exceeded");
    }
    static final class Snapshot {
        final String ticket,key,state;final long navigation,expires,size;final Spec[] files;final long[] received;
        final boolean sealed,cleanupConfirmed;
        Snapshot(Job job){
            ticket=job.id;key=job.key;navigation=job.scope.navigation;expires=job.expires;size=job.size;
            files=job.specs.clone();received=job.received.clone();sealed=job.sealed&&!job.failed&&!job.closing&&!job.retired;
            state=job.retired?"released":job.closing?"cleanup_pending":job.failed?"failed":sealed?"sealed":"receiving";cleanupConfirmed=job.retired;
        }
    }
    private static final class Job {
        final String id="u-"+UUID.randomUUID(),key;final Scope scope;final long expires,size;final Spec[] specs;
        final MdoBrowserUploadFiles.Leaf[] files;final long[] received,lastOffset;final byte[][] lastHash;final int[] lastSize;
        boolean sealed,failed,closing,retired;
        Job(Scope scope,String key,Spec[] specs,long expires){
            this.scope=scope;this.key=key;this.specs=specs.clone();this.expires=expires;
            files=new MdoBrowserUploadFiles.Leaf[specs.length];received=new long[specs.length];lastOffset=new long[specs.length];lastHash=new byte[specs.length][];lastSize=new int[specs.length];
            long total=0;for(Spec spec:specs)total+=spec.size;size=total;
        }
        boolean same(Spec[] values){if(values.length!=specs.length)return false;for(int at=0;at<values.length;at++)if(!specs[at].same(values[at]))return false;return true;}
    }
    private final Thread owner=Thread.currentThread();private final MdoBrowserUploadFiles files;private final LongSupplier clock;private final Job[] jobs=new Job[TICKETS];
    private boolean closed;
    MdoBrowserUploadStore(MdoBrowserUploadFiles files,LongSupplier clock){if(files==null||clock==null)throw new IllegalArgumentException("Missing upload files/clock");this.files=files;this.clock=clock;}
    private static MdoBrowserUploadFiles.Failure fail(String code){return new MdoBrowserUploadFiles.Failure(code);}
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Upload tickets require their owning worker thread");}
    private void active()throws MdoBrowserUploadFiles.Failure {thread();if(closed)throw fail("upload_store_closed");}
    private void live(Scope scope,long expires)throws MdoBrowserUploadFiles.Failure {
        active();if(scope==null||!scope.live())throw fail("upload_owner_changed");long now=clock.getAsLong();if(now<0||now>=expires)throw fail("upload_expired");
    }
    private Job exact(Scope scope,String id)throws MdoBrowserUploadFiles.Failure {
        active();for(Job job:jobs)if(job!=null&&job.scope==scope&&job.id.equals(id))return job;throw fail("upload_ticket_not_found");
    }
    private void writable(Job job)throws MdoBrowserUploadFiles.Failure {
        live(job.scope,job.expires);if(job.closing||job.retired||job.failed)throw fail("upload_not_receiving");
    }
    private boolean release(Job job){
        job.closing=true;boolean clean=true;
        for(int at=0;at<job.files.length;at++)if(job.files[at]!=null)try{job.files[at].close();job.files[at]=null;}catch(IOException|RuntimeException failure){clean=false;}
        if(clean){job.retired=true;job.lastHash[0]=null;for(int at=1;at<job.lastHash.length;at++)job.lastHash[at]=null;}
        return clean;
    }
    Snapshot begin(Scope scope,String key,Spec[] specs,long expires)throws IOException {
        live(scope,expires);manifest(specs);long now=clock.getAsLong();
        if(!bounded(key,100)||expires-now>120000L)throw fail("upload_metadata_invalid");
        collect(now);
        for(Job old:jobs)if(old!=null&&old.scope==scope&&old.key.equals(key)){
            if(!old.same(specs))throw fail("upload_identity_conflict");
            if(old.retired||old.closing||old.failed)throw fail("upload_operation_retired");return new Snapshot(old);
        }
        int slot=0;while(slot<jobs.length&&jobs[slot]!=null)slot++;if(slot==jobs.length)throw fail("upload_operation_limit");
        Job job=new Job(scope,key,specs,expires);jobs[slot]=job;
        try{
            for(int at=0;at<job.files.length;at++){
                live(scope,expires);
                try{job.files[at]=files.create(job.specs[at].size,job.specs[at].sha256);}
                catch(IOException|RuntimeException|Error failure){job.files[at]=files.takeFailedCreation();throw failure;}
            }
            live(scope,expires);return new Snapshot(job);
        }catch(IOException|RuntimeException|Error failure){job.failed=true;release(job);throw failure;}
    }
    Snapshot chunk(Scope scope,String id,int index,long offset,byte[] bytes)throws IOException {
        Job job=exact(scope,id);writable(job);if(job.sealed)throw fail("upload_already_sealed");
        if(index<0||index>=job.files.length||bytes==null||bytes.length<1||bytes.length>MdoBrowserUploadFiles.CHUNK)throw fail("upload_chunk_invalid");
        byte[] hash;try{hash=MessageDigest.getInstance("SHA-256").digest(bytes);}catch(NoSuchAlgorithmException impossible){throw fail("upload_hash_unavailable");}
        if(offset==job.lastOffset[index]&&job.lastHash[index]!=null&&bytes.length==job.lastSize[index]&&job.received[index]==offset+bytes.length){
            if(!Arrays.equals(hash,job.lastHash[index]))throw fail("upload_chunk_conflict");return new Snapshot(job);
        }
        if(offset!=job.received[index]||bytes.length>job.specs[index].size-offset)throw fail("upload_chunk_invalid");
        try{
            job.files[index].append(offset,bytes);job.received[index]+=bytes.length;job.lastOffset[index]=offset;job.lastHash[index]=hash;job.lastSize[index]=bytes.length;
            live(scope,job.expires);return new Snapshot(job);
        }catch(IOException|RuntimeException|Error failure){job.failed=true;release(job);throw failure;}
    }
    Snapshot seal(Scope scope,String id)throws IOException {
        Job job=exact(scope,id);writable(job);
        for(int at=0;at<job.files.length;at++)if(job.received[at]!=job.specs[at].size)throw fail("upload_incomplete");
        try{
            for(MdoBrowserUploadFiles.Leaf file:job.files){live(scope,job.expires);file.seal();file.verify();}
            live(scope,job.expires);job.sealed=true;return new Snapshot(job);
        }catch(IOException|RuntimeException|Error failure){job.failed=true;release(job);throw failure;}
    }
    Snapshot status(Scope scope,String id,long now)throws IOException {
        thread();try{collect(now);}catch(IOException ignored){/* Preserve independently inspectable ticket state. */}
        return new Snapshot(exact(scope,id));
    }
    Snapshot cancel(Scope scope,String id)throws IOException {Job job=exact(scope,id);if(!release(job))throw fail("upload_cleanup_failed");return new Snapshot(job);}
    void collect(long now)throws IOException {
        thread();boolean clean=true;
        for(int at=0;at<jobs.length;at++){
            Job job=jobs[at];if(job==null)continue;
            if(job.closing||!job.scope.live()||now>=job.expires){if(!release(job))clean=false;}
            if(job.retired&&!job.scope.live())jobs[at]=null;
        }
        try{files.collectFailed();}catch(IOException failure){clean=false;}
        if(!clean)throw fail("upload_cleanup_failed");
    }
    int reservations(){thread();return files.reservations();}
    long reservedBytes(){thread();return files.reservedBytes();}
    @Override public void close()throws IOException {
        thread();closed=true;boolean clean=true;for(Job job:jobs)if(job!=null&&!release(job))clean=false;
        if(!clean)throw fail("upload_cleanup_failed");files.close();
    }
}
