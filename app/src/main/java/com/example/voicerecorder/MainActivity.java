package com.example.voicerecorder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private ImageView mImageView;
    private Button mButtonTakePhoto;
    private FloatingActionButton mFabRecord, mFabSend;
    private TextView mTextViewTimer;
    private File mImageFile, mAudioFile;
    private byte[] mBytes;
    private CountDownTimer mCountDownTimer;
    private int mSecond = -1, mMinute, mHour;
    private MediaRecorder mMediaRecorder;
    private MutableLiveData<byte[]> mMutableLiveData = new MutableLiveData<>();

    private static final int REQUEST_CODE_TAKE_IMAGE = 0;
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 0;
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String AUTHORITIES = "com.example.voicerecorder.fileprovider";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViews();
        setListener();
        setObserver();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK & requestCode == REQUEST_CODE_TAKE_IMAGE) {
            Uri imageUri = getUriForFile();
            revokeUriPermission(imageUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        FileInputStream fileInputStream = new FileInputStream(mImageFile);
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        mBytes = new byte[1024];
                        int len = 0;
                        while ((len = fileInputStream.read(mBytes)) != -1) {
                            byteArrayOutputStream.write(mBytes, 0, len);
                        }
                        mMutableLiveData.postValue(mBytes);

                    } catch (FileNotFoundException e) {
                        Log.e(TAG, e.getMessage(), e);
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }
            }).start();

            /*Bitmap bitmap = BitmapFactory.decodeFile(mImageFile.getAbsolutePath());
            mImageView.setImageBitmap(bitmap);*/
            //Type of File -> getContentResolver().getType(imageUri)
            /*Cursor cursor = getContentResolver().query(imageUri, null, null, null, null);
            int nameIndexColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            int sizeIndexColumn = cursor.getColumnIndex(OpenableColumns.SIZE);
            cursor.moveToFirst();
            Log.d(TAG, "uriName" + cursor.getString(nameIndexColumn));
            Log.d(TAG, "uriSize" + cursor.getString(sizeIndexColumn));*/
            /*Bitmap bitmap = data.getParcelableExtra("data");
            mImageView.setImageBitmap(bitmap);*/
            /*Log.d(TAG, "mImageFileName" + mImageFile.getName());
            Log.d(TAG, "mImageFileSize" + mImageFile.length());*/
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                takeImage();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void findViews() {
        mImageView = findViewById(R.id.img);
        mButtonTakePhoto = findViewById(R.id.btn_take_photo);
        mFabRecord = findViewById(R.id.fab_record);
        mFabSend = findViewById(R.id.fab_send);
        mTextViewTimer = findViewById(R.id.txt_timer);
    }

    private void setListener() {
        mButtonTakePhoto.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("NewApi")
            @Override
            public void onClick(View view) {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    takeImage();
                } else {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
                }
            }
        });

        mFabRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    startRecording();
                    Log.d(TAG, mAudioFile.getName());
                    Log.d(TAG, mAudioFile.length() + "");
                    Log.d(TAG, mAudioFile.getPath());
                } catch (IOException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
        });

        mFabSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopRecording();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            convertFileToString(mAudioFile);
                        } catch (IOException e) {
                            Log.e(TAG, e.getMessage(), e);
                        }
                    }
                }).start();
            }
        });
    }

    private void setObserver() {
        mMutableLiveData.observe(this, new Observer<byte[]>() {
            @Override
            public void onChanged(byte[] bytes) {
                Log.d(TAG, bytes.length + "");
            }
        });
    }

    private void takeImage() {
        Intent takeImageIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takeImageIntent.resolveActivity(getPackageManager()) != null) {
            try {
                mImageFile = createImageFile();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage(), e);
            }
            if (mImageFile != null) {
                Uri imageUri = getUriForFile();

                List<ResolveInfo> activities = getPackageManager().queryIntentActivities(
                        takeImageIntent, PackageManager.MATCH_DEFAULT_ONLY);

                for (ResolveInfo activity : activities) {
                    grantUriPermission(
                            activity.activityInfo.packageName,
                            imageUri,
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                }
                takeImageIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
                startActivityForResult(takeImageIntent, REQUEST_CODE_TAKE_IMAGE);
            }
        }
    }

    private Uri getUriForFile() {
        return FileProvider.getUriForFile(
                MainActivity.this, AUTHORITIES, mImageFile);
    }

    private File createImageFile() throws IOException {
        String fileName = "img_" + new Date().getTime();
        File fileDirectory = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        Log.d(TAG, fileDirectory + "");
        File imageFile = File.createTempFile(fileName, ".jpg", fileDirectory);
        return imageFile;
    }

    private File createAudioFile() throws IOException {
        String fileName = "audio_" + new Date().getTime();
        File fileDirectory = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        Log.d(TAG, fileDirectory + "");
        File audioFile = File.createTempFile(fileName, ".3gp", fileDirectory);
        return audioFile;
    }

    private void startRecording() throws IOException {
        mAudioFile = createAudioFile();
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        mMediaRecorder.setOutputFile(mAudioFile.getAbsolutePath());
        mMediaRecorder.prepare();
        mMediaRecorder.start();
        showTimer();
    }

    private void stopRecording() {
        mCountDownTimer.cancel();
        mSecond = -1;
        mMinute = 0;
        mHour = 0;
        mTextViewTimer.setText("00:00:00");
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();
            mMediaRecorder.reset();
            mMediaRecorder.release();
        }
    }

    private String convertFileToString(File audioFile) throws IOException {
        Log.d(TAG, audioFile.length() + "");
        InputStream inputStream = new FileInputStream(audioFile.getPath());
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int len = 0;

        while ((len = inputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, len);
        }

        Log.d(TAG, byteArrayOutputStream.toByteArray().length + "");

        StringBuilder result = new StringBuilder();
        for (byte b : byteArrayOutputStream.toByteArray()) {
            result.append(String.format("%02x", b));
        }

        Log.d(TAG, result.length() + "");

        return result.toString();
    }

    private void showTimer() {
        mCountDownTimer = new CountDownTimer(Long.MAX_VALUE, 1000) {
            @Override
            public void onTick(long l) {
                mSecond++;
                mTextViewTimer.setText(recorderTime());
            }

            @Override
            public void onFinish() {
            }
        };
        mCountDownTimer.start();
    }

    private String recorderTime() {
        if (mSecond == 60) {
            mMinute++;
            mSecond = 0;
        }
        if (mMinute == 60) {
            mHour++;
            mMinute = 0;
        }
        return String.format("%02d:%02d:%02d", mHour, mMinute, mSecond);
    }
}