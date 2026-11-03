bool MdoApiApprovalsRoute(MdoApiContext* Context)
{
    char Project[MDO_APPROVAL_PROJECT_CAPACITY], Session[MDO_APPROVAL_SESSION_CAPACITY];
    if ( !BuApprovalQuery(Context->Target.Query, Project, Session) )
        return MdoApiReplyError(Context, 400u, "approval_scope_invalid",
            "Supply both project_id and session_id exactly once", NULL);
    xwork_error Error;
    memset(&Error, 0, sizeof(Error));
    MdoApprovalSnapshot* Snapshot = MdoApprovalSnapshotCreate(&Error);
    size_t Pending = MdoApprovalSnapshotCount(Snapshot), Total = 0u, Count = 0u;
    xvalue* Data = xrtValueObject();
    xvalue* Items = xrtValueArray();
    bool Ok = Snapshot != NULL && Data != NULL && Items != NULL;
    for ( size_t Index = 0u; Ok && Index < Pending; ++Index ) {
        MdoApprovalInfo Info;
        memset(&Info, 0, sizeof(Info)); Info.Size = sizeof(Info);
        Ok = MdoApprovalSnapshotAt(Snapshot, Index, &Info);
        if ( !Ok ) break;
        if ( Project[0] && (strcmp(Project, Info.ProjectId) || strcmp(Session, Info.SessionId)) ) continue;
        ++Total;
        if ( Count >= MDO_APPROVAL_API_LIMIT ) continue;
        xvalue* Item = NULL;
        Ok = MdoApiApprovalValue(&Info, &Item) && MdoApiValueAppendTake(Items, &Item);
        xrtValueRelease(Item);
        if ( Ok ) ++Count;
    }
    if ( Ok ) Ok = MdoApiValueSetUInt(Data, "total", Total) &&
        MdoApiValueSetUInt(Data, "pending_total", Pending) &&
        MdoApiValueSetUInt(Data, "limit", MDO_APPROVAL_API_LIMIT) &&
        MdoApiValueSetString(Data, "project_id", Project) &&
        MdoApiValueSetString(Data, "session_id", Session) &&
        MdoApiValueSetBool(Data, "truncated", Total > Count) &&
        MdoApiValueSetTake(Data, "items", &Items);
    xrtValueRelease(Items);
    MdoApprovalSnapshotRelease(Snapshot);
    if ( !Ok ) {
        xrtValueRelease(Data);
        return MdoApiReplyError(Context, 503u, "approvals_unavailable",
            "Pending approvals could not be read", NULL);
    }
    return MdoApiReplySuccessTake(Context, 200u, Data, NULL);
}
