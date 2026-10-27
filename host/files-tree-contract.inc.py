    tree_metadata={'type':'object','properties':{'path':s,'revision':s,'type':u,'size':u,'device':u,'identity':u,
        'modified':{'type':'integer'},'changed':{'type':'integer'},'sha256':{'type':'string','pattern':'^[0-9a-f]{64}$'}},
        'required':['path','revision','type','size','device','identity','modified'],'additionalProperties':False}
    tree_revision={'type':'string','pattern':'^t1-[0-9a-f]{64}$','minLength':67,'maxLength':67}
    add('files.tree','Read a complete bounded directory manifest with file hashes, anchored identities and no omitted links or partial results',
        {**file_path,'path':{'type':'string','minLength':1,'maxLength':4096},'revision':tree_revision,
         'max_files':{'type':'integer','minimum':1,'maximum':128},'max_directories':{'type':'integer','minimum':0,'maximum':128},
         'max_depth':{'type':'integer','minimum':0,'maximum':16},'max_bytes':{'type':'integer','minimum':1,'maximum':67108864},
         'max_file_bytes':{'type':'integer','minimum':1,'maximum':33554432}},('path',),
        output={'type':'object','properties':{'format':{'type':'string','enum':['mdo.file-tree.v1']},'revision':tree_revision,
            'root':tree_metadata,'files':{'type':'array','maxItems':128,'items':{**tree_metadata,'required':[*tree_metadata['required'],'sha256']}},
            'directories':{'type':'array','maxItems':128,'items':tree_metadata},
            'file_count':u,'directory_count':u,'total_bytes':u},
            'required':['format','revision','root','files','directories','file_count','directory_count','total_bytes'],'additionalProperties':False})
