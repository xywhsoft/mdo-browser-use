package org.xserver.android;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Worker-confined staging primitives. The native caller supplies an app-private
 * cache directory, never a wire path. All leaf I/O stays relative to its pinned
 * directory handle. This class does not grant URIs or enable automation. */
final class MdoBrowserUploadFiles implements AutoCloseable {
    static final int CHUNK=65536,FILES=16;
    static final int HEADER_BYTES=1024;
    private static final int HEADER_DATA=HEADER_BYTES-32;
    private static final byte[] HEADER_MAGIC="mdo-browser-use-upload-leaf-v1\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    static final long FILE_BYTES=33554432L,TOTAL_BYTES=134217728L;
    static final class Failure extends IOException {
        final String code;
        Failure(String code){super(code);this.code=code;}
        Failure(String code,Throwable cause){super(code,cause);this.code=code;}
    }
    private static final Set<OpenOption> CREATE=Collections.unmodifiableSet(new HashSet<OpenOption>(Arrays.asList(
        StandardOpenOption.CREATE_NEW,StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)));
    private static final Set<OpenOption> READ=Collections.unmodifiableSet(new HashSet<OpenOption>(Arrays.asList(
        StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS)));
    private static final FileAttribute<Set<PosixFilePermission>> PRIVATE=PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
    private final Thread owner=Thread.currentThread();
    private final Path root;private final Object rootKey;
    private final SecureDirectoryStream<Path> directory;
    private final Leaf[] leaves=new Leaf[FILES];
    private FileChannel cacheLease;private FileLock cacheLock;private Object cacheOwnerKey;
    private Leaf failedCreation;
    static final String OWNER_FILE=".mdo-browser-upload-owner-v1";
    private static final byte[] OWNER_BYTES="mdo-browser-use-private-upload-cache-v1\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private long reserved,recoveredBytes;private int recoveredFiles;private boolean closed,directoryClosed;

