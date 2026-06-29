/*
 * Copyright 2026 wchill.
 * https://github.com/wchill/patcheddit
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.boostforreddit;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.morphe.extension.boostforreddit.http.HttpUtils;
import app.morphe.extension.boostforreddit.utils.LoggingUtils;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Adds a long-press save-as action to Boost's existing media download control.
 */
@SuppressWarnings("deprecation")
public final class BoostSaveAsDownload {
    private static final int REQUEST_CODE_SAVE_AS = 0x51A5;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Map<View, Boolean> INSTALLED_VIEWS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static PendingSave pendingSave;

    private BoostSaveAsDownload() {
    }

    public static void install(Object owner) {
        try {
            Activity activity = resolveActivity(owner, null);
            if (activity == null || activity.getWindow() == null) {
                return;
            }

            View root = activity.getWindow().getDecorView();
            if (root == null) {
                return;
            }

            root.post(() -> attachToDownloadViews(owner, root));
            root.postDelayed(() -> attachToDownloadViews(owner, root), 500L);
        } catch (Throwable t) {
            LoggingUtils.logException(false, () -> "Failed to install Boost save-as download", t);
        }
    }

    public static void install(Object owner, View downloadView) {
        try {
            attach(owner, downloadView);
        } catch (Throwable t) {
            LoggingUtils.logException(false, () -> "Failed to install Boost save-as download view", t);
        }
    }

