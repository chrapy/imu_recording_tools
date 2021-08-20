package com.pascaldornfeld.gsdble.audio;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;

import java.util.Locale;

public class AudioPlayer {

    private SoundPool sp;
    private TextToSpeech tts;
    private Context context;

    private static com.pascaldornfeld.gsdble.audio.AudioPlayer instance;

    public static com.pascaldornfeld.gsdble.audio.AudioPlayer getInstance() {
        if (instance == null) instance = new com.pascaldornfeld.gsdble.audio.AudioPlayer();

        return instance;
    }

    private AudioPlayer() {}

    public AudioPlayer(Context context) {
        this.context = context;
        sp = new SoundPool.Builder().build();
        sp.setOnLoadCompleteListener((soundPool, sampleId, status) -> {
            sp.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f);
        });

        tts = new TextToSpeech(context, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int ttsLang = tts.setLanguage(Locale.UK);

                    if (ttsLang == TextToSpeech.LANG_MISSING_DATA
                            || ttsLang == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "The Language is not supported!");
                    } else {
                        Log.i("TTS", "Language Supported.");
                    }
                    Log.i("TTS", "Initialization success.");
                } else {
                    Toast.makeText(context.getApplicationContext(), "TTS Initialization failed!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    public void loadSound(int... resourceId) {

    }

    public void play(int resourceId) {
        //sp.load(context, resourceId, 1);
        MediaPlayer mp = MediaPlayer.create(context, resourceId);
        mp.setOnCompletionListener(mp1 -> {
            mp1.release();
            mp1 = null;
        });
        mp.start();
    }

    public void speak(String text) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null,null);
    }

    public void release() {
        sp.release();
        tts.stop();
        tts.shutdown();
    }
}
