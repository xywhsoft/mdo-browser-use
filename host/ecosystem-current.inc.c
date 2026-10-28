static bool EcoLocalCurrent(cstr path,xvalue* change,const xvalue* owned,char error[1024])
{
    if(!EcoLocalAllowed(path))return false;
    xvalue* Plugin=ExtensionPluginOwner(path);
    if(Plugin){snprintf(error,1024,"Resource is owned by plugin %s; change that plugin first",MdoPackageText(Plugin,"id",64));xrtValueRelease(Plugin);return false;}
    if(!strcmp(path,"config/plugins.json"))return xrtValueObjectHas(change,XRT_STR_LITERAL("old"));
    bool exists=false;xfileinfo info;
    if(!MdoHomeExternalStat(path,&exists,&info))return false;
    if(!exists){
        if(owned){snprintf(error,1024,"Installed file is missing: %s",path);return false;}
        xfile builtin=MdoResourceOpenRead(path);
        if(builtin){xrtClose(builtin);snprintf(error,1024,"Built-in resource cannot be replaced: %s",path);return false;}
        xrtClearError();return xrtValueObjectSetNew(change,XRT_STR_LITERAL("old"),xrtValueNull());
    }
    if(!owned&&!strncmp(path,"data/ecosystem/mcp/",19)){bool missing=false;char* raw=MdoExtensionRead(path,true,131072,&missing);char hash[65];bool same=raw&&MdoExtensionHash(raw,hash)&&!strcmp(hash,MdoPackageText(change,"sha256",64));
        char* encoded=same?xrtBase64EncodeNew(raw,strlen(raw),NULL):NULL;bool reused=encoded&&MdoApiValueSetString(change,"old",encoded);xrtFree(encoded);xrtFree(raw);if(reused)return true;}
    /* Immutable plugin generations outlive their installation receipt.
     * Reimport may reuse only byte-identical content at these validated paths.
     * Keep the original bytes in the rollback journal even for a reused file.
     * Ordinary unowned resources must still conflict. */
    const xvalue* Expected=owned;
    if(!Expected&&!strncmp(path,"plugins/",8))Expected=change;
    cstr Digest=MdoPackageText(Expected,"sha256",64);
    if(!Expected||!Digest||info.Type!=XFILE_TYPE_FILE||!(info.Available&XFILE_INFO_SIZE)||info.Size>64u*1024u*1024u){
        snprintf(error,1024,"Resource already exists: %s",path);return false;
    }
    xfile File=MdoHomeOpenRead(path);size_t Size=(size_t)info.Size;
    bytes Data=xrtMalloc(Size+1);char Hash[65];
    bool Ok=File&&Data&&xrtReadFull(File,Data,Size,NULL)&&MdoExtensionHashBytes(Data,Size,Hash)&&!strcmp(Hash,Digest);
    if(File)xrtClose(File);
    if(!Ok)snprintf(error,1024,"Resource was edited locally: %s. Keep it or export/remove it before updating.",path);
    char* Encoded=Ok?xrtBase64EncodeNew(Data,Size,NULL):NULL;
    Ok=Encoded&&MdoApiValueSetString(change,"old",Encoded);
    xrtFree(Encoded);xrtFree(Data);return Ok;
}
