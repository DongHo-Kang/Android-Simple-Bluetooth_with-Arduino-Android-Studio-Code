package com.mcuhq.simplebluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private final String TAG = MainActivity.class.getSimpleName();

    private static final UUID BT_MODULE_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // "random" unique identifier

    // 함수 호출하는 공유 유형을 식별하기 위한 정의
    private final static int REQUEST_ENABLE_BT = 1; // 추가 블루투스 이름을 식별하는 데 사용.
    public final static int MESSAGE_READ = 2; // 메시지 업데이트를 식별하기 위해 블루투스 핸들러에서 사용
    final static int CONNECTING_STATUS = 3; // 메시지 상태를 식별하기 위해 블루투스 핸들러에서 사용

    // GUI 요소 설정
    private TextView mBluetoothStatus, mReadBuffer, mLedStatus, mReceiveNumber;
    private Button mScanBtn, mOffBtn, mListPairedDevicesBtn, mDiscoverBtn;
    private ListView mDevicesListView;
    private CheckBox mLED1, mBlink;

    private BluetoothAdapter mBTAdapter;
    private Set<BluetoothDevice> mPairedDevices;
    private ArrayAdapter<String> mBTArrayAdapter;

    private Handler mHandler; // 콜백 알림을 받을 메인 핸들러
    private ConnectedThread mConnectedThread; // 데이터를 송수신하는 블루투스 백그라운드 작업자 스레드
    private BluetoothSocket mBTSocket = null; // 양방향 클라이언트 간 데이터 경로
    private int mRandomNumber = 0;
    @SuppressLint("MissingInflatedId")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //activity_main.xml과 연결
        mBluetoothStatus = (TextView)findViewById(R.id.bluetooth_status);
        mReadBuffer = (TextView) findViewById(R.id.read_buffer);
        mScanBtn = (Button)findViewById(R.id.scan);
        mOffBtn = (Button)findViewById(R.id.off);
        mDiscoverBtn = (Button)findViewById(R.id.discover);
        mListPairedDevicesBtn = (Button)findViewById(R.id.paired_btn);
        mLED1 = (CheckBox)findViewById(R.id.checkbox_led);
        mBlink = (CheckBox)findViewById(R.id.checkbox_blink);
        mLedStatus = (TextView) findViewById(R.id.led_status);
        mReceiveNumber = (TextView) findViewById(R.id.receiveNumber);

        mBTArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        mBTAdapter = BluetoothAdapter.getDefaultAdapter(); // 블루투스 기능에 대한 기본 어댑터를 가져오는 매서드

        mDevicesListView = (ListView)findViewById(R.id.devices_list_view);
        mDevicesListView.setAdapter(mBTArrayAdapter); // 모델 할당
        mDevicesListView.setOnItemClickListener(mDeviceClickListener);

        // 허용되지 않은 경우 위치권한 요청
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);


        mHandler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(Message msg){
                if(msg.what == MESSAGE_READ){
                    String readMessage = null;
                    readMessage = new String((byte[]) msg.obj, StandardCharsets.UTF_8);
                    mReadBuffer.setText(readMessage); //난수 받아와서 읽음

                    int receivedNumber;
                    try{
                        receivedNumber = Integer.parseInt(readMessage.trim());
                    }catch (NumberFormatException e){
                        e.printStackTrace();
                        return;
                    }
                    //수신받으면 행동함. 102를 받으면 LED On, 103을 받으면 LED Off.
                    if(receivedNumber == 102){
                        mLedStatus.setText("LED On");
                        mLED1.setChecked(true);
                    }else if(receivedNumber == 103){
                        mLedStatus.setText("LED Off");
                        mLED1.setChecked(false);
                    }else if(0 <= receivedNumber){
                       mReceiveNumber.setText(String.valueOf(receivedNumber));
                    }
                }

                if(msg.what == CONNECTING_STATUS){
                    char[] sConnected;
                    if(msg.arg1 == 1)
                        mBluetoothStatus.setText("연결된 디바이스: "+ (String) msg.obj);
                    else
                        mBluetoothStatus.setText("연결 실패");
                }
            }
        };

        if (mBTArrayAdapter == null) {
            // 블루투스를 지원하지 않음
            mBluetoothStatus.setText("상태: 블루투스 연결 없음");
            Toast.makeText(getApplicationContext(),"블루투스 연결 없음",Toast.LENGTH_SHORT).show();
        }
        else {

            mLED1.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @SuppressLint("SetTextI18n")
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        ledOn(); //ledOn() 메서드 호출
                    }else {
                        ledOff(); //ledOff() 메서드 호출
                    }
                }
            });

            mBlink.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (isChecked) {
                        blinkOn(); //blinkOn() 메서드 호출
                    } else {
                        blinkOff(); //blinkOff() 메서드 호출
                    }
                }
            });

            mScanBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    bluetoothOn();
                } //bluetoothOn 메소드를 불러온다.
            });

            mOffBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    bluetoothOff();
                } //bluetoothOff 메소드를 불러온다.
            });

            mListPairedDevicesBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v){
                    listPairedDevices();
                } //listPairedDevices 메소드를 불러온다.
            });

            mDiscoverBtn.setOnClickListener(new View.OnClickListener(){
                @Override
                public void onClick(View v){
                    discover();
                } //discover 메소드를 불러온다.
            });
        }
    }

    private void bluetoothOn(){
        if (!mBTAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            mBluetoothStatus.setText("블루투스 사용 가능");
            Toast.makeText(getApplicationContext(),"블루투스 켜짐",Toast.LENGTH_SHORT).show();

        }
        else{
            Toast.makeText(getApplicationContext(),"이미 블루투스가 켜져 있습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    // 사용자가 무선 활성화에 대해 "예" 또는 "아니오"를 선택한 후 입력하기.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent Data){
        // 응답 중인 요천 확인
        if (requestCode == REQUEST_ENABLE_BT) {
            //요청이 성공했는지 확인
            if (resultCode == RESULT_OK) {
                //연결될 기기 선택
                //Intent 데이터 Uri는 선택 연결된 기기를 식별
                mBluetoothStatus.setText("설정 완료");
            }
            else
                mBluetoothStatus.setText("설정 취소");
        }
    }

    private void bluetoothOff(){
        mBTAdapter.disable(); //끄기
        mBluetoothStatus.setText("블루투스 비활성화");
        Toast.makeText(getApplicationContext(),"블루투스 꺼짐", Toast.LENGTH_SHORT).show();
    }

    private void discover(){
        //장치가 이미 검색 중인지 확인
        if(mBTAdapter.isDiscovering()){
            mBTAdapter.cancelDiscovery();
            Toast.makeText(getApplicationContext(),"탐색 중단",Toast.LENGTH_SHORT).show();
        }
        else{
            if(mBTAdapter.isEnabled()) {
                mBTArrayAdapter.clear(); //아이템 초기화
                mBTAdapter.startDiscovery();
                Toast.makeText(getApplicationContext(), "탐색 시작", Toast.LENGTH_SHORT).show();
                registerReceiver(blReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
            }
            else{
                Toast.makeText(getApplicationContext(), "블루투스 꺼져 있음.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    final BroadcastReceiver blReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //목록에 기기이름 추가
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());
                mBTArrayAdapter.notifyDataSetChanged();
            }
        }
    };

    private void listPairedDevices(){
        mBTArrayAdapter.clear();
        mPairedDevices = mBTAdapter.getBondedDevices();
        if(mBTAdapter.isEnabled()) {
            //어댑터 하나 추가.
            for (BluetoothDevice device : mPairedDevices)
                mBTArrayAdapter.add(device.getName() + "\n" + device.getAddress());

            Toast.makeText(getApplicationContext(), "페이링된 기기 표시", Toast.LENGTH_SHORT).show();
        }
        else
            Toast.makeText(getApplicationContext(), "블루투스 꺼져 있음.", Toast.LENGTH_SHORT).show();
    }

    private void ledOn(){
        if (mConnectedThread != null) {
            mConnectedThread.write("1"); //LED 토글 클릭시 쓰레드에 1 입력
            mLedStatus.setText("LED On");
        }
    }
    private void ledOff(){
        if(mConnectedThread != null){
            mConnectedThread.write("3"); //LED 토글 해제시 쓰레드에 3 입력
            mLedStatus.setText("LED Off");
        }
    }
    private void blinkOn(){
        if(mConnectedThread != null){
            mConnectedThread.write("2"); //Blink 토글 클릭시 쓰레드에 2 입력
            mLedStatus.setText("LED가 깜박이는 중입니다.");
        }
    }
    private void blinkOff(){
        if(mConnectedThread != null){
            mConnectedThread.write("4"); //Blink 토글 해제시 쓰레드에 4 입력
            mLedStatus.setText("LED가 꺼졌습니다.");
        }
    }

    //ListView항목 클릭 시 호출되는 이벤트 리스너
    private AdapterView.OnItemClickListener mDeviceClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            //클릭 시 블루투스가 활성되어 있는지 확인
            if(!mBTAdapter.isEnabled()) { //비활성 시 Toast메시지로 사용자에게 알림
                Toast.makeText(getBaseContext(), "블루투스 꺼져 있음.", Toast.LENGTH_SHORT).show();
                return;
            }
            //활성화 시 클릭된 정보 추출
            mBluetoothStatus.setText("연결중...");
            //마지막 17자인 장치 MAC주소를 가져옴.
            String info = ((TextView) view).getText().toString();
            final String address = info.substring(info.length() - 17);
            final String name = info.substring(0,info.length() - 17);

            //GUI를 차단하지 않도록 새 스레드 생성
            new Thread()
            {
                @Override
                public void run() {
                    boolean fail = false;

                    BluetoothDevice device = mBTAdapter.getRemoteDevice(address);

                    try {
                        mBTSocket = createBluetoothSocket(device);
                    } catch (IOException e) {
                        fail = true;
                        Toast.makeText(getBaseContext(), "소켓 생성 실패", Toast.LENGTH_SHORT).show();
                    }
                    //블루투스 소켓 연결을 설정.
                    try {
                        mBTSocket.connect();
                    } catch (IOException e) {
                        try {
                            fail = true;
                            mBTSocket.close();
                            mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                    .sendToTarget();
                        } catch (IOException e2) {
                            //이를 처리하기 위한 코드 삽입
                            Toast.makeText(getBaseContext(), "소켓 생성 실패", Toast.LENGTH_SHORT).show();
                        }
                    }
                    if(!fail) {
                        mConnectedThread = new ConnectedThread(mBTSocket, mHandler);
                        mConnectedThread.start();

                        mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                                .sendToTarget();
                    }
                }
            }.start();
        }
    };

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        try {
            final Method m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", UUID.class);
            return (BluetoothSocket) m.invoke(device, BT_MODULE_UUID);
        } catch (Exception e) {
            Log.e(TAG, "Could not create Insecure RFComm Connection",e);
        }
        return  device.createRfcommSocketToServiceRecord(BT_MODULE_UUID);
    }
}
