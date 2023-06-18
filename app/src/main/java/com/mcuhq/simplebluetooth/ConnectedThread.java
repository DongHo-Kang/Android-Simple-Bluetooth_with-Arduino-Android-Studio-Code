package com.mcuhq.simplebluetooth;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ConnectedThread extends Thread {
    private final BluetoothSocket mmSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;
    private final Handler mHandler;

    public ConnectedThread(BluetoothSocket socket, Handler handler) {
        mmSocket = socket;
        mHandler = handler;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        // 입력 및 출력 스트림 가져오기
        // 멤버 스트림이 최종.
        try {
            tmpIn = socket.getInputStream();
            tmpOut = socket.getOutputStream();
        } catch (IOException e) { }

        mmInStream = tmpIn;
        mmOutStream = tmpOut;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[1024];  // 스트림에 대한 버퍼 저장소
        int bytes; // read()에서 반환된 바이트 수
        // 예외가 발생할 때까지 InputStream 대기
        while (true) {
            try {
                // InputStream 읽기
                bytes = mmInStream.available();
                if(bytes != 0) {
                    buffer = new byte[1024];
                    SystemClock.sleep(100); //데이터 일시 정지 및 대기. 전송 속도에 따라 값 조정.
                    bytes = mmInStream.available();
                    bytes = mmInStream.read(buffer, 0, bytes); // 읽힌 read()바이트 수 기록
                    mHandler.obtainMessage(MainActivity.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget(); // 받은 바이트 UI로 보내기.
                }
            } catch (IOException e) {
                e.printStackTrace();

                break;
            }
        }
    }

    /* 원격 장치로 데이터를 보내기 위한 기본활동 호출 */
    public void write(String input) {
        byte[] bytes = input.getBytes(); //입력한 문자열을 바이트로 변환.
        try {
            mmOutStream.write(bytes);
        } catch (IOException e) { }
    }

    /* 연결 종료 */
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) { }
    }
}