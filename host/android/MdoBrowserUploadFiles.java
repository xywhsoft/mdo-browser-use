package org.xserver.android;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
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
    private long reserved;private boolean closed,directoryClosed;

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
    }
    private static boolean hash(String value){return value!=null&&value.matches("[a-f0-9]{64}");}
    private BasicFileAttributes attributes(Path path)throws IOException {
        return directory.getFileAttributeView(path,BasicFileAttributeView.class,LinkOption.NOFOLLOW_LINKS).readAttributes();
    }
    Leaf create(long size,String sha256)throws IOException {
        active();if(size<0||size>FILE_BYTES||!hash(sha256))throw new Failure("upload_metadata_invalid");
        int slot=0;while(slot<leaves.length&&leaves[slot]!=null)slot++;
        if(slot==leaves.length||size>TOTAL_BYTES-reserved)throw new Failure("upload_budget_exceeded");
        // Allocate identity/metadata before touching disk. Once published, a
        // failed cleanup keeps its slot/byte reservation and can be retried.
        Leaf leaf=new Leaf(slot,size,sha256);leaves[slot]=leaf;reserved+=size;
        try{
            leaf.claimed=true;
            SeekableByteChannel channel;
            try{channel=directory.newByteChannel(leaf.name,CREATE,PRIVATE);}
            catch(FileAlreadyExistsException failure){leaf.claimed=false;leaf.retire();throw new Failure("upload_identity_conflict");}
            leaf.channel=channel;
            if(!(channel instanceof FileChannel))throw new Failure("upload_file_channel_unsupported");
            BasicFileAttributes value=attributes(leaf.name);
            if(!value.isRegularFile()||value.isSymbolicLink()||value.fileKey()==null||value.size()!=0)throw new Failure("upload_file_changed");
            leaf.fileKey=value.fileKey();return leaf;
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
    @Override public void close()throws IOException {
        thread();closed=true;Throwable first=null;
        for(Leaf leaf:leaves)if(leaf!=null)try{leaf.close();}catch(IOException|RuntimeException failure){if(first==null)first=failure;}
        if(first!=null)throw new Failure("upload_cleanup_failed",first);
        if(!directoryClosed){directory.close();directoryClosed=true;}
    }

    final class Leaf implements AutoCloseable {
        private final int slot;private final long expected;private final String sha256;private final Path name;
        private SeekableByteChannel channel;private Object fileKey;private long received;
        private boolean claimed,sealed,failed,retired,closing;
        private Leaf(int slot,long expected,String sha256){
            this.slot=slot;this.expected=expected;this.sha256=sha256;
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
            if(original().size()!=received||channel.size()!=received)throw new Failure("upload_file_changed");
            try{
                channel.position(received);ByteBuffer buffer=ByteBuffer.wrap(bytes);
                while(buffer.hasRemaining())if(channel.write(buffer)<=0)throw new Failure("upload_write_stalled");
                received+=bytes.length;
            }catch(IOException|RuntimeException|Error failure){failed=true;throw failure;}
        }
        private void verifyBytes(SeekableByteChannel input)throws IOException {
            MessageDigest digest;
            try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException failure){throw new Failure("upload_hash_unavailable");}
            input.position(0);ByteBuffer buffer=ByteBuffer.allocate(32768);long total=0;
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
        void seal()throws IOException {
            usable();if(sealed)return;
            if(received!=expected)throw new Failure("upload_incomplete");
            SeekableByteChannel read=null;
            try{
                if(original().size()!=expected||channel.size()!=expected)throw new Failure("upload_file_changed");
                ((FileChannel)channel).force(true);verifyBytes(channel);
                read=directory.newByteChannel(name,READ);original();verifyBytes(read);
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
            try{original();verifyBytes(channel);if(original().size()!=expected)throw new Failure("upload_file_changed");}
            catch(IOException|RuntimeException|Error failure){failed=true;throw failure;}
        }
        byte[] read(long offset,int limit)throws IOException {
            usable();if(!sealed)throw new Failure("upload_not_sealed");
            if(offset<0||offset>expected||limit<1||limit>CHUNK)throw new Failure("upload_range_invalid");
            if(original().size()!=expected||channel.size()!=expected)throw new Failure("upload_file_changed");
            ByteBuffer bytes=ByteBuffer.allocate((int)Math.min(limit,expected-offset));channel.position(offset);
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
