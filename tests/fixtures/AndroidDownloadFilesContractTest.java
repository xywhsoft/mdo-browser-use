package org.xserver.android;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Real files in the one project QA output. Context/UID/provider and network
 * completion are not qualified by this directory-relative storage gate. */
public final class AndroidDownloadFilesContractTest {
    private interface Work {void run()throws Exception;}
    private static int checks;private static Path base;private static final List<Path> directories=new ArrayList<>();
    private static final String OWNER=".mdo-browser-download-owner-v1";
    private static void check(boolean value){if(!value)throw new AssertionError("Download files check "+(checks+1));checks++;}
    private static void refused(String code,Work work)throws Exception{
        try{work.run();throw new AssertionError("Missing download failure "+code);}catch(MdoBrowserDownloadFiles.Failure failure){check(failure.code.equals(code)&&failure.getCause()==null);}
    }
    private static void rawRefused(String code,Work work)throws Exception{
        try{work.run();throw new AssertionError("Missing transfer failure "+code);}catch(MdoBrowserUploadFiles.Failure failure){check(failure.code.equals(code));}
    }
    private static Path directory(String name)throws Exception{Path root=base.resolve(name);Files.createDirectory(root);directories.add(root);return root;}
    private static String hash(byte[] bytes)throws Exception{StringBuilder text=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))text.append(String.format("%02x",b&255));return text.toString();}
    private static Path leaf(Path root)throws Exception{
        Path found=null;try(DirectoryStream<Path> entries=Files.newDirectoryStream(root)){
            for(Path entry:entries)if(entry.getFileName().toString().startsWith("mdo-bu-download-")){check(found==null);found=entry;}
        }check(found!=null);return found;
    }
    private static int leaves(Path root)throws Exception{int count=0;try(DirectoryStream<Path> entries=Files.newDirectoryStream(root)){for(Path p:entries)if(p.getFileName().toString().startsWith("mdo-bu-download-"))count++;}return count;}
    private static void streaming()throws Exception{
        Path root=directory("stream");byte[] bytes=new byte[131075];for(int i=0;i<bytes.length;i++)bytes[i]=(byte)(i*37);
        try(MdoBrowserDownloadFiles files=new MdoBrowserDownloadFiles(root)){
            MdoBrowserDownloadFiles.Writer writer=files.create(MdoBrowserDownloadFiles.FILE_BYTES);
            check(files.reservations()==1&&files.reservedBytes()==67108864&&!writer.sealed());
            refused("download_not_sealed",()->writer.read(0,1));refused("download_not_sealed",writer::sha256);
            refused("download_chunk_invalid",()->writer.append(1,new byte[]{1}));refused("download_chunk_invalid",()->writer.append(0,new byte[0]));
            writer.append(0,Arrays.copyOfRange(bytes,0,65536));refused("download_incomplete",()->writer.finish(65535));
            writer.append(65536,Arrays.copyOfRange(bytes,65536,131072));writer.append(131072,Arrays.copyOfRange(bytes,131072,bytes.length));
            writer.finish(bytes.length);writer.finish(bytes.length);writer.verify();
            check(writer.sealed()&&writer.size()==bytes.length&&writer.sha256().equals(hash(bytes))&&files.reservedBytes()==bytes.length);
            for(int at=0;at<bytes.length;at+=65536)check(Arrays.equals(writer.read(at,65536),Arrays.copyOfRange(bytes,at,Math.min(bytes.length,at+65536))));
            check(writer.read(bytes.length,1).length==0);refused("download_already_sealed",()->writer.append(bytes.length,new byte[]{1}));
            refused("download_range_invalid",()->writer.read(-1,1));refused("download_range_invalid",()->writer.read(0,65537));
            writer.close();writer.close();check(files.reservations()==0&&files.reservedBytes()==0);refused("download_file_unavailable",()->writer.read(0,1));
        }check(Files.isRegularFile(root.resolve(OWNER))&&leaves(root)==0);
    }
    private static void budgets()throws Exception{
        Path root=directory("budgets");try(MdoBrowserDownloadFiles files=new MdoBrowserDownloadFiles(root)){
            refused("download_metadata_invalid",()->files.create(0));refused("download_metadata_invalid",()->files.create(67108865));
            MdoBrowserDownloadFiles.Writer first=files.create(67108864),second=files.create(67108864);
            check(files.reservedBytes()==134217728);refused("download_budget_exceeded",()->files.create(1));
            first.finish(0);check(first.sha256().equals(hash(new byte[0]))&&files.reservedBytes()==67108864);
            MdoBrowserDownloadFiles.Writer next=files.create(67108864);first.close();second.close();next.close();
            List<MdoBrowserDownloadFiles.Writer> empty=new ArrayList<>();for(int i=0;i<32;i++){MdoBrowserDownloadFiles.Writer w=files.create(1);w.finish(0);empty.add(w);}
            check(files.reservations()==32&&files.reservedBytes()==0);refused("download_budget_exceeded",()->files.create(1));
            for(MdoBrowserDownloadFiles.Writer writer:empty)writer.close();check(files.reservations()==0&&files.reservedBytes()==0);
        }
    }
    private static void integrityAndReplacement()throws Exception{
        Path root=directory("integrity");try(MdoBrowserDownloadFiles files=new MdoBrowserDownloadFiles(root)){
            MdoBrowserDownloadFiles.Writer writer=files.create(16);writer.append(0,new byte[]{1,2,3});
            try(SeekableByteChannel corrupt=Files.newByteChannel(leaf(root),StandardOpenOption.WRITE)){corrupt.position(MdoBrowserUploadFiles.HEADER_BYTES+1);corrupt.write(ByteBuffer.wrap(new byte[]{9}));}
            refused("download_hash_mismatch",()->writer.finish(3));check(!writer.sealed()&&files.reservedBytes()==16);
            refused("download_file_unavailable",writer::sha256);writer.close();check(files.reservations()==0);
            final MdoBrowserDownloadFiles.Writer replaced=files.create(2);replaced.append(0,new byte[]{1});Path original=leaf(root),saved=root.resolve("saved");Files.move(original,saved);Files.write(original,new byte[]{7});
            refused("download_file_replaced",()->replaced.append(1,new byte[]{2}));refused("download_file_replaced",replaced::close);
            check(Files.readAllBytes(original)[0]==7&&files.reservations()==1&&files.reservedBytes()==2);
            Files.delete(original);Files.move(saved,original);replaced.close();check(files.reservations()==0);
        }
    }
    private static void ownership()throws Exception{
        Path root=directory("ownership");MdoBrowserDownloadFiles files=new MdoBrowserDownloadFiles(root);
        try{
            MdoBrowserDownloadFiles.Writer writer=files.create(4);Throwable[] result=new Throwable[1];Thread foreign=new Thread(()->{try{writer.append(0,new byte[]{1});}catch(Throwable failure){result[0]=failure;}});foreign.start();foreign.join();
            check(result[0] instanceof IllegalStateException&&files.reservedBytes()==4&&writer.size()==0);
            refused("download_cache_busy",()->new MdoBrowserDownloadFiles(root));check(files.reservations()==1);writer.append(0,new byte[]{1});writer.finish(1);writer.close();
        }finally{files.close();}
        refused("download_cache_closed",()->files.create(1));check(files.reservations()==0);
        Path upload=directory("upload-purpose");try(MdoBrowserUploadFiles original=new MdoBrowserUploadFiles(upload)){
            original.acquireCacheLease();MdoBrowserUploadFiles.Leaf file=original.create(1,hash(new byte[]{2}));
            refused("download_cache_owner_invalid",()->new MdoBrowserDownloadFiles(upload));file.append(0,new byte[]{2});file.seal();check(file.sealed());
        }
        Files.delete(upload.resolve(MdoBrowserUploadFiles.OWNER_FILE));
        Path outside=directory("outside"),link=base.resolve("root-link");Files.write(outside.resolve("foreign"),new byte[]{7});Files.createSymbolicLink(link,outside);
        try{refused("download_cache_invalid",()->new MdoBrowserDownloadFiles(link));check(Files.readAllBytes(outside.resolve("foreign"))[0]==7);}
        finally{Files.delete(link);Files.delete(outside.resolve("foreign"));}
    }
    private static void orphan(Path root)throws Exception{
        MdoBrowserDownloadFiles files=new MdoBrowserDownloadFiles(root);
        for(int i=0;i<4;i++){MdoBrowserDownloadFiles.Writer w=files.create(67108864);w.append(0,new byte[]{(byte)i});w.finish(1);}
        MdoBrowserDownloadFiles.Writer pending=files.create(67108864);pending.append(0,new byte[]{1,2,3});Runtime.getRuntime().halt(0);
    }
    private static void seed(Path root)throws Exception{
        String java=Paths.get(System.getProperty("java.home"),"bin/java").toString();
        Process child=new ProcessBuilder(java,"-cp",System.getProperty("java.class.path"),AndroidDownloadFilesContractTest.class.getName(),"orphan",root.toString()).inheritIO().start();
        check(child.waitFor(15,TimeUnit.SECONDS)&&child.exitValue()==0);check(leaves(root)==5);
    }
    private static void recovery()throws Exception{
        Path root=directory("recovery");seed(root);
        Path unknown=root.resolve("preserve.txt");Files.write(unknown,new byte[]{9});
        refused("download_cache_unrecognized",()->new MdoBrowserDownloadFiles(root));check(leaves(root)==5&&Files.readAllBytes(unknown)[0]==9);Files.delete(unknown);
        try(MdoBrowserDownloadFiles files=new MdoBrowserDownloadFiles(root)){
            check(files.recoveredFiles()==5&&files.recoveredBytes()==7&&files.reservations()==0&&files.reservedBytes()==0&&leaves(root)==0);
        }
    }
    private static Object invoke(java.lang.reflect.Method method,Object real,Object[] args)throws Throwable{
        try{return method.invoke(real,args);}catch(java.lang.reflect.InvocationTargetException failure){throw failure.getCause();}
    }
    /** Real CREATE_NEW then path replacement before its metadata lookup. The
     * channel still owns the original inode, not the foreign path entry. */
    private static void creationReplacement()throws Exception{
        for(boolean downloads:new boolean[]{false,true}){
            Path root=directory(downloads?"creation-download":"creation-upload");MdoBrowserUploadFiles files=new MdoBrowserUploadFiles(root,downloads);files.acquireCacheLease();
            java.lang.reflect.Field field=MdoBrowserUploadFiles.class.getDeclaredField("directory");field.setAccessible(true);
            @SuppressWarnings("unchecked") SecureDirectoryStream<Path> real=(SecureDirectoryStream<Path>)field.get(files);Path[] replacement={null};Path saved=root.resolve("saved");
            Object proxy=java.lang.reflect.Proxy.newProxyInstance(AndroidDownloadFilesContractTest.class.getClassLoader(),new Class<?>[]{SecureDirectoryStream.class},(p,m,a)->{
                Object value=invoke(m,real,a);
                if(m.getName().equals("newByteChannel")&&((java.util.Set<?>)a[1]).contains(StandardOpenOption.CREATE_NEW)){
                    replacement[0]=root.resolve((Path)a[0]);Files.move(replacement[0],saved);Files.write(replacement[0],new byte[0]);
                }return value;
            });field.set(files,proxy);
            try{
                if(downloads)rawRefused("upload_file_changed",()->files.createStream(1));else rawRefused("upload_file_changed",()->files.create(0,hash(new byte[0])));
                check(replacement[0]!=null&&Files.size(replacement[0])==0&&files.reservations()==1);
                MdoBrowserUploadFiles.Leaf pending=files.takeFailedCreation();check(pending!=null);rawRefused("upload_file_changed",pending::close);
                check(Files.exists(replacement[0]));Files.delete(replacement[0]);pending.close();check(files.reservations()==0);
            }finally{files.close();Files.delete(saved);}
            if(!downloads)Files.delete(root.resolve(MdoBrowserUploadFiles.OWNER_FILE));
        }
    }
    public static void main(String[] args)throws Exception{
        if(args[0].equals("orphan")){orphan(Paths.get(args[1]));return;}
        base=Paths.get(args[0]);Files.createDirectory(base);Throwable primary=null;
        try{streaming();budgets();integrityAndReplacement();ownership();recovery();creationReplacement();}
        catch(Exception|Error failure){primary=failure;throw failure;}
        finally{try{for(int i=directories.size()-1;i>=0;i--){Path dir=directories.get(i);Files.deleteIfExists(dir.resolve(OWNER));Files.delete(dir);}Files.delete(base);}
            catch(Exception cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}}
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"real_directory_relative_io\":true,\"independent_source_hash\":true,\"creation_replacement_preserved\":true,\"abrupt_exit_recovery\":true,\"framework_verified\":false,\"private_cache_authority_verified\":false}");
    }
}
