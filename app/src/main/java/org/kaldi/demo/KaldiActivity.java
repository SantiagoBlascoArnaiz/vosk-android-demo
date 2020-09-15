// Copyright 2019 Alpha Cephei Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.kaldi.demo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.kaldi.Assets;
import org.kaldi.Model;
import org.kaldi.RecognitionListener;
import org.kaldi.SpeechRecognizer;
import org.kaldi.Vosk;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;


/**
 * The type Kaldi activity.
 */
public class KaldiActivity extends Activity implements
        RecognitionListener {

    static {
        System.loadLibrary("kaldi_jni");
    }

    static private final int STATE_START = 0;
    static private final int STATE_READY = 1;
    static private final int STATE_DONE = 2;
    static private final int STATE_MIC  = 3;
    static private final int STATE_STATICS = 4;
    static private final int STATE_NOSTATICS = 5;
    static private final int STATE_CHANGE_MODEL = 6;


    static private int currentModel = 0;
    static private String currentModelPath = "vosk-spanish-model";
    static private final int TOTAL_MODELS = 3;


    static private boolean recognitionStats = false;


    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;


    private Model model;
    private SpeechRecognizer recognizer;
    /**
     * The Result view.
     */
    TextView resultView;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);

        // Setup layout
        resultView = findViewById(R.id.result_text);
        setUiState(STATE_START);

        findViewById(R.id.stats_options).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recognizeStats();
            }
        });

        findViewById(R.id.recognize_mic).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recognizeMicrophone();
            }
        });


        findViewById(R.id.change_model).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                changeModel();
            }
        });

        // Check if user has given permission to record audio
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
            return;
        }
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new SetupTask(this).execute();
    }

    private static class SetupTask extends AsyncTask<Void, Void, Exception> {

        //The Activity reference.
        WeakReference<KaldiActivity> activityReference;

        //Instantiates a new setup task.
        SetupTask(KaldiActivity activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected Exception doInBackground(Void... params) {
            try {
                Assets assets = new Assets(activityReference.get());
                File assetDir = assets.syncAssets();
                Log.d("KaldiDemo", "Sync files in the folder " + assetDir.toString());

                Log.d("Santiago", "DIRECTORIO DE ASSETS" + assetDir.toString());

                Vosk.SetLogLevel(0);

                activityReference.get().model = new Model(assetDir.toString() + "/" + currentModelPath);
            } catch (IOException e) {
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            if (result != null) {
                activityReference.get().setErrorState(String.format(activityReference.get().getString(R.string.failed), result));
            } else {
                activityReference.get().setUiState(STATE_READY);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                new SetupTask(this).execute();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (recognizer != null) {
            recognizer.cancel();
            recognizer.shutdown();
        }
    }


    @Override
    public void onResult(String hypothesis) {

        if (recognitionStats){
            resultView.append(hypothesis + "\n");
        }else{
            resultView.append(getSpeechResult(hypothesis) + "\n\n");
        }

    }


    @Override
    public void onPartialResult(String hypothesis) {
        //resultView.append(hypothesis + "\n");
    }

    /**
     * Transform a raw transcription to a readable format
     *
     * @param hypothesis the transcription with raw format
     * @return the string in readable format
     */
    public String getSpeechResult(String hypothesis){


        String[] arrayOfStrings = hypothesis.split("text\" :");
        String textPredicted = arrayOfStrings[1];

        arrayOfStrings = textPredicted.split("\"");
        textPredicted = arrayOfStrings[1];

        return textPredicted;
    }

    @Override
    public void onError(Exception e) {
        setErrorState(e.getMessage());
    }

    @Override
    public void onTimeout() {
        recognizer.cancel();
        recognizer = null;
        setUiState(STATE_READY);
    }

    private void setUiState(int state) {
        switch (state) {
            case STATE_START:
                resultView.setText(R.string.preparing);
                resultView.setMovementMethod(new ScrollingMovementMethod());
                findViewById(R.id.stats_options).setEnabled(false);
                ((Button) findViewById(R.id.recognize_mic)).setBackgroundResource(R.drawable.mic_nr);
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.change_model).setEnabled(false);
                break;
            case STATE_READY:
                resultView.setText(getString(R.string.ready)  + " (" + currentModelPath + ")\n" + getString(R.string.say_something));
                ((Button) findViewById(R.id.recognize_mic)).setBackgroundResource(R.drawable.mic_in);
                findViewById(R.id.stats_options).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.change_model).setEnabled(true);
                break;
            case STATE_DONE:
                ((Button) findViewById(R.id.recognize_mic)).setBackgroundResource(R.drawable.mic_in);
                findViewById(R.id.stats_options).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.change_model).setEnabled(true);
                break;
            case STATE_MIC:
                ((Button) findViewById(R.id.recognize_mic)).setBackgroundResource(R.drawable.mic_out);
                resultView.setText(getString(R.string.listening));
                findViewById(R.id.stats_options).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.change_model).setEnabled(false);
                break;
            case STATE_STATICS:
                resultView.setText(getString(R.string.statics));
                break;
            case STATE_NOSTATICS:
                resultView.setText(getString(R.string.nostatics));
                break;
            case STATE_CHANGE_MODEL:
                resultView.setText("Modelo cambiado a " + currentModelPath);

                findViewById(R.id.stats_options).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(false);
                ((Button) findViewById(R.id.recognize_mic)).setBackgroundResource(R.drawable.mic_nr);
                findViewById(R.id.change_model).setEnabled(false);
                break;
        }
    }

    private void setErrorState(String message) {
        resultView.setText(message);
        ((Button) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
        findViewById(R.id.stats_options).setEnabled(false);
        findViewById(R.id.recognize_mic).setEnabled(false);
        ((Button) findViewById(R.id.recognize_mic)).setBackgroundResource(R.drawable.mic_nr);
    }

    /**
     * Switch between data display modes.
     */
    public void recognizeStats() {

        if (recognitionStats){
            setUiState(STATE_NOSTATICS);
            recognitionStats = false;
        }else{
            setUiState(STATE_STATICS);
            recognitionStats = true;
        }
    }

    /**
     * Switch between models.
     */
    public void changeModel(){
        currentModel = (currentModel + 1)%TOTAL_MODELS;

        switch (currentModel){
            case 0:
                currentModelPath = "vosk-spanish-model";
                break;
            case 1:
                currentModelPath = "model-android";
                break;
            case 2:
                currentModelPath = "timit-model";
                break;
        }
        Log.d("Santiago", "MODELO" + currentModel + currentModelPath);
        setUiState(STATE_CHANGE_MODEL);


        new SetupTask(this).execute();
    }

    /**
     * Create the speech recognizer, if it's created delete it.
     */
    public void recognizeMicrophone() {
        if (recognizer != null) {
            setUiState(STATE_DONE);
            recognizer.cancel();
            recognizer = null;
        } else {
            setUiState(STATE_MIC);
            try {
                recognizer = new SpeechRecognizer(model);
                recognizer.addListener(this);
                recognizer.startListening();
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

}
