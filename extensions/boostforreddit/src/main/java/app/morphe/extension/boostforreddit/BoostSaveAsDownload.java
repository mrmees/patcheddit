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
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
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
    private static final long[] INSTALL_RETRY_DELAYS_MS = {
            0L,
            100L,
            300L,
            700L,
            1500L,
            3000L
    };
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService DOWNLOAD_EXECUTOR = Executors.newSingleThreadExecutor();

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

            installRepeatedly(owner, root);
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

    private static void installRepeatedly(Object owner, View root) {
        for (long delay : INSTALL_RETRY_DELAYS_MS) {
            root.postDelayed(() -> attachToDownloadViews(owner, root), delay);
        }
    }

    private static void attachToDownloadViews(Object owner, View root) {
        List<View> candidates = new ArrayList<>();
        View actionDownloadView = findActionDownloadView(root);
        if (actionDownloadView != null) {
            candidates.add(actionDownloadView);
        }
        collectDownloadCandidates(root, candidates);
        for (View candidate : candidates) {
            attach(owner, candidate);
        }
    }

    private static View findActionDownloadView(View root) {
        if (root == null || root.getContext() == null) {
            return null;
        }

        int id = root.getResources().getIdentifier(
                "action_download",
                "id",
                root.getContext().getPackageName()
        );
        return id == 0 ? null : root.findViewById(id);
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
        if (downloadView == null) {
            return;
        }

        downloadView.setLongClickable(true);
        if (Build.VERSION.SDK_INT >= 26) {
            downloadView.setTooltipText(null);
        }
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
            String url = resolveBoostVideoDownloadUrl(owner, "f34891c", "f34585t", "f34572g", "f34589x");
            if (isUsableMediaUrl(url)) {
                return url;
            }

            url = stringField(owner, "f34573h");
            return isUsableMediaUrl(url) ? url : stringField(owner, "f34890b");
        }

        if (className.endsWith(".MediaVideoActivity") || className.endsWith("$MediaVideoActivity")
                || className.contains("MediaVideoActivity")) {
            return resolveMediaVideoDownloadUrl(owner);
        }

        if (className.endsWith(".MediaImageActivity") || className.endsWith("$MediaImageActivity")
                || className.contains("MediaImageActivity")) {
            return resolveMediaImageDownloadUrl(owner);
        }

        if (className.endsWith(".ImageActivity2") || className.endsWith("$ImageActivity2")
                || className.contains("ImageActivity2")) {
            return resolveLegacyImageDownloadUrl(owner, "F1");
        }

        if (className.endsWith(".ImageActivity") || className.endsWith("$ImageActivity")
                || className.contains("ImageActivity")) {
            return resolveLegacyImageDownloadUrl(owner, "D1");
        }

        if (className.endsWith(".GifActivity") || className.endsWith("$GifActivity")
                || className.contains("GifActivity")) {
            return resolveGifDownloadUrl(owner);
        }

        if (isInstanceOf(owner, "com.rubenmayayo.reddit.ui.activities.GalleryActivity")) {
            return resolveGalleryDownloadUrl(owner);
        }

        return null;
    }

    private static String resolveMediaVideoDownloadUrl(Object owner) {
        int mediaMode = intField(owner, "f34767m", -1);
        boolean useHlsFallback = booleanField(owner, "C");
        if (mediaMode == 2 || (useHlsFallback && mediaMode == 3)) {
            String url = firstUsableMediaUrl(
                    stringMethod(owner, "v2"),
                    resolveBoostVideoDownloadUrl(owner, "f34737g", "f34779y", "f34767m", "C")
            );
            if (url != null) {
                return url;
            }
        }

        String url = firstUsableMediaUrl(
                stringField(owner, "f34768n"),
                stringMethod(owner, "t2"),
                stringField(owner, "f34736f")
        );
        if (url != null) {
            return url;
        }

        return firstUsableMediaUrl(
                stringMethod(owner, "v2"),
                resolveBoostVideoDownloadUrl(owner, "f34737g", "f34779y", "f34767m", "C")
        );
    }

    private static String resolveMediaImageDownloadUrl(Object owner) {
        String url = stringMethod(owner, "R1");
        return isUsableMediaUrl(url) ? url : stringField(owner, "f34736f");
    }

    private static String resolveLegacyImageDownloadUrl(Object owner, String resolverMethod) {
        String url = stringMethod(owner, resolverMethod);
        return isUsableMediaUrl(url) ? url : stringField(owner, "f34890b");
    }

    private static String resolveGifDownloadUrl(Object owner) {
        String url = stringField(owner, "f34646g");
        return isUsableMediaUrl(url) ? url : stringField(owner, "f34890b");
    }

    private static String resolveGalleryDownloadUrl(Object owner) {
        Object images = objectField(owner, "f34615h");
        if (!(images instanceof List)) {
            return stringField(owner, "f34890b");
        }

        List<?> imageList = (List<?>) images;
        if (imageList.isEmpty()) {
            return stringField(owner, "f34890b");
        }

        int index = 0;
        Object viewPager = objectField(owner, "viewPager");
        Object currentItem = invokeNoArgMethod(viewPager, "getCurrentItem");
        if (currentItem instanceof Number) {
            int candidate = ((Number) currentItem).intValue();
            if (candidate >= 0 && candidate < imageList.size()) {
                index = candidate;
            }
        }

        Object imageModel = imageList.get(index);
        String url = stringMethod(imageModel, "getDownloadUrl");
        return isUsableMediaUrl(url) ? url : stringField(owner, "f34890b");
    }

    private static String resolveBoostVideoDownloadUrl(
            Object owner,
            String submissionField,
            String tracksField,
            String mediaModeField,
            String hlsFallbackField
    ) {
        int mediaMode = intField(owner, mediaModeField, -1);
        if (mediaMode != 2 && !(booleanField(owner, hlsFallbackField) && mediaMode == 3)) {
            return null;
        }

        Object submission = objectField(owner, submissionField);
        if (submission == null) {
            return null;
        }

        Object tracks = objectField(owner, tracksField);
        try {
            Class<?> submissionClass = Class.forName("com.rubenmayayo.reddit.models.reddit.SubmissionModel");
            Method method = Class.forName("he.h0").getDeclaredMethod("F", submissionClass, List.class);
            method.setAccessible(true);
            Object url = method.invoke(null, submission, tracks instanceof List ? tracks : null);
            return applyRedditFallbackMp4Extension(valueToString(url));
        } catch (Throwable t) {
            LoggingUtils.logException(false, () -> "Failed to resolve Boost video download URL", t);
            return null;
        }
    }

    private static Object objectField(Object owner, String fieldName) {
        for (Class<?> cls = owner.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            try {
                Field field = cls.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {
                // Try superclass.
            } catch (Throwable t) {
                LoggingUtils.logException(false, () -> "Failed to read Boost media field " + fieldName, t);
                return null;
            }
        }
        return null;
    }

    private static String stringMethod(Object owner, String methodName) {
        Object value = invokeNoArgMethod(owner, methodName);
        return value instanceof CharSequence ? value.toString() : null;
    }

    private static Object invokeNoArgMethod(Object owner, String methodName) {
        if (owner == null) {
            return null;
        }

        for (Class<?> cls = owner.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            try {
                Method method = cls.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return method.invoke(owner);
            } catch (NoSuchMethodException ignored) {
                // Try superclass.
            } catch (Throwable t) {
                LoggingUtils.logException(false, () -> "Failed to call Boost media method " + methodName, t);
                return null;
            }
        }

        return null;
    }

    private static boolean isInstanceOf(Object owner, String className) {
        if (owner == null) {
            return false;
        }

        for (Class<?> cls = owner.getClass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
            if (className.equals(cls.getName())) {
                return true;
            }
        }

        return false;
    }

    private static String stringField(Object owner, String fieldName) {
        Object value = objectField(owner, fieldName);
        return value instanceof CharSequence ? value.toString() : null;
    }

    private static int intField(Object owner, String fieldName, int fallback) {
        Object value = objectField(owner, fieldName);
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static boolean booleanField(Object owner, String fieldName) {
        Object value = objectField(owner, fieldName);
        return value instanceof Boolean && (Boolean) value;
    }

    private static String firstUsableMediaUrl(String... candidates) {
        if (candidates == null) {
            return null;
        }

        for (String candidate : candidates) {
            String url = applyRedditFallbackMp4Extension(candidate);
            if (isUsableMediaUrl(url)) {
                return url;
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

        if (isRedditVideoUrl(lower)) {
            return isDownloadableRedditVideoUrl(lower);
        }

        return hasKnownMediaExtension(lower) || lower.contains("i.redd.it") || lower.contains("i.imgur.com")
                || lower.contains("redgifs.com") || lower.contains("gfycat.com")
                || lower.contains("giphy.com");
    }

    private static boolean isRedditVideoUrl(String lower) {
        return lower.contains("v.redd.it");
    }

    private static boolean isDownloadableRedditVideoUrl(String lower) {
        return lower.contains(".mp4") || lower.contains("/dash_") || lower.contains("/audio");
    }

    private static boolean hasKnownMediaExtension(String lower) {
        return lower.contains(".jpg") || lower.contains(".jpeg") || lower.contains(".png")
                || lower.contains(".gif") || lower.contains(".webp") || lower.contains(".mp4")
                || lower.contains(".webm") || lower.contains(".m4v");
    }

    private static String applyRedditFallbackMp4Extension(String url) {
        if (url == null) {
            return null;
        }

        String lower = url.toLowerCase(Locale.ROOT);
        if (isRedditVideoUrl(lower) && lower.endsWith("?source=fallback")
                && !lower.contains(".mp4?source=fallback")) {
            return url.substring(0, url.length() - "?source=fallback".length()) + ".mp4?source=fallback";
        }

        return url;
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
        File tempFile = null;
        try {
            tempFile = File.createTempFile("boost-save-as-", extensionForUrl(save.url), context.getCacheDir());
            if (downloadWithBoostDownloader(context, save.url, tempFile)) {
                copyFileToUri(context, tempFile, target);
            } else {
                writeMediaDirectly(context, save, target);
            }

            success = true;
            showToast(context, "Media saved");
        } catch (Throwable t) {
            LoggingUtils.logException(false, () -> "Failed to save Boost media as " + save.mimeType, t);
            showToast(context, "Unable to save media");
        } finally {
            if (tempFile != null && tempFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
            final boolean completed = success;
            LoggingUtils.logInfo(completed, () -> "Boost save-as download complete success=" + completed);
        }
    }

    private static boolean downloadWithBoostDownloader(Context context, String url, File target) {
        final boolean[] failed = {false};
        final boolean[] completed = {false};
        try {
            Class<?> listenerClass = Class.forName("tb.d");
            Object listener = Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("a".equals(name)) {
                            failed[0] = true;
                        } else if ("c".equals(name)) {
                            completed[0] = true;
                        }
                        return null;
                    }
            );

            Class<?> downloaderClass = Class.forName("tb.b");
            Object downloader = downloaderClass.getDeclaredConstructor().newInstance();
            Method download = downloaderClass.getDeclaredMethod(
                    "b",
                    Context.class,
                    String.class,
                    File.class,
                    listenerClass
            );
            download.invoke(downloader, context, url, target, listener);
            return !failed[0] && (completed[0] || target.exists()) && target.length() > 0L;
        } catch (Throwable t) {
            LoggingUtils.logException(false, () -> "Failed to save with Boost downloader", t);
            return false;
        }
    }

    private static void copyFileToUri(Context context, File source, Uri target) throws IOException {
        try (InputStream input = new FileInputStream(source);
             OutputStream output = context.getContentResolver().openOutputStream(target)) {
            if (output == null) {
                throw new IOException("ContentResolver returned null output stream");
            }
            copy(input, output);
        }
    }

    private static void writeMediaDirectly(Context context, PendingSave save, Uri target) throws IOException {
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
