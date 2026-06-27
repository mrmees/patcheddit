package app.morphe.extension.boostforreddit;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Method;

@SuppressWarnings("deprecation")
public final class BoostAudioFocus {
    private static final String TAG = "BoostAudioFocus";

    private static final AudioManager.OnAudioFocusChangeListener FOCUS_CHANGE_LISTENER =
            new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    synchronized (BoostAudioFocus.class) {
                        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                            hasFocus = true;
                        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS
                                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                                || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                            hasFocus = false;
                        }
                    }

                    Log.d(TAG, "audio focus changed: " + focusChange);
                }
            };

    private static AudioManager audioManager;
    private static AudioFocusRequest focusRequest;
    private static boolean hasFocus;

    private BoostAudioFocus() {
    }

    public static synchronized void requestVideoFocus(Object source) {
        try {
            Context context = resolveContext(source);
            if (context == null) {
                Log.d(TAG, "request skipped: no context for " + className(source));
                return;
            }

            AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if (manager == null) {
                Log.d(TAG, "request skipped: no audio manager");
                return;
            }

            if (hasFocus && manager == audioManager) {
                Log.d(TAG, "request skipped: focus already held");
                return;
            }

            if (hasFocus) {
                abandonVideoFocus();
            }

            int result;
            AudioFocusRequest request = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build();

                request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attributes)
                        .setOnAudioFocusChangeListener(FOCUS_CHANGE_LISTENER)
                        .build();
                result = manager.requestAudioFocus(request);
            } else {
                result = manager.requestAudioFocus(
                        FOCUS_CHANGE_LISTENER,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN
                );
            }

            audioManager = manager;
            focusRequest = request;
            hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            Log.d(TAG, "request result=" + result + " granted=" + hasFocus);
        } catch (Throwable throwable) {
            Log.e(TAG, "request failed", throwable);
        }
    }

    public static synchronized void abandonVideoFocus() {
        try {
            if (audioManager == null) {
                hasFocus = false;
                focusRequest = null;
                return;
            }

            int result;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && focusRequest != null) {
                result = audioManager.abandonAudioFocusRequest(focusRequest);
            } else {
                result = audioManager.abandonAudioFocus(FOCUS_CHANGE_LISTENER);
            }

            Log.d(TAG, "abandon result=" + result);
        } catch (Throwable throwable) {
            Log.e(TAG, "abandon failed", throwable);
        } finally {
            hasFocus = false;
            focusRequest = null;
            audioManager = null;
        }
    }

    private static Context resolveContext(Object source) {
        if (source == null) {
            return null;
        }

        if (source instanceof Context) {
            return ((Context) source).getApplicationContext();
        }

        if (source instanceof View) {
            Context context = ((View) source).getContext();
            return context == null ? null : context.getApplicationContext();
        }

        Context context = invokeContextMethod(source, "getContext");
        if (context != null) {
            return context.getApplicationContext();
        }

        context = invokeContextMethod(source, "getActivity");
        return context == null ? null : context.getApplicationContext();
    }

    private static Context invokeContextMethod(Object source, String methodName) {
        try {
            Method method = source.getClass().getMethod(methodName);
            Object value = method.invoke(source);
            return value instanceof Context ? (Context) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String className(Object source) {
        return source == null ? "null" : source.getClass().getName();
    }
}
