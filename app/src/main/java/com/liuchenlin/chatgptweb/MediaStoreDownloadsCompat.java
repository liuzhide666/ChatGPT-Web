package com.liuchenlin.chatgptweb;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.Objects;

/**
 * Capability-probed Android 10+ Downloads writer.
 *
 * <p>The API-29-only downloads collection class is intentionally resolved by name instead of
 * appearing as a symbolic DEX dependency. This keeps the app loadable on API 26-28 and on
 * compatibility layers that report a newer SDK but do not expose the complete API-29 framework.
 */
public final class MediaStoreDownloadsCompat {
    private static final int DOWNLOADS_API = 29;
    private static final String DISPLAY_NAME = "_display_name";
    private static final String MIME_TYPE = "mime_type";
    private static final String RELATIVE_PATH = "relative_path";
    private static final String IS_PENDING = "is_pending";

    interface Creator { Uri create(DownloadMetadata metadata) throws IOException; }
    interface Opener { OutputStream open(Uri uri) throws IOException; }
    interface Publisher { void publish(Uri uri) throws IOException; }
    interface Deleter { void delete(Uri uri); }

    private final Creator creator;
    private final Opener opener;
    private final Publisher publisher;
    private final Deleter deleter;

    private MediaStoreDownloadsCompat(
            Creator creator,
            Opener opener,
            Publisher publisher,
            Deleter deleter) {
        this.creator = Objects.requireNonNull(creator, "creator");
        this.opener = Objects.requireNonNull(opener, "opener");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.deleter = Objects.requireNonNull(deleter, "deleter");
    }

    public static MediaStoreDownloadsCompat tryCreate(ContentResolver resolver, int sdkInt) {
        if (resolver == null || sdkInt < DOWNLOADS_API) {
            return null;
        }
        final Uri downloadsUri;
        try {
            Class<?> downloadsClass = Class.forName("android.provider.MediaStore$Downloads");
            Field field = downloadsClass.getField("EXTERNAL_CONTENT_URI");
            Object value = field.get(null);
            if (!(value instanceof Uri)) {
                return null;
            }
            downloadsUri = (Uri) value;
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException
                 | RuntimeException | LinkageError unavailable) {
            return null;
        }

        return new MediaStoreDownloadsCompat(
                metadata -> createItem(resolver, downloadsUri, metadata),
                uri -> openItem(resolver, uri),
                uri -> publishItem(resolver, uri),
                uri -> deleteItem(resolver, uri));
    }

    static MediaStoreDownloadsCompat forTesting(
            Creator creator,
            Opener opener,
            Publisher publisher,
            Deleter deleter) {
        return new MediaStoreDownloadsCompat(creator, opener, publisher, deleter);
    }

    Uri create(DownloadMetadata metadata) throws IOException {
        return creator.create(metadata);
    }

    OutputStream open(Uri uri) throws IOException {
        return opener.open(uri);
    }

    void publish(Uri uri) throws IOException {
        publisher.publish(uri);
    }

    void delete(Uri uri) {
        deleter.delete(uri);
    }

    private static Uri createItem(ContentResolver resolver, Uri downloadsUri, DownloadMetadata metadata)
            throws IOException {
        ContentValues values = new ContentValues();
        values.put(DISPLAY_NAME, metadata.fileName);
        values.put(MIME_TYPE, metadata.mimeType);
        values.put(RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ChatGPT Web");
        values.put(IS_PENDING, 1);
        Uri uri = resolver.insert(downloadsUri, values);
        if (uri == null) {
            throw new IOException("MediaStore insert failed");
        }
        return uri;
    }

    private static OutputStream openItem(ContentResolver resolver, Uri uri) throws IOException {
        OutputStream output = resolver.openOutputStream(uri, "w");
        if (output == null) {
            throw new IOException("ContentResolver returned null OutputStream");
        }
        return output;
    }

    private static void publishItem(ContentResolver resolver, Uri uri) throws IOException {
        ContentValues values = new ContentValues();
        values.put(IS_PENDING, 0);
        if (resolver.update(uri, values, null, null) <= 0) {
            throw new IOException("MediaStore publish failed");
        }
    }

    private static void deleteItem(ContentResolver resolver, Uri uri) {
        try {
            resolver.delete(uri, null, null);
        } catch (RuntimeException ignored) {
            // Best-effort cleanup of an incomplete MediaStore row.
        }
    }
}
