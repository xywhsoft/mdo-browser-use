package org.xserver.android;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

/** Real abrupt JVM exits and directory-relative recovery in the project QA
 * cache. This is not an Android process/framework/private-cache qualification. */
public final class AndroidUploadRecoveryContractTest {
    interface Checked {void run()throws Exception;}
    private static int checks,exits;private static Path base;
    private static void check(boolean value){checks++;if(!value)throw new AssertionError("Recovery check "+checks+" failed");}
    private static void refused(String code,Checked operation)throws Exception {
        try{operation.run();throw new AssertionError("Expected "+code);}catch(MdoBrowserUploadFiles.Failure failure){check(code.equals(failure.code));}
    }
    private static String sha(byte[] bytes)throws Exception {
        StringBuilder text=new StringBuilder(64);for(byte value:MessageDigest.getInstance("SHA-256").digest(bytes))text.append(String.format(java.util.Locale.ROOT,"%02x",value&255));return text.toString();
    }
    private static byte[] data(){byte[] bytes=new byte[65539];for(int at=0;at<bytes.length;at++)bytes[at]=(byte)(at*17+3);return bytes;}
    private static Path root(String name)throws Exception {Path value=base.resolve(name);Files.createDirectory(value);return value;}
    private static List<Path> leaves(Path root)throws Exception {
        List<Path> result=new ArrayList<>();try(DirectoryStream<Path> entries=Files.newDirectoryStream(root)){
            for(Path entry:entries)if(!entry.getFileName().toString().equals(MdoBrowserUploadFiles.OWNER_FILE))result.add(entry);
        }return result;
    }
    private static void removeEmpty(Path root)throws Exception {check(leaves(root).isEmpty());Files.delete(root.resolve(MdoBrowserUploadFiles.OWNER_FILE));Files.delete(root);}
    private static Map<String,String> inventory(Path root)throws Exception {
        Map<String,String> result=new TreeMap<>();try(DirectoryStream<Path> entries=Files.newDirectoryStream(root)){
            for(Path entry:entries){BasicFileAttributes value=Files.readAttributes(entry,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                result.put(entry.getFileName().toString(),value.isRegularFile()?value.fileKey()+":"+sha(Files.readAllBytes(entry)):"non-regular:"+value.fileKey());}
        }return result;
    }
    private static void rejectedUnchanged(Path root,String code)throws Exception {
        Map<String,String> before=inventory(root);
        try(MdoBrowserUploadFiles files=new MdoBrowserUploadFiles(root)){refused(code,files::acquireCacheLease);check(files.reservations()==0);}
        check(before.equals(inventory(root)));
    }
    private static void writer(Path root,boolean limit)throws Exception {
        MdoBrowserUploadFiles files=new MdoBrowserUploadFiles(root);files.acquireCacheLease();
        if(limit){for(int at=0;at<16;at++)files.create(0,sha(new byte[0]));}
        else{
            byte[] bytes=data();MdoBrowserUploadFiles.Leaf partial=files.create(bytes.length,sha(bytes));partial.append(0,Arrays.copyOf(bytes,65536));
            MdoBrowserUploadFiles.Leaf full=files.create(3,sha(new byte[]{1,2,3}));full.append(0,new byte[]{1,2,3});full.seal();
            MdoBrowserUploadFiles.Leaf empty=files.create(0,sha(new byte[0]));empty.seal();
        }
        System.out.println("abrupt-staging-writer-ready");System.out.flush();Runtime.getRuntime().halt(0);
    }
    private static void holder(Path root)throws Exception {
        try(MdoBrowserUploadFiles files=new MdoBrowserUploadFiles(root)){
            files.acquireCacheLease();System.out.println("live-staging-owner-ready");System.out.flush();
            if(System.in.read()!=1)throw new AssertionError("Missing owner release");
        }
    }
    private static void crossProcessLease()throws Exception {
        Path root=root("live-owner");Process child=new ProcessBuilder(Paths.get(System.getProperty("java.home"),"bin/java").toString(),"-cp",System.getProperty("java.class.path"),
            AndroidUploadRecoveryContractTest.class.getName(),"holder",root.toString(),"normal").redirectErrorStream(true).start();
        try{
            InputStream input=child.getInputStream();ByteArrayOutputStream line=new ByteArrayOutputStream();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean complete=false;
            while(System.nanoTime()<deadline&&!complete){
                if(input.available()==0){Thread.sleep(5);continue;}int item=input.read();if(item<0)break;
                if(item=='\n'){complete=true;break;}if(line.size()>100)throw new AssertionError("Unexpected live owner output");line.write(item);
            }
            check(complete&&new String(line.toByteArray(),java.nio.charset.StandardCharsets.US_ASCII).trim().equals("live-staging-owner-ready"));
            rejectedUnchanged(root,"upload_cache_busy");
            child.getOutputStream().write(1);child.getOutputStream().close();check(child.waitFor(5,TimeUnit.SECONDS)&&child.exitValue()==0);
        }finally{if(child.isAlive()){child.destroyForcibly();if(!child.waitFor(5,TimeUnit.SECONDS))throw new AssertionError("Live owner did not stop");}}
        recovered(root,0,0);
    }
    private static void crash(Path root,boolean limit)throws Exception {
        Process child=new ProcessBuilder(Paths.get(System.getProperty("java.home"),"bin/java").toString(),"-cp",System.getProperty("java.class.path"),
            AndroidUploadRecoveryContractTest.class.getName(),"writer",root.toString(),limit?"limit":"normal").redirectErrorStream(true).start();
        try{
            if(!child.waitFor(10,TimeUnit.SECONDS))throw new AssertionError("Owned staging writer did not terminate");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream input=child.getInputStream()){
                byte[] block=new byte[512];int count;while((count=input.read(block))!=-1){if(bytes.size()+count>4096)throw new AssertionError("Unexpected child output");bytes.write(block,0,count);}
            }
            check(child.exitValue()==0&&new String(bytes.toByteArray(),java.nio.charset.StandardCharsets.US_ASCII).trim().equals("abrupt-staging-writer-ready"));exits++;
        }finally{if(child.isAlive()){child.destroyForcibly();if(!child.waitFor(5,TimeUnit.SECONDS))throw new AssertionError("Owned staging writer still live");}}
    }
    private static void recovered(Path root,int count,long bytes)throws Exception {
        try(MdoBrowserUploadFiles files=new MdoBrowserUploadFiles(root)){
            files.acquireCacheLease();check(files.recoveredFiles()==count&&files.recoveredBytes()==bytes);
            check(files.reservations()==0&&files.reservedBytes()==0&&leaves(root).isEmpty());
            byte[] fresh={7,8,9};MdoBrowserUploadFiles.Leaf next=files.create(3,sha(fresh));next.append(0,fresh);next.seal();next.verify();
            check(next.size()==3&&Arrays.equals(next.read(0,65536),fresh));next.close();check(files.reservations()==0);
        }removeEmpty(root);
    }
    private static MdoBrowserUploadQueue.Outcome queued(MdoBrowserUploadQueue queue,ConcurrentLinkedQueue<Runnable> callbacks,MdoBrowserUploadStore.Scope scope,MdoBrowserUploadQueue.Request request)throws Exception {
        AtomicReference<MdoBrowserUploadQueue.Outcome> result=new AtomicReference<>();check(queue.submit(scope,request,result::set));long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(result.get()==null&&System.nanoTime()<deadline){Runnable task=callbacks.poll();if(task!=null)task.run();else Thread.sleep(5);}
        check(result.get()!=null);return result.get();
    }
    private static void workerStartup()throws Exception {
        Path root=root("worker-startup");crash(root,false);ConcurrentLinkedQueue<Runnable> callbacks=new ConcurrentLinkedQueue<>();
        MdoBrowserUploadQueue queue=new MdoBrowserUploadQueue(()->{
            MdoBrowserUploadFiles files=new MdoBrowserUploadFiles(root);
            try{files.acquireCacheLease();return files;}catch(Exception|Error failure){try{files.close();}catch(Exception ignored){}throw failure;}
        },task->{callbacks.add(task);return true;},()->System.nanoTime()/1000000L);
        MdoBrowserUploadStore.Scope scope=new MdoBrowserUploadStore.Scope(1);
        try{
            MdoBrowserUploadStore.Spec[] manifest={new MdoBrowserUploadStore.Spec("new-file.txt",3,sha(new byte[]{1,2,3}))};
            MdoBrowserUploadQueue.Outcome value=queued(queue,callbacks,scope,MdoBrowserUploadQueue.Request.begin("fresh-after-process-exit",manifest,System.nanoTime()/1000000L+60000));
            check(value.ok()&&value.value.files.length==1&&value.value.received[0]==0&&queue.ready());
            check(queue.recoveredFiles()==3&&queue.recoveredBytes()==65539&&queue.activeFiles()==1&&queue.reservedBytes()==3);
            value=queued(queue,callbacks,scope,MdoBrowserUploadQueue.Request.ticket("cancel",value.value.ticket));
            check(value.ok()&&value.value.cleanupConfirmed&&queue.activeFiles()==0&&queue.reservedBytes()==0&&queue.unfinishedDeliveries()==0);
        }finally{
            scope.cancel();queue.close();check(queue.awaitStopped(5000));Runnable task;while((task=callbacks.poll())!=null)task.run();check(queue.unfinishedDeliveries()==0);
        }removeEmpty(root);
    }
    private static void streaming()throws Exception {
        Path root=root("streaming");byte[] bytes=data();
        try(MdoBrowserUploadFiles files=new MdoBrowserUploadFiles(root)){
            files.acquireCacheLease();MdoBrowserUploadFiles.Leaf leaf=files.create(bytes.length,sha(bytes));
            Path path=leaves(root).get(0);check(Files.size(path)==MdoBrowserUploadFiles.HEADER_BYTES);
            leaf.append(0,Arrays.copyOf(bytes,65536));leaf.append(65536,Arrays.copyOfRange(bytes,65536,bytes.length));leaf.seal();leaf.verify();
            check(Files.size(path)==MdoBrowserUploadFiles.HEADER_BYTES+bytes.length&&leaf.size()==bytes.length);
            check(Arrays.equals(leaf.read(0,65536),Arrays.copyOf(bytes,65536)));check(Arrays.equals(leaf.read(65536,65536),Arrays.copyOfRange(bytes,65536,bytes.length)));
            check(leaf.read(bytes.length,1).length==0);leaf.close();check(files.reservedBytes()==0&&files.reservations()==0);
        }removeEmpty(root);
    }
    private static void liveRecordChange()throws Exception {
        Path root=root("live-record");
        try(MdoBrowserUploadFiles files=new MdoBrowserUploadFiles(root)){
            files.acquireCacheLease();MdoBrowserUploadFiles.Leaf leaf=files.create(1,sha(new byte[]{1}));leaf.append(0,new byte[]{1});leaf.seal();
            Path path=leaves(root).get(0);try(FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE)){channel.write(ByteBuffer.wrap(new byte[]{0}));}
            refused("upload_file_changed",leaf::verify);check(!leaf.sealed());leaf.close();check(files.reservations()==0);
        }removeEmpty(root);
    }
    private static void restarts()throws Exception {
        Path root=root("restart");crash(root,false);check(leaves(root).size()==3);recovered(root,3,65539);
        Path limit=root("limit");crash(limit,true);check(leaves(limit).size()==16);recovered(limit,16,0);
        Path partial=root("interrupted-cleanup");crash(partial,false);
        for(Path entry:leaves(partial))if(Files.size(entry)==MdoBrowserUploadFiles.HEADER_BYTES+3)Files.delete(entry);
        check(leaves(partial).size()==2);recovered(partial,2,65536);
    }
    private static void foreignAndDamage()throws Exception {
        Path root=root("unknown");crash(root,false);Path foreign=root.resolve("unrecognized-note");Files.write(foreign,new byte[]{42});
        rejectedUnchanged(root,"upload_cache_unrecognized");check(Files.readAllBytes(foreign)[0]==42);Files.delete(foreign);recovered(root,3,65539);
        root=root("damaged-record");crash(root,false);Path path=leaves(root).get(0);byte[] original=Files.readAllBytes(path);
        try(FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE)){channel.position(MdoBrowserUploadFiles.HEADER_BYTES-1);channel.write(ByteBuffer.wrap(new byte[]{(byte)(original[MdoBrowserUploadFiles.HEADER_BYTES-1]^1)}));}
        rejectedUnchanged(root,"upload_cache_recovery_required");
        try(FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE)){channel.write(ByteBuffer.wrap(original,0,MdoBrowserUploadFiles.HEADER_BYTES));}
        recovered(root,3,65539);
        root=root("oversized");crash(root,false);path=null;for(Path entry:leaves(root))if(Files.size(entry)==MdoBrowserUploadFiles.HEADER_BYTES)path=entry;
        check(path!=null);try(FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE)){channel.position(MdoBrowserUploadFiles.HEADER_BYTES);channel.write(ByteBuffer.wrap(new byte[]{1}));}
        rejectedUnchanged(root,"upload_cache_recovery_required");try(FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE)){channel.truncate(MdoBrowserUploadFiles.HEADER_BYTES);}
        recovered(root,3,65539);
    }
    private static void replacedAndLinked()throws Exception {
        Path root=root("replacement");crash(root,false);Path path=leaves(root).get(0),saved=base.resolve("replacement-saved");
        Files.move(path,saved,StandardCopyOption.ATOMIC_MOVE);Files.write(path,new byte[]{99});
        rejectedUnchanged(root,"upload_cache_recovery_required");check(Files.readAllBytes(path)[0]==99);Files.delete(path);
        Files.copy(saved,path);check(!Files.readAttributes(path,BasicFileAttributes.class).fileKey().equals(Files.readAttributes(saved,BasicFileAttributes.class).fileKey()));
        rejectedUnchanged(root,"upload_cache_recovery_required");Files.delete(path);Files.move(saved,path,StandardCopyOption.ATOMIC_MOVE);recovered(root,3,65539);
        root=root("linked");crash(root,false);path=leaves(root).get(0);saved=base.resolve("linked-saved");Path outside=base.resolve("linked-foreign");
        Files.write(outside,new byte[]{55});Files.move(path,saved,StandardCopyOption.ATOMIC_MOVE);Files.createSymbolicLink(path,outside);
        rejectedUnchanged(root,"upload_cache_recovery_required");check(Files.isSymbolicLink(path)&&Files.readAllBytes(outside)[0]==55);
        Files.delete(path);Files.move(saved,path,StandardCopyOption.ATOMIC_MOVE);Files.delete(outside);recovered(root,3,65539);
    }
    private static void namesAndRoots()throws Exception {
        Path root=root("renamed");crash(root,false);Path original=leaves(root).get(0),renamed=root.resolve("mdo-bu-upload-"+UUID.randomUUID()+".bin");
        Files.move(original,renamed,StandardCopyOption.ATOMIC_MOVE);rejectedUnchanged(root,"upload_cache_recovery_required");Files.move(renamed,original,StandardCopyOption.ATOMIC_MOVE);recovered(root,3,65539);
        root=root("copy-source");crash(root,false);original=leaves(root).get(0);Path copy=root("copy-target"),copyFile=copy.resolve(original.getFileName());
        Files.copy(root.resolve(MdoBrowserUploadFiles.OWNER_FILE),copy.resolve(MdoBrowserUploadFiles.OWNER_FILE));Files.copy(original,copyFile);
        rejectedUnchanged(copy,"upload_cache_recovery_required");Files.delete(copyFile);
        // Preserve the leaf inode but change its owning directory identity.
        Files.move(original,copyFile,StandardCopyOption.ATOMIC_MOVE);rejectedUnchanged(copy,"upload_cache_recovery_required");
        Files.move(copyFile,original,StandardCopyOption.ATOMIC_MOVE);removeEmpty(copy);recovered(root,3,65539);
    }
    private static void malformedRecords()throws Exception {
        Path root=root("malformed");crash(root,false);Path path=leaves(root).get(0);byte[] original=Files.readAllBytes(path);
        int magic="mdo-browser-use-upload-leaf-v1\n".length();
        for(int kind=0;kind<4;kind++){
            byte[] header=Arrays.copyOf(original,MdoBrowserUploadFiles.HEADER_BYTES);ByteBuffer fields=ByteBuffer.wrap(header);
            if(kind==0)fields.putInt(magic,2);
            if(kind==1)fields.putLong(magic+4,-1);
            if(kind==2)fields.putShort(magic+12,(short)65535);
            if(kind==3)header[MdoBrowserUploadFiles.HEADER_BYTES-33]=1;
            MessageDigest digest=MessageDigest.getInstance("SHA-256");digest.update(header,0,header.length-32);
            fields.position(header.length-32);fields.put(digest.digest());
            try(FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE)){channel.write(ByteBuffer.wrap(header));}
            rejectedUnchanged(root,"upload_cache_recovery_required");
            try(FileChannel channel=FileChannel.open(path,StandardOpenOption.WRITE)){channel.write(ByteBuffer.wrap(original,0,MdoBrowserUploadFiles.HEADER_BYTES));}
        }recovered(root,3,65539);
    }
    private static void unprovedHeaders()throws Exception {
        Path root=root("legacy-raw");try(MdoBrowserUploadFiles files=new MdoBrowserUploadFiles(root)){files.acquireCacheLease();}
        Path path=root.resolve("mdo-bu-upload-"+UUID.randomUUID()+".bin");Files.write(path,new byte[]{1,2,3});
        rejectedUnchanged(root,"upload_cache_recovery_required");Files.delete(path);
        Files.write(path,Arrays.copyOf("mdo-browser-use-upload-leaf-v1\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII),200));
        rejectedUnchanged(root,"upload_cache_recovery_required");Files.delete(path);
        try(MdoBrowserUploadFiles files=new MdoBrowserUploadFiles(root)){files.acquireCacheLease();check(files.recoveredFiles()==0);}removeEmpty(root);
    }
    public static void main(String[] args)throws Exception {
        if(args.length==3&&"writer".equals(args[0])){writer(Paths.get(args[1]),"limit".equals(args[2]));return;}
        if(args.length==3&&"holder".equals(args[0])){holder(Paths.get(args[1]));return;}
        base=Paths.get(args[0]);Files.createDirectory(base);
        streaming();liveRecordChange();crossProcessLease();restarts();foreignAndDamage();replacedAndLinked();namesAndRoots();malformedRecords();unprovedHeaders();workerStartup();Files.delete(base);
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"abrupt_jvm_exits\":"+exits+",\"cross_process_lease_verified\":true,\"process_restart_verified\":true,\"worker_startup_recovery_verified\":true,\"framework_verified\":false,\"private_cache_authority_verified\":false,\"uri_assignment_verified\":false}");
    }
}
