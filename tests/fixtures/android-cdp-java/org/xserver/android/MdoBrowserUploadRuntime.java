package org.xserver.android;
/** Full host fixture supplies its actual bounded queue, never a global cache. */
final class MdoBrowserUploadRuntime {
    static MdoBrowserUploadQueue get(Object activity){throw new AssertionError("Unexpected global file runtime in controlled native host");}
}