    MdoBrowserUploadFiles(Path root)throws IOException {
        if(root==null)throw new Failure("upload_cache_invalid");
        Path owned=root.toAbsolutePath().normalize();
        BasicFileAttributes before=Files.readAttributes(owned,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
        if(!before.isDirectory()||before.isSymbolicLink()||before.fileKey()==null)throw new Failure("upload_cache_invalid");
        DirectoryStream<Path> opened=Files.newDirectoryStream(owned);
        if(!(opened instanceof SecureDirectoryStream<?>)){opened.close();throw new Failure("upload_secure_directory_unsupported");}
        @SuppressWarnings("unchecked") SecureDirectoryStream<Path> pinned=(SecureDirectoryStream<Path>)opened;
        try{
            BasicFileAttributes after=pinned.getFileAttributeView(BasicFileAttributeView.class).readAttributes();
            if(!after.isDirectory()||!before.fileKey().equals(after.fileKey()))throw new Failure("upload_cache_changed");
        }catch(IOException|RuntimeException|Error failure){try{pinned.close();}catch(IOException ignored){}throw failure;}
        directory=pinned;this.root=owned;rootKey=before.fileKey();
    }
    private void thread(){if(Thread.currentThread()!=owner)throw new IllegalStateException("Uploads require their owning worker thread");}
    private void active()throws Failure {
        thread();if(closed)throw new Failure("upload_cache_closed");
        try{
            BasicFileAttributes current=Files.readAttributes(root,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
            if(!current.isDirectory()||current.isSymbolicLink()||!rootKey.equals(current.fileKey()))throw new Failure("upload_cache_changed");
        }catch(Failure failure){throw failure;}catch(IOException failure){throw new Failure("upload_cache_changed",failure);}
        if(cacheLease!=null)try{
            BasicFileAttributes marker=attributes(java.nio.file.Paths.get(OWNER_FILE));
            if(!cacheLease.isOpen()||cacheLock==null||!cacheLock.isValid()||cacheOwnerKey==null||!cacheOwnerKey.equals(marker.fileKey())||
                !marker.isRegularFile()||marker.isSymbolicLink()||marker.size()!=OWNER_BYTES.length)throw new Failure("upload_cache_owner_changed");
        }catch(Failure failure){throw failure;}catch(IOException failure){throw new Failure("upload_cache_owner_changed",failure);}
    }
    private static boolean hash(String value){return value!=null&&value.matches("[a-f0-9]{64}");}
    private BasicFileAttributes attributes(Path path)throws IOException {
        return directory.getFileAttributeView(path,BasicFileAttributeView.class,LinkOption.NOFOLLOW_LINKS).readAttributes();
    }
    Leaf create(long size,String sha256)throws IOException {
        thread();failedCreation=null;
        active();if(size<0||size>FILE_BYTES||!hash(sha256))throw new Failure("upload_metadata_invalid");
        int slot=0;while(slot<leaves.length&&leaves[slot]!=null)slot++;
        if(slot==leaves.length||size>TOTAL_BYTES-reserved)throw new Failure("upload_budget_exceeded");
        // Allocate identity/metadata before touching disk. Once published, a
        // failed cleanup keeps its slot/byte reservation and can be retried.
        Leaf leaf=new Leaf(slot,size,sha256);leaves[slot]=leaf;reserved+=size;failedCreation=leaf;
        try{
            leaf.claimed=true;
            SeekableByteChannel channel;
            try{channel=directory.newByteChannel(leaf.name,CREATE,PRIVATE);}
            catch(FileAlreadyExistsException failure){leaf.claimed=false;leaf.retire();throw new Failure("upload_identity_conflict");}
            leaf.channel=channel;
            if(!(channel instanceof FileChannel))throw new Failure("upload_file_channel_unsupported");
            BasicFileAttributes value=attributes(leaf.name);
            if(!value.isRegularFile()||value.isSymbolicLink()||value.fileKey()==null||value.size()!=0)throw new Failure("upload_file_changed");
            leaf.fileKey=value.fileKey();
            if(leaf.payloadOffset!=0){
                ByteBuffer header=ByteBuffer.wrap(record(leaf.name,leaf.fileKey,leaf.expected,leaf.sha256));
                while(header.hasRemaining())if(channel.write(header)<=0)throw new Failure("upload_write_stalled");
                ((FileChannel)channel).force(true);
            }
            active();failedCreation=null;return leaf;
        }catch(IOException|RuntimeException|Error failure){
            leaf.failed=true;
            try{leaf.close();}catch(IOException|RuntimeException ignored){}
            if(failure instanceof Failure)throw failure;
            if(failure instanceof Error)throw failure;
            throw new Failure("upload_file_create_failed",failure);
        }
    }
    int reservations(){thread();int used=0;for(Leaf leaf:leaves)if(leaf!=null)used++;return used;}
    long reservedBytes(){thread();return reserved;}
    int recoveredFiles(){thread();return recoveredFiles;}
    long recoveredBytes(){thread();return recoveredBytes;}
    Leaf takeFailedCreation(){thread();Leaf value=failedCreation;failedCreation=null;return value;}
    private static boolean stagingName(String name){return name.matches("mdo-bu-upload-[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}\\.bin");}
    // The public fileKey contract does not define a persistent encoding. Only
    // Android/OpenJDK's reviewed Unix provider encoding is accepted here; no
    // reflection or hidden API is used, and other providers fail closed.
    private static String recoveryKey(Object key)throws Failure {
        if(key==null||!"sun.nio.fs.UnixFileKey".equals(key.getClass().getName()))throw new Failure("upload_recovery_identity_unsupported");
        String text=key.toString();if(!text.matches("\\(dev=[a-f0-9]{1,16},ino=-?[0-9]{1,20}\\)"))throw new Failure("upload_recovery_identity_unsupported");return text;
    }
    private static byte[] digest(byte[] bytes,int length)throws Failure {
        try{MessageDigest sha=MessageDigest.getInstance("SHA-256");sha.update(bytes,0,length);return sha.digest();}
        catch(NoSuchAlgorithmException unavailable){throw new Failure("upload_hash_unavailable");}
    }
    private static void putText(ByteBuffer output,String text){byte[] bytes=text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);output.putShort((short)bytes.length).put(bytes);}
    private static String takeText(ByteBuffer input)throws Failure {
        if(input.remaining()<2)throw new Failure("upload_cache_recovery_required");int length=input.getShort()&65535;
        if(length<1||length>200||length>input.remaining())throw new Failure("upload_cache_recovery_required");
        byte[] value=new byte[length];input.get(value);for(byte item:value)if(item<32||item>126)throw new Failure("upload_cache_recovery_required");
        return new String(value,java.nio.charset.StandardCharsets.US_ASCII);
    }
    private byte[] record(Path name,Object key,long expected,String sha)throws Failure {
        byte[] bytes=new byte[HEADER_BYTES];ByteBuffer output=ByteBuffer.wrap(bytes);
        output.put(HEADER_MAGIC).putInt(1).putLong(expected);
        putText(output,recoveryKey(rootKey));putText(output,recoveryKey(key));putText(output,name.toString());putText(output,sha);
        output.position(HEADER_DATA);output.put(digest(bytes,HEADER_DATA));return bytes;
    }
    private static final class Recovery {
        final Path name;final Object key;final long physicalBytes,expected;final byte[] header;
        Recovery(Path name,Object key,long physicalBytes,long expected,byte[] header){this.name=name;this.key=key;this.physicalBytes=physicalBytes;this.expected=expected;this.header=header;}
    }
    private Recovery readRecovery(Path name)throws IOException {
        active();BasicFileAttributes before=attributes(name);
        if(!before.isRegularFile()||before.isSymbolicLink()||before.fileKey()==null||before.size()<HEADER_BYTES||before.size()>HEADER_BYTES+FILE_BYTES)
            throw new Failure("upload_cache_recovery_required");
        byte[] bytes=new byte[HEADER_BYTES];
        try(SeekableByteChannel input=directory.newByteChannel(name,READ)){
            if(!(input instanceof FileChannel)||input.size()!=before.size())throw new Failure("upload_cache_recovery_required");
            ByteBuffer target=ByteBuffer.wrap(bytes);while(target.hasRemaining())if(input.read(target)<=0)throw new Failure("upload_cache_recovery_required");
        }
        if(!MessageDigest.isEqual(digest(bytes,HEADER_DATA),Arrays.copyOfRange(bytes,HEADER_DATA,HEADER_BYTES)))throw new Failure("upload_cache_recovery_required");
        ByteBuffer input=ByteBuffer.wrap(bytes,0,HEADER_DATA);
        for(byte item:HEADER_MAGIC)if(input.get()!=item)throw new Failure("upload_cache_recovery_required");
        if(input.getInt()!=1)throw new Failure("upload_cache_recovery_required");long expected=input.getLong();
        if(expected<0||expected>FILE_BYTES||before.size()>HEADER_BYTES+expected)throw new Failure("upload_cache_recovery_required");
        if(!recoveryKey(rootKey).equals(takeText(input))||!recoveryKey(before.fileKey()).equals(takeText(input))||!name.toString().equals(takeText(input))||!hash(takeText(input)))
            throw new Failure("upload_cache_recovery_required");
        while(input.hasRemaining())if(input.get()!=0)throw new Failure("upload_cache_recovery_required");
        BasicFileAttributes after=attributes(name);
        if(!after.isRegularFile()||after.isSymbolicLink()||!before.fileKey().equals(after.fileKey())||after.size()!=before.size())throw new Failure("upload_cache_recovery_required");
        return new Recovery(name,before.fileKey(),before.size(),expected,bytes);
    }
    private void recover(Object ownerKey)throws IOException {
        recoveryKey(rootKey);java.util.List<Recovery> pending=new java.util.ArrayList<>();long total=0;
        // Validate the entire bounded inventory before unlinking any entry.
        try(SecureDirectoryStream<Path> inventory=directory.newDirectoryStream(java.nio.file.Paths.get("."),LinkOption.NOFOLLOW_LINKS)){
            int count=0;for(Path entry:inventory){
                if(++count>FILES+1)throw new Failure("upload_cache_inventory_limit");String name=entry.getFileName().toString();
                if(OWNER_FILE.equals(name))continue;
                if(!stagingName(name))throw new Failure("upload_cache_unrecognized");
                Recovery value=readRecovery(entry.getFileName());total+=value.expected;
                if(total>TOTAL_BYTES)throw new Failure("upload_cache_inventory_limit");pending.add(value);
            }
        }
        for(Recovery value:pending){
            active();BasicFileAttributes owner=attributes(java.nio.file.Paths.get(OWNER_FILE));
            if(!owner.isRegularFile()||owner.isSymbolicLink()||!ownerKey.equals(owner.fileKey())||owner.size()!=OWNER_BYTES.length)throw new Failure("upload_cache_owner_changed");
            Recovery current=readRecovery(value.name);
            if(!value.key.equals(current.key)||value.physicalBytes!=current.physicalBytes||!Arrays.equals(value.header,current.header))throw new Failure("upload_cache_recovery_required");
            directory.deleteFile(value.name);recoveredFiles++;recoveredBytes+=value.physicalBytes-HEADER_BYTES;
        }
    }
    void acquireCacheLease()throws IOException {
        active();if(cacheLease!=null)throw new Failure("upload_cache_already_owned");
        Path name=java.nio.file.Paths.get(OWNER_FILE);SeekableByteChannel opened=null;FileLock lock=null;boolean created=false;
        try{
            try{attributes(name);}
            catch(NoSuchFileException absent){
                try(SecureDirectoryStream<Path> inventory=directory.newDirectoryStream(java.nio.file.Paths.get("."),LinkOption.NOFOLLOW_LINKS)){
                    if(inventory.iterator().hasNext())throw new Failure("upload_cache_owner_invalid");
                }
            }
            try{opened=directory.newByteChannel(name,CREATE,PRIVATE);created=true;}
            catch(FileAlreadyExistsException existing){
                Set<OpenOption> options=new HashSet<OpenOption>(Arrays.asList(StandardOpenOption.READ,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS));
                opened=directory.newByteChannel(name,options);
            }
            if(!(opened instanceof FileChannel))throw new Failure("upload_file_channel_unsupported");
            BasicFileAttributes value=attributes(name);
            if(!value.isRegularFile()||value.isSymbolicLink()||value.fileKey()==null)throw new Failure("upload_cache_owner_invalid");
            try{lock=((FileChannel)opened).tryLock();}catch(OverlappingFileLockException busy){throw new Failure("upload_cache_busy");}
            if(lock==null)throw new Failure("upload_cache_busy");
            if(created){
                ByteBuffer bytes=ByteBuffer.wrap(OWNER_BYTES);
                while(bytes.hasRemaining())if(opened.write(bytes)<=0)throw new Failure("upload_write_stalled");
                ((FileChannel)opened).force(true);
            }else{
                if(opened.size()!=OWNER_BYTES.length)throw new Failure("upload_cache_owner_invalid");
                ByteBuffer bytes=ByteBuffer.allocate(OWNER_BYTES.length);
                while(bytes.hasRemaining())if(opened.read(bytes)<=0)throw new Failure("upload_cache_owner_invalid");
                if(!Arrays.equals(bytes.array(),OWNER_BYTES))throw new Failure("upload_cache_owner_invalid");
            }
            recover(value.fileKey());
            cacheLease=(FileChannel)opened;cacheLock=lock;cacheOwnerKey=value.fileKey();opened=null;lock=null;
        }finally{
            if(lock!=null)try{lock.release();}finally{if(opened!=null)opened.close();}
            else if(opened!=null)opened.close();
        }
    }
    void collectFailed()throws IOException {
        thread();Throwable first=null;
        for(Leaf leaf:leaves)if(leaf!=null&&leaf.failed)try{leaf.close();}catch(IOException|RuntimeException error){if(first==null)first=error;}
        if(first!=null)throw new Failure("upload_cleanup_failed",first);
    }
    @Override public void close()throws IOException {
        thread();closed=true;Throwable first=null;
        for(Leaf leaf:leaves)if(leaf!=null)try{leaf.close();}catch(IOException|RuntimeException failure){if(first==null)first=failure;}
        if(first!=null)throw new Failure("upload_cleanup_failed",first);
        if(cacheLease!=null){try{if(cacheLock!=null)cacheLock.release();}finally{cacheLease.close();}cacheLock=null;cacheLease=null;cacheOwnerKey=null;}
        if(!directoryClosed){directory.close();directoryClosed=true;}
    }

    final class Leaf implements AutoCloseable {
        private final int slot,payloadOffset;private final long expected;private final String sha256;private final Path name;
        private SeekableByteChannel channel;private Object fileKey;private long received;
        private boolean claimed,sealed,failed,retired,closing;
        private Leaf(int slot,long expected,String sha256){
            this.slot=slot;this.expected=expected;this.sha256=sha256;payloadOffset=cacheLease==null?0:HEADER_BYTES;
            name=java.nio.file.Paths.get("mdo-bu-upload-"+UUID.randomUUID()+".bin");
        }
        private void usable()throws Failure {
            active();if(retired||closing||failed||channel==null)throw new Failure("upload_file_unavailable");
        }
        private BasicFileAttributes original()throws IOException {
            BasicFileAttributes value=attributes(name);
            if(!value.isRegularFile()||value.isSymbolicLink()||fileKey==null||!fileKey.equals(value.fileKey()))throw new Failure("upload_file_replaced");
            return value;
        }
        void append(long offset,byte[] bytes)throws IOException {
            usable();
            if(sealed)throw new Failure("upload_already_sealed");
            if(bytes==null||bytes.length==0||bytes.length>CHUNK||offset!=received||bytes.length>expected-received)
                throw new Failure("upload_chunk_invalid");
            if(original().size()!=payloadOffset+received||channel.size()!=payloadOffset+received)throw new Failure("upload_file_changed");
            try{
                channel.position(payloadOffset+received);ByteBuffer buffer=ByteBuffer.wrap(bytes);
                while(buffer.hasRemaining())if(channel.write(buffer)<=0)throw new Failure("upload_write_stalled");
                received+=bytes.length;
            }catch(IOException|RuntimeException|Error failure){failed=true;throw failure;}
        }
        private void verifyBytes(SeekableByteChannel input)throws IOException {
            MessageDigest digest;
            try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException failure){throw new Failure("upload_hash_unavailable");}
            input.position(payloadOffset);ByteBuffer buffer=ByteBuffer.allocate(32768);long total=0;
            for(;;){
                int count=input.read(buffer);if(count<0)break;if(count==0)throw new Failure("upload_read_stalled");
                total+=count;if(total>expected)throw new Failure("upload_file_changed");
                buffer.flip();digest.update(buffer);buffer.clear();
            }
            if(total!=expected)throw new Failure("upload_file_changed");
            byte[] actual=digest.digest();
            for(int index=0;index<actual.length;index++){
                int high=Character.digit(sha256.charAt(index*2),16),low=Character.digit(sha256.charAt(index*2+1),16);
                if((actual[index]&255)!=(high<<4|low))throw new Failure("upload_hash_mismatch");
            }
        }
        private void verifyRecord(SeekableByteChannel input)throws IOException {
            if(payloadOffset==0)return;ByteBuffer header=ByteBuffer.allocate(HEADER_BYTES);input.position(0);
            while(header.hasRemaining())if(input.read(header)<=0)throw new Failure("upload_file_changed");
            if(!Arrays.equals(header.array(),record(name,fileKey,expected,sha256)))throw new Failure("upload_file_changed");
        }
        void seal()throws IOException {
            usable();if(sealed)return;
            if(received!=expected)throw new Failure("upload_incomplete");
            SeekableByteChannel read=null;
            try{
                if(original().size()!=payloadOffset+expected||channel.size()!=payloadOffset+expected)throw new Failure("upload_file_changed");
                ((FileChannel)channel).force(true);verifyRecord(channel);verifyBytes(channel);
                read=directory.newByteChannel(name,READ);original();verifyRecord(read);verifyBytes(read);
                channel.close();channel=read;read=null;sealed=true;
            }catch(IOException|RuntimeException|Error failure){
                failed=true;if(read!=null)try{read.close();}catch(IOException ignored){}throw failure;
            }
        }
        boolean sealed(){thread();return sealed&&!failed&&!retired;}
        long size(){thread();return expected;}
        // Revalidate before a future native grant. Range reads are bounded and
        // do not repeatedly hash the whole file for every chunk.
        void verify()throws IOException {
            usable();if(!sealed)throw new Failure("upload_not_sealed");
            try{original();verifyRecord(channel);verifyBytes(channel);if(original().size()!=payloadOffset+expected)throw new Failure("upload_file_changed");}
            catch(IOException|RuntimeException|Error failure){failed=true;throw failure;}
        }
        byte[] read(long offset,int limit)throws IOException {
            usable();if(!sealed)throw new Failure("upload_not_sealed");
            if(offset<0||offset>expected||limit<1||limit>CHUNK)throw new Failure("upload_range_invalid");
            if(original().size()!=payloadOffset+expected||channel.size()!=payloadOffset+expected)throw new Failure("upload_file_changed");
            ByteBuffer bytes=ByteBuffer.allocate((int)Math.min(limit,expected-offset));channel.position(payloadOffset+offset);
            while(bytes.hasRemaining())if(channel.read(bytes)<=0){failed=true;throw new Failure("upload_file_changed");}
            return bytes.array();
        }
        private void retire(){
            if(retired)return;retired=true;leaves[slot]=null;reserved-=expected;
        }
        @Override public void close()throws IOException {
            thread();if(retired)return;if(closing)throw new Failure("upload_cleanup_pending");closing=true;
            try{
                if(channel!=null){channel.close();channel=null;}
                if(claimed){
                    try{
                        BasicFileAttributes value=attributes(name);
                        if(!value.isRegularFile()||value.isSymbolicLink()||fileKey!=null&&!fileKey.equals(value.fileKey()))throw new Failure("upload_file_replaced");
                        directory.deleteFile(name);
                    }catch(NoSuchFileException gone){/* Already unlinked from this exact pinned directory. */}
                }
                retire();
            }finally{closing=false;}
        }
    }
}