    public static void handleActivityResult(Object owner, int requestCode, int resultCode, Intent data) {
        try {
            if (requestCode != REQUEST_CODE_SAVE_AS) {
                return;
            }

            PendingSave save = pendingSave;
            pendingSave = null;

            if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null || save == null) {
                return;
            }

            Activity activity = resolveActivity(owner, null);
            Context context = activity == null ? null : activity.getApplicationContext();
            if (context == null) {
                return;
            }

            Uri target = data.getData();
            DOWNLOAD_EXECUTOR.execute(() -> writeMedia(context, save, target));
        } catch (Throwable t) {
            LoggingUtils.logException(false, () -> "Failed to handle Boost save-as result", t);
        }
    }

    private static void attachToDownloadViews(Object owner, View root) {
        List<View> candidates = new ArrayList<>();
        collectDownloadCandidates(root, candidates);
        for (View candidate : candidates) {
            attach(owner, candidate);
        }
    }

    private static void collectDownloadCandidates(View view, List<View> candidates) {
        if (view == null) {
            return;
        }

        if (looksLikeDownloadView(view)) {
            candidates.add(view);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectDownloadCandidates(group.getChildAt(i), candidates);
            }
        }
    }

    private static boolean looksLikeDownloadView(View view) {
        if (!view.isClickable()) {
            return false;
        }

        String contentDescription = valueToString(view.getContentDescription());
        if (isDownloadIdentifier(contentDescription)) {
            return true;
        }

        if (view.getId() != View.NO_ID) {
            try {
                String resourceName = view.getResources().getResourceEntryName(view.getId());
                return isDownloadIdentifier(resourceName);
            } catch (Exception ignored) {
                return false;
            }
        }

        return false;
    }

    private static boolean isDownloadIdentifier(String value) {
        if (value == null) {
            return false;
        }

        String lower = value.toLowerCase(Locale.ROOT);
        return lower.equals("action_download") || lower.contains("action_download")
                || lower.contains("download");
    }

    private static void attach(Object owner, View downloadView) {
        if (downloadView == null || INSTALLED_VIEWS.containsKey(downloadView)) {
            return;
        }

        INSTALLED_VIEWS.put(downloadView, Boolean.TRUE);
        downloadView.setLongClickable(true);
        downloadView.setOnLongClickListener(view -> {
            try {
                return startSaveAs(owner, view);
            } catch (Throwable t) {
                LoggingUtils.logException(false, () -> "Failed to start Boost save-as download", t);
                showToast(view.getContext(), "Unable to save media");
                return true;
            }
        });
    }

    private static boolean startSaveAs(Object owner, View view) {
        Activity activity = resolveActivity(owner, view == null ? null : view.getContext());
        if (activity == null) {
            showToast(view == null ? null : view.getContext(), "Unable to open save picker");
            return true;
        }

        MediaSource mediaSource = resolveMediaSource(owner, activity.getIntent());
        if (mediaSource == null) {
            showToast(activity, "Unable to find media URL");
            return true;
        }

        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mediaSource.mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, mediaSource.fileName);

        boolean launched = false;
        try {
            pendingSave = new PendingSave(mediaSource.url, mediaSource.mimeType);
            activity.startActivityForResult(intent, REQUEST_CODE_SAVE_AS);
            launched = true;
        } catch (ActivityNotFoundException e) {
            LoggingUtils.logException(false, () -> "No create-document picker available", e);
            showToast(activity, "Unable to open save picker");
        } finally {
            if (!launched) {
                pendingSave = null;
            }
        }

        return true;
    }

    private static MediaSource resolveMediaSource(Object owner, Intent intent) {
        String url = resolveKnownMediaUrl(owner);
        if (!isUsableMediaUrl(url)) {
            url = resolveIntentMediaUrl(intent);
        }

        if (!isUsableMediaUrl(url)) {
            return null;
        }

        String fileName = fileNameForUrl(url);
        return new MediaSource(url, fileName, mimeTypeForFileName(fileName, url));
    }

    private static String resolveKnownMediaUrl(Object owner) {
        if (owner == null) {
            return null;
        }

        String className = owner.getClass().getName();
        if (className.endsWith(".ExoActivity") || className.endsWith("$ExoActivity")
                || className.contains("ExoActivity")) {
            String url = stringField(owner, "f34573h");
            return isUsableMediaUrl(url) ? url : stringField(owner, "f34890b");
        }

        if (className.endsWith(".MediaVideoActivity") || className.endsWith("$MediaVideoActivity")
                || className.contains("MediaVideoActivity")) {
            String url = stringField(owner, "f34768n");
            return isUsableMediaUrl(url) ? url : stringField(owner, "f34736f");
        }

        return null;
    }

    private static String stringField(Object owner, String fieldName) {
        for (Class<?> cls = owner.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            try {
                Field field = cls.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(owner);
                return value instanceof CharSequence ? value.toString() : null;
            } catch (NoSuchFieldException ignored) {
                // Try superclass.
            } catch (Throwable t) {
                LoggingUtils.logException(false, () -> "Failed to read Boost media field " + fieldName, t);
                return null;
            }
        }
        return null;
    }

    private static String resolveIntentMediaUrl(Intent intent) {
        if (intent == null) {
            return null;
        }

        Uri data = intent.getData();
        if (data != null && isUsableMediaUrl(data.toString())) {
            return data.toString();
        }

        if (intent.getExtras() == null) {
            return null;
        }

        List<String> keys = new ArrayList<>(intent.getExtras().keySet());
        Collections.sort(keys);
        for (String key : keys) {
            String lowerKey = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if (!lowerKey.contains("url") && !lowerKey.contains("uri")
                    && !lowerKey.contains("media") && !lowerKey.contains("video")
                    && !lowerKey.contains("image")) {
                continue;
            }

            Object value = intent.getExtras().get(key);
            if (value instanceof CharSequence && isUsableMediaUrl(value.toString())) {
                return value.toString();
            }
            if (value instanceof Uri && isUsableMediaUrl(value.toString())) {
                return value.toString();
            }
        }

        return null;
    }

    private static boolean isUsableMediaUrl(String value) {
        if (value == null) {
            return false;
        }

        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        return hasKnownMediaExtension(lower) || lower.contains("v.redd.it")
                || lower.contains("i.redd.it") || lower.contains("i.imgur.com")
                || lower.contains("redgifs.com") || lower.contains("gfycat.com")
                || lower.contains("giphy.com");
    }

    private static boolean hasKnownMediaExtension(String lower) {
        return lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png")
                || lower.contains(".gif") || lower.contains(".webp") || lower.contains(".mp4")
                || lower.contains(".webm") || lower.contains(".m4v");
    }

    private static String fileNameForUrl(String url) {
        String pathSegment = null;
        try {
            pathSegment = Uri.parse(url).getLastPathSegment();
        } catch (Exception ignored) {
            // Fall through to generated filename.
        }

        String decoded = valueToString(pathSegment);
        if (decoded != null) {
            try {
                decoded = URLDecoder.decode(decoded, "UTF-8");
            } catch (Exception ignored) {
                // Use the raw segment if decoding fails.
            }
        }

        String extension = extensionForUrl(url);
        if (decoded == null || decoded.trim().isEmpty() || !decoded.contains(".")) {
            decoded = "boost-media-" + System.currentTimeMillis() + extension;
        }

        return decoded.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
    }

    private static String extensionForUrl(String url) {
        String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (lower.contains(".jpeg")) return ".jpg";
        if (lower.contains(".jpg")) return ".jpg";
        if (lower.contains(".png")) return ".png";
        if (lower.contains(".gif")) return ".gif";
        if (lower.contains(".webp")) return ".webp";
        if (lower.contains(".webm")) return ".webm";
        if (lower.contains(".m4v")) return ".m4v";
        return ".mp4";
    }

    private static String mimeTypeForFileName(String fileName, String url) {
        String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
        if (extension != null && !extension.isEmpty()) {
            String mimeType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(extension.toLowerCase(Locale.ROOT));
            if (mimeType != null) {
                return mimeType;
            }
        }

        String lower = (fileName + " " + url).toLowerCase(Locale.ROOT);
        if (lower.contains(".jpg") || lower.contains(".jpeg")) return "image/jpeg";
        if (lower.contains(".png")) return "image/png";
        if (lower.contains(".gif")) return "image/gif";
        if (lower.contains(".webp")) return "image/webp";
        if (lower.contains(".webm")) return "video/webm";
        return "video/mp4";
    }

    private static void writeMedia(Context context, PendingSave save, Uri target) {
        boolean success = false;
        try (Response response = HttpUtils.get(save.url)) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Response body was null");
            }

            try (InputStream input = body.byteStream();
                 OutputStream output = context.getContentResolver().openOutputStream(target)) {
                if (output == null) {
                    throw new IOException("ContentResolver returned null output stream");
                }
                copy(input, output);
            }

            success = true;
            showToast(context, "Media saved");
        } catch (Throwable t) {
            LoggingUtils.logException(false, () -> "Failed to save Boost media as " + save.mimeType, t);
            showToast(context, "Unable to save media");
        } finally {
            LoggingUtils.logInfo(success, () -> "Boost save-as download complete success=" + success);
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    private static Activity resolveActivity(Object owner, Context fallbackContext) {
        if (owner instanceof Activity) {
            return (Activity) owner;
        }

        return findActivity(fallbackContext);
    }

    private static Activity findActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private static void showToast(Context context, String message) {
        if (context == null) {
            return;
        }
        MAIN.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    private static String valueToString(Object value) {
        return value == null ? null : value.toString();
    }

    private static final class MediaSource {
        final String url;
        final String fileName;
        final String mimeType;

        MediaSource(String url, String fileName, String mimeType) {
            this.url = url;
            this.fileName = fileName;
            this.mimeType = mimeType;
        }
    }

    private static final class PendingSave {
        final String url;
        final String mimeType;

        PendingSave(String url, String mimeType) {
            this.url = url;
            this.mimeType = mimeType;
        }
    }
}
