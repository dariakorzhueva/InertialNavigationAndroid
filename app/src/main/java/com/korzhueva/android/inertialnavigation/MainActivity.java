package com.korzhueva.android.inertialnavigation;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.korzhueva.android.inertialnavigation.filters.AlphaBetaFilter;
import com.korzhueva.android.inertialnavigation.filters.FilterInterface;
import com.korzhueva.android.inertialnavigation.filters.LowPassFilter;
import com.korzhueva.android.inertialnavigation.filters.MedianFilter;
import com.korzhueva.android.inertialnavigation.filters.MovingAverageFilter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    Button startButton;

    TextView tvTime;
    TextView tvAxis;
    TextView tvRotations;
    TextView tvMagnetic;
    TextView tvFilter;

    ConstraintLayout mConstraintLayout;

    SensorManager sensorManager;
    Sensor sensorAccel;
    Sensor sensorGyro;
    Sensor sensorMag;
    Sensor sensorLinearAccel;

    MovingAverageFilter mMovingAverageFilterX = new MovingAverageFilter(3);
    MovingAverageFilter mMovingAverageFilterY = new MovingAverageFilter(3);
    MovingAverageFilter mMovingAverageFilterZ = new MovingAverageFilter(3);

    LowPassFilter mLowPassFilterX = new LowPassFilter(0.25);
    LowPassFilter mLowPassFilterY = new LowPassFilter(0.25);
    LowPassFilter mLowPassFilterZ = new LowPassFilter(0.25);

    MedianFilter mMedianFilterX = new MedianFilter(3);
    MedianFilter mMedianFilterY = new MedianFilter(3);
    MedianFilter mMedianFilterZ = new MedianFilter(3);

    AlphaBetaFilter mAlphaBetaFilterX = new AlphaBetaFilter(0.5, 0, 0, 0.85, 0.005);
    AlphaBetaFilter mAlphaBetaFilterY = new AlphaBetaFilter(0.5, 0, 0, 0.85, 0.005);
    AlphaBetaFilter mAlphaBetaFilterZ = new AlphaBetaFilter(0.5, 0, 0, 0.85, 0.005);

    Date currentDate = new Date();
    DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd HH-mm-ss");
    private double mInitTime;
    private double sensTime;
    Timer timer;

    private static String FILE_NAME = "sensorsValues";
    private static String FILE_NAME_MAF = "MAF";
    private static String FILE_NAME_LPF = "LPF";
    private static String FILE_NAME_MF = "MF";
    private static String FILE_NAME_ABF = "ABF";

    private static String FILE_PATH = "";
    private static String FILE_PATH_MAF = "";
    private static String FILE_PATH_LPF = "";
    private static String FILE_PATH_MF = "";
    private static String FILE_PATH_ABF = "";

    private static final int REQUEST_PERMISSION_WRITE = 1001;

    StringBuilder sb = new StringBuilder();
    private boolean permissionGranted;

    private int flagFilter = -1;
    private boolean flagStatus = false;
    private boolean isStart = false;
    private boolean flagCalibration = true;

    private int sdk = android.os.Build.VERSION.SDK_INT;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        mConstraintLayout = (ConstraintLayout) findViewById(R.id.constraint_layout);
        startButton = (Button) findViewById(R.id.btn_start);

        tvTime = (TextView) findViewById(R.id.tv_time);
        tvTime.setText(String.format("Время: %1$.3f\n", sensTime));
        tvAxis = (TextView) findViewById(R.id.tv_axis);
        tvRotations = (TextView) findViewById(R.id.tv_rotations);
        tvFilter = (TextView) findViewById(R.id.tv_filter);
        //tvMagnetic = (TextView) findViewById(R.id.tv_magnetic);

        startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isStart) {
                    FILE_PATH = "";
                    FILE_PATH_MAF = "";
                    FILE_PATH_LPF = "";
                    FILE_PATH_MF = "";
                    FILE_PATH_ABF = "";

                    currentDate = new Date();
                    FILE_PATH = getExternalPath(FILE_NAME);

                    FILE_PATH_MAF = getExternalPath(FILE_NAME_MAF);
                    FILE_PATH_LPF = getExternalPath(FILE_NAME_LPF);
                    FILE_PATH_MF = getExternalPath(FILE_NAME_MF);
                    FILE_PATH_ABF = getExternalPath(FILE_NAME_ABF);

                    isStart = true;

                    startButton.setText("Стоп");
                    startButton.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.bg_button_red_state));
                    tvTime.setTextColor(getResources().getColor(R.color.textColorPrimaryDark));
                    mInitTime = System.currentTimeMillis();

                    writeLine(FILE_PATH, "Calibration");
                    createTableHead(FILE_PATH);

                    flagStatus = true;

                    // Задержка в 0,5 секунд для сбора показаний для калибровки
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            flagCalibration = false;
                            writeLine(FILE_PATH, "Start of motion recording");
                            createTableHead(FILE_PATH);
                            Uri notify = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                            Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), notify);
                            r.play();
                            tvTime.setTextColor(getResources().getColor(R.color.colorStart));
                            // Получение текущего времени
                            mInitTime = System.currentTimeMillis();
                        }
                    }, 5000);

                    String filename = FILE_PATH;
                    filename = filename.replaceFirst(".*/(\\w+)", "$1");

                    Snackbar.make(v, "Началась запись в файл " + filename, Snackbar.LENGTH_LONG).show();
                } else {
                    isStart = false;

                    startButton.setText("Старт");

                    startButton.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.bg_button_green_state));

                    tvTime.setTextColor(getResources().getColor(R.color.colorStop));

                    flagStatus = false;

                    writeLine(FILE_PATH, "Stop of motion recording");

                    mMovingAverageFilterX.reset();
                    mMovingAverageFilterY.reset();
                    mMovingAverageFilterZ.reset();

                    mLowPassFilterX.reset();
                    mLowPassFilterY.reset();
                    mLowPassFilterZ.reset();

                    mMedianFilterX.reset();
                    mMedianFilterY.reset();
                    mMedianFilterZ.reset();

                    mAlphaBetaFilterX.reset();
                    mAlphaBetaFilterY.reset();
                    mAlphaBetaFilterZ.reset();

                    String filename = FILE_PATH;
                    filename = filename.replaceFirst(".*/(\\w+)", "$1");

                    Snackbar.make(v, "Приостановлена запись в файл " + filename, Snackbar.LENGTH_LONG)
                            .setActionTextColor(Color.GREEN)
                            .setAction("Открыть файл",
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            openCSVFile(FILE_PATH);
                                        }
                                    }).show();
                }
            }
        });

        if (!permissionGranted)
            checkPermissions();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorAccel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorGyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        //sensorMag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorLinearAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!flagCalibration) {
            switch (item.getItemId()) {
                case R.id.maf:
                    flagFilter = 1;
                    writeLine(FILE_PATH_MAF, "Moving Average Filter");
                    createTableHead(FILE_PATH_MAF);
                    tvFilter.setText("Работает фильтр скользящего среднего");
                    Snackbar.make(mConstraintLayout, "Включен фильтр скользящего среднего", Snackbar.LENGTH_LONG).show();
                    return true;
                case R.id.lpf:
                    flagFilter = 2;
                    writeLine(FILE_PATH_LPF, "Low-Pass Filter");
                    createTableHead(FILE_PATH_LPF);
                    tvFilter.setText("Работает фильтр низких частот");
                    Snackbar.make(mConstraintLayout, "Включен фильтр низких частот", Snackbar.LENGTH_LONG).show();
                    return true;
                case R.id.mf:
                    flagFilter = 3;
                    writeLine(FILE_PATH_MF, "Median Filter");
                    createTableHead(FILE_PATH_MF);
                    tvFilter.setText("Работает медианный фильтр");
                    Snackbar.make(mConstraintLayout, "Включен медианный фильтр", Snackbar.LENGTH_LONG).show();
                    return true;
                case R.id.abf:
                    flagFilter = 4;
                    writeLine(FILE_PATH_ABF, "Alpha-Beta Filter");
                    createTableHead(FILE_PATH_ABF);
                    tvFilter.setText("Работает альфа-бета фильтр");
                    Snackbar.make(mConstraintLayout, "Включен альфа-бета фильтр", Snackbar.LENGTH_LONG).show();
                    return true;
                case R.id.all:
                    flagFilter = 0;

                    writeLine(FILE_PATH_MAF, "Moving Average Filter");
                    createTableHead(FILE_PATH_MAF);

                    writeLine(FILE_PATH_LPF, "Low-Pass Filter");
                    createTableHead(FILE_PATH_LPF);

                    writeLine(FILE_PATH_MF, "Median Filter");
                    createTableHead(FILE_PATH_MF);

                    writeLine(FILE_PATH_ABF, "Alpha-Beta Filter");
                    createTableHead(FILE_PATH_ABF);

                    tvFilter.setText("Работают все фильтры");
                    Snackbar.make(mConstraintLayout, "Включены всех фильтры", Snackbar.LENGTH_LONG).show();
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        } else {
            tvFilter.setText("Дождитесь завершения калибровки!");
            return super.onOptionsItemSelected(item);
        }
    }


    private void openCSVFile(String path) {
        Uri selectedUri = Uri.parse(path);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(selectedUri, "text/csv");

        startActivity(intent);
    }

    private double getDeltaT() {
        return System.currentTimeMillis() - mInitTime;
    }

    public void writeLine(String path, String line) {
        try {
            File file = new File(path);
            FileWriter fr = null;
            BufferedWriter br = null;
            try {
                fr = new FileWriter(file, true);
                br = new BufferedWriter(fr);
                br.append(line);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    br.close();
                    fr.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void createTableHead(String path) {
        File file = new File(path);
        FileWriter fr = null;
        BufferedWriter br = null;
        try {
            fr = new FileWriter(file, true);
            br = new BufferedWriter(fr);
            br.newLine();
            br.append("Time (s)");
            br.append(",");
            br.append("aAxisX (m/s2)");
            br.append(",");
            br.append("aAxisY (m/s2)");
            br.append(",");
            br.append("aAxisZ (m/s2)");
            br.append(",");
            br.append("LinAAxisX (m/s2)");
            br.append(",");
            br.append("LinAAxisY (m/s2)");
            br.append(",");
            br.append("LinAAxisZ (m/s2)");
            br.append(",");
            br.append("gRotX (rad/s)");
            br.append(",");
            br.append("gRotY (rad/s)");
            br.append(",");
            br.append("gRotZ (rad/s)");
//            br.append(",");
//            br.append("magX (mT)");
//            br.append(",");
//            br.append("magY (mT)");
//            br.append(",");
//            br.append("magZ (mT)");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
                fr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(listener, sensorAccel,
                SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(listener, sensorGyro, SensorManager.SENSOR_DELAY_FASTEST);
        //sensorManager.registerListener(listener, sensorMag, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(listener, sensorLinearAccel, SensorManager.SENSOR_DELAY_FASTEST);

        timer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (flagStatus) {
                            sensTime = getDeltaT() / 1000;
                            writeValues(FILE_PATH);

                            switch (flagFilter) {
                                case 0:
                                    writeFilteredValues(mMovingAverageFilterX, mMovingAverageFilterY, mMovingAverageFilterZ, FILE_PATH_MAF);

                                    writeFilteredValues(mLowPassFilterX, mLowPassFilterY, mLowPassFilterZ, FILE_PATH_LPF);

                                    writeFilteredValues(mMedianFilterX, mMedianFilterY, mMedianFilterZ, FILE_PATH_MF);

                                    writeFilteredValues(mAlphaBetaFilterX, mAlphaBetaFilterY, mAlphaBetaFilterZ, FILE_PATH_ABF);
                                    break;
                                case 1:
                                    writeFilteredValues(mMovingAverageFilterX, mMovingAverageFilterY, mMovingAverageFilterZ, FILE_PATH_MAF);
                                    break;
                                case 2:
                                    writeFilteredValues(mLowPassFilterX, mLowPassFilterY, mLowPassFilterZ, FILE_PATH_LPF);
                                    break;
                                case 3:
                                    writeFilteredValues(mMedianFilterX, mMedianFilterY, mMedianFilterZ, FILE_PATH_MF);
                                    break;
                                case 4:
                                    writeFilteredValues(mAlphaBetaFilterX, mAlphaBetaFilterY, mAlphaBetaFilterZ, FILE_PATH_ABF);
                                    break;
                                case -1:
                                    break;
                            }

                            showInfo();
                        }
                    }
                });
            }
        };
        timer.schedule(task, 0, 200);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(listener);
        timer.cancel();
    }

    public void writeValues(String path) {
        File file = new File(path);
        FileWriter fr = null;
        BufferedWriter br = null;
        try {
            fr = new FileWriter(file, true);
            br = new BufferedWriter(fr);
            br.newLine();
            br.append(String.valueOf(sensTime));
            br.append(',');
            br.append(String.valueOf(valuesAccel[0]));
            br.append(',');
            br.append(String.valueOf(valuesAccel[1]));
            br.append(',');
            br.append(String.valueOf(valuesAccel[2]));
            br.append(',');
            br.append(String.valueOf(valuesLinear[0]));
            br.append(',');
            br.append(String.valueOf(valuesLinear[1]));
            br.append(',');
            br.append(String.valueOf(valuesLinear[2]));
            br.append(',');
            br.append(String.valueOf(valuesGyro[0]));
            br.append(',');
            br.append(String.valueOf(valuesGyro[1]));
            br.append(',');
            br.append(String.valueOf(valuesGyro[2]));
//            br.append(',');
//            br.append(String.valueOf(valuesMag[0]));
//            br.append(',');
//            br.append(String.valueOf(valuesMag[1]));
//            br.append(',');
//            br.append(String.valueOf(valuesMag[2]));
            br.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
                fr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public void writeFilteredValues(FilterInterface filterX, FilterInterface filterY, FilterInterface filterZ, String path) {
        File file = new File(path);
        FileWriter fr = null;
        BufferedWriter br = null;

        try {
            fr = new FileWriter(file, true);
            br = new BufferedWriter(fr);
            br.newLine();
            br.append(String.valueOf(sensTime));
            br.append(',');
            br.append(String.valueOf(filterX.update(valuesAccel[0])));
            br.append(',');
            br.append(String.valueOf(filterY.update(valuesAccel[1])));
            br.append(',');
            br.append(String.valueOf(filterZ.update(valuesAccel[2])));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
                fr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String format(double values[]) {
        return String.format("X: %2$.8f\tY: %3$.8f\tZ: %4$.8f", sensTime, values[0], values[1],
                values[2]);
    }

    private void showInfo() {
        tvTime.setText(String.format("Время: %1$.3f\n", sensTime));

        tvAxis.setText("Акселерометр\n" + format(valuesAccel));

        tvRotations.setText("Гироскоп\n" + format(valuesGyro));

        //tvMagnetic.setText("Магнитометр\n" + format(valuesMag));
    }

    double[] valuesAccel = new double[3];
    double[] valuesGyro = new double[3];
    //double[] valuesMag = new double[3];
    double[] valuesLinear = new double[3];

    SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        @Override

        public void onSensorChanged(SensorEvent event) {
            switch (event.sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    for (int i = 0; i < 3; i++) {
                        valuesAccel[i] = event.values[i];
                    }
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    for (int i = 0; i < 3; i++) {
                        valuesGyro[i] = event.values[i];
                    }
                    break;
//                case Sensor.TYPE_MAGNETIC_FIELD:
//                    for (int i = 0; i < 3; i++) {
//                        valuesMag[i] = event.values[i];
//                    }
//                    break;
                case Sensor.TYPE_LINEAR_ACCELERATION:
                    for (int i = 0; i < 3; i++) {
                        valuesLinear[i] = event.values[i];
                    }
                    break;
            }

        }

    };

    // Маска итогового файла: sensorsValues XXXX.XX.XX XX-XX-XX.csv
    private String getExternalPath(String name) {
        File storage = Environment.getExternalStorageDirectory();
        name = name + " " + dateFormat.format(currentDate) + ".csv";
        String path = storage + "/" + name;

        return path;
    }

    public boolean isExternalStorageWriteable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state));
    }

    private boolean checkPermissions() {
        if (!isExternalStorageReadable() || !isExternalStorageWriteable()) {
            Toast.makeText(this, "Внешнее хранилище недоступно", Toast.LENGTH_LONG).show();
            return false;
        }
        int permissionCheck = 1;
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION_WRITE);
            return false;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_WRITE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    permissionGranted = true;
                break;
        }
    }
}