def manifest(package: str) -> str:
    import re
    if not re.fullmatch(r'[a-z][a-z0-9_]*(?:\.[a-z][a-z0-9_]*)+', package):
        raise ValueError('Invalid private upload provider package')
    return (f'<provider android:name="org.xserver.android.MdoBrowserUploadProvider" '
            f'android:authorities="{package}.mdo.browseruse.upload" '
            'android:exported="false" android:grantUriPermissions="false" />')

