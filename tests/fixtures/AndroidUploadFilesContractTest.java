package org.xserver.android;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Real directory-relative filesystem checks in the project's one QA directory.
 * This is neither an Android framework nor app-private-directory qualification. */
public final class AndroidUploadFilesContractTest {
    interface Checked {void run()throws Exception;}
    static int checks;static Path base;static boolean openDirectoryRenameVerified;static final List<Path> directories=new ArrayList<>();
    static void check(boolean value){checks++;if(!value)throw new AssertionError("Upload files check "+checks+" failed");}
    static void refused(String code,Checked action)throws Exception{
        try{action.run();throw new AssertionError("Expected "+code);}catch(MdoBrowserUploadFiles.Failure failure){check(code.equals(failure.code));}
    }
    static String sha(byte[] bytes)throws Exception{
        StringBuilder text=new StringBuilder();for(byte value:MessageDigest.getInstance("SHA-256").digest(bytes))text.append(String.format("%02x",value&255));return text.toString();
    }
    static Path directory(String name)throws Exception{Path path=base.resolve(name);Files.createDirectory(path);directories.add(path);return path;}
    static Path onlyFile(Path root)throws Exception{
        try(java.nio.file.DirectoryStream<Path> files=Files.newDirectoryStream(root)){
            Path result=null;for(Path path:files){check(result==null);result=path;}check(result!=null);return result;
        }
    }
    static void streaming()throws Exception{
        Path root=directory("stream");byte[] data=new byte[131075];for(int at=0;at<data.length;at++)data[at]=(byte)(at*37);
        try(MdoBrowserUploadFiles store=new MdoBrowserUploadFiles(root)){
            MdoBrowserUploadFiles.Leaf file=store.create(data.length,sha(data));
            check(store.reservations()==1&&store.reservedBytes()==data.length&&file.size()==data.length&&!file.sealed());
            refused("upload_not_sealed",()->file.read(0,1));
            refused("upload_chunk_invalid",()->file.append(0,null));
            refused("upload_chunk_invalid",()->file.append(0,new byte[0]));
            refused("upload_chunk_invalid",()->file.append(0,new byte[65537]));
            refused("upload_chunk_invalid",()->file.append(1,new byte[1]));
            file.append(0,Arrays.copyOfRange(data,0,65536));
            refused("upload_chunk_invalid",()->file.append(0,new byte[1]));
            refused("upload_incomplete",file::seal);
            file.append(65536,Arrays.copyOfRange(data,65536,131072));file.append(131072,Arrays.copyOfRange(data,131072,data.length));
            refused("upload_chunk_invalid",()->file.append(data.length,new byte[1]));
            file.seal();file.seal();file.verify();check(file.sealed());
            for(int at=0;at<data.length;at+=65536)check(Arrays.equals(file.read(at,65536),Arrays.copyOfRange(data,at,Math.min(data.length,at+65536))));
            refused("upload_already_sealed",()->file.append(data.length,new byte[1]));
            refused("upload_range_invalid",()->file.read(-1,1));refused("upload_range_invalid",()->file.read(0,65537));
            check(file.read(data.length,1).length==0);file.close();file.close();
            check(store.reservations()==0&&store.reservedBytes()==0);refused("upload_file_unavailable",()->file.read(0,1));
            MdoBrowserUploadFiles.Leaf empty=store.create(0,sha(new byte[0]));empty.seal();check(empty.sealed()&&empty.read(0,1).length==0);
        }
        try(java.nio.file.DirectoryStream<Path> files=Files.newDirectoryStream(root)){check(!files.iterator().hasNext());}
    }
    static void budgets()throws Exception{
        Path root=directory("budgets");String empty=sha(new byte[0]);
        try(MdoBrowserUploadFiles store=new MdoBrowserUploadFiles(root)){
            refused("upload_metadata_invalid",()->store.create(-1,empty));refused("upload_metadata_invalid",()->store.create(33554433L,empty));
            refused("upload_metadata_invalid",()->store.create(0,"not-a-hash"));refused("upload_metadata_invalid",()->store.create(0,empty.toUpperCase()));
            for(int at=0;at<4;at++)store.create(33554432L,empty);
            check(store.reservedBytes()==134217728L&&store.reservations()==4);refused("upload_budget_exceeded",()->store.create(1,empty));
            for(int at=4;at<16;at++)store.create(0,empty);
            refused("upload_budget_exceeded",()->store.create(0,empty));check(store.reservations()==16);
        }
    }
    static void failures()throws Exception{
        Path root=directory("failures");byte[] data={1,2,3};
        try(MdoBrowserUploadFiles store=new MdoBrowserUploadFiles(root)){
            MdoBrowserUploadFiles.Leaf bad=store.create(data.length,sha(new byte[]{3,2,1}));bad.append(0,data);
            refused("upload_hash_mismatch",bad::seal);check(!bad.sealed());refused("upload_file_unavailable",()->bad.read(0,3));
            check(store.reservations()==1);bad.close();check(store.reservations()==0);
            MdoBrowserUploadFiles.Leaf file=store.create(0,sha(new byte[0]));Path entry=onlyFile(root),saved=root.resolve("saved");
            Files.move(entry,saved);Files.write(entry,new byte[]{42});
            try{
                refused("upload_file_replaced",file::seal);refused("upload_file_replaced",file::close);
                check(store.reservations()==1&&store.reservedBytes()==0&&Files.readAllBytes(entry)[0]==42);
            }finally{Files.delete(entry);Files.move(saved,entry);}
            file.close();check(store.reservations()==0);
        }
    }
    static void revalidation()throws Exception{
        Path root=directory("revalidation");byte[] original={1,2,3};
        try(MdoBrowserUploadFiles store=new MdoBrowserUploadFiles(root)){
            MdoBrowserUploadFiles.Leaf file=store.create(original.length,sha(original));file.append(0,original);file.seal();
            Files.write(onlyFile(root),new byte[]{3,2,1});
            refused("upload_hash_mismatch",file::verify);check(!file.sealed());
            refused("upload_file_unavailable",()->file.read(0,3));file.close();check(store.reservations()==0);
        }
    }
    static void pinnedDirectory()throws Exception{
        Path root=directory("pinned"),moved=base.resolve("pinned-moved"),outside=directory("outside");
        Path foreign=outside.resolve("foreign");Files.write(foreign,new byte[]{9});
        MdoBrowserUploadFiles store=new MdoBrowserUploadFiles(root);boolean movedRoot=false;Throwable primary=null;
        try{
            Files.move(root,moved,java.nio.file.StandardCopyOption.ATOMIC_MOVE);movedRoot=true;Files.createSymbolicLink(root,outside);
            try{new MdoBrowserUploadFiles(root);throw new AssertionError("Symlink cache accepted");}
            catch(MdoBrowserUploadFiles.Failure failure){check(failure.code.equals("upload_cache_invalid"));}
            refused("upload_cache_changed",()->store.create(0,sha(new byte[0])));
            try(java.util.stream.Stream<Path> entries=Files.list(outside)){check(entries.count()==1&&Files.readAllBytes(foreign)[0]==9);}
            check(store.reservations()==0&&store.reservedBytes()==0);
            Files.delete(root);Files.move(moved,root,java.nio.file.StandardCopyOption.ATOMIC_MOVE);movedRoot=false;
            MdoBrowserUploadFiles.Leaf file=store.create(1,sha(new byte[]{7}));
            file.append(0,new byte[]{7});file.seal();check(file.read(0,1)[0]==7);store.close();
            try(java.nio.file.DirectoryStream<Path> entries=Files.newDirectoryStream(root)){check(!entries.iterator().hasNext());}
        }catch(Exception|Error failure){primary=failure;throw failure;}
        finally{
            try{store.close();if(Files.isSymbolicLink(root))Files.delete(root);if(movedRoot)Files.move(moved,root,java.nio.file.StandardCopyOption.ATOMIC_MOVE);Files.delete(foreign);}
            catch(Exception cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}
        }
    }
    static void pinnedOpenFile()throws Exception{
        Path root=directory("open-directory"),moved=base.resolve("open-directory-moved");boolean changed=false;
        try(MdoBrowserUploadFiles store=new MdoBrowserUploadFiles(root)){
            MdoBrowserUploadFiles.Leaf file=store.create(0,sha(new byte[0]));
            try{
                try{Files.move(root,moved);changed=true;}
                catch(java.nio.file.AccessDeniedException unsupported){
                    // The WSL/NTFS fixture can forbid renaming a directory with
                    // an open file. Keep this qualification explicitly false.
                    check(Files.isDirectory(root)&&!Files.exists(moved));return;
                }
                Files.createSymbolicLink(root,moved);
                refused("upload_cache_changed",file::seal);openDirectoryRenameVerified=true;
            }finally{if(changed){if(Files.isSymbolicLink(root))Files.delete(root);Files.move(moved,root);}}
            file.seal();file.verify();check(file.sealed());
        }
    }
    static void leafLink()throws Exception{
        Path root=directory("leaf-link"),outside=directory("leaf-outside"),foreign=outside.resolve("foreign");Files.write(foreign,new byte[]{5});
        try(MdoBrowserUploadFiles store=new MdoBrowserUploadFiles(root)){
            MdoBrowserUploadFiles.Leaf file=store.create(1,sha(new byte[]{1}));Path entry=onlyFile(root),saved=root.resolve("saved");
            Files.move(entry,saved);Files.createSymbolicLink(entry,foreign);
            try{
                refused("upload_file_replaced",()->file.append(0,new byte[]{1}));refused("upload_file_replaced",file::close);
                check(Files.readAllBytes(foreign)[0]==5&&Files.isSymbolicLink(entry)&&store.reservations()==1);
            }finally{Files.delete(entry);Files.move(saved,entry);}
            file.close();check(store.reservations()==0);
        }finally{Files.delete(foreign);}
    }
    static void ownership()throws Exception{
        Path root=directory("ownership");MdoBrowserUploadFiles store=new MdoBrowserUploadFiles(root);
        MdoBrowserUploadFiles.Leaf file=store.create(0,sha(new byte[0]));AtomicReference<Throwable> error=new AtomicReference<>();
        Thread wrong=new Thread(()->{try{file.close();}catch(Throwable failure){error.set(failure);}});wrong.start();wrong.join();
        check(error.get() instanceof IllegalStateException&&store.reservations()==1);store.close();store.close();
        refused("upload_cache_closed",()->store.create(0,sha(new byte[0])));refused("upload_cache_closed",()->file.read(0,1));
        check(store.reservations()==0&&store.reservedBytes()==0);
    }
    public static void main(String[] args)throws Exception{
        base=Paths.get(args[0]);Files.createDirectory(base);
        Throwable primary=null;
        try{streaming();budgets();failures();revalidation();pinnedDirectory();pinnedOpenFile();leafLink();ownership();}
        catch(Exception|Error failure){primary=failure;throw failure;}
        finally{
            try{for(int index=directories.size()-1;index>=0;index--)Files.delete(directories.get(index));Files.delete(base);}
            catch(Exception cleanup){if(primary!=null)primary.addSuppressed(cleanup);else throw cleanup;}
        }
        System.out.println("{\"passed\":true,\"checks\":"+checks+",\"framework_verified\":false,\"private_cache_authority_verified\":false,"+
            "\"open_directory_rename_verified\":"+openDirectoryRenameVerified+"}");
    }
}
