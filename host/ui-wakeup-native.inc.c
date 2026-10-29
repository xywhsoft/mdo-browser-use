/* UI queue publication calls this only after releasing its own mutex. */
void MdoApiLiveUiNotify(void)
{
    if ( g_MdoLive.Lock == NULL ) return;
    xrtMutexLock(g_MdoLive.Lock);
    if ( !g_MdoLive.Stopping ) {
        ++g_MdoLive.UiRevision;
        (void)xrtCondBroadcast(g_MdoLive.Changed);
    }
    xrtMutexUnlock(g_MdoLive.Lock);
}

