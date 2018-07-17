package com.example.usbhidspeedtester.udp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.os.Build;

public class UDP_Client{
        private AsyncTask<Void, Void, Void> async_task;
        private InetAddress inetAddress;
        private int port;

        public UDP_Client(InetAddress inetAddress, int port){
            this.inetAddress = inetAddress;
            this.port = port;
        }

        @SuppressLint("NewApi")
        public void send(final String message)
        {
            async_task = new AsyncTask<Void, Void, Void>()
            {
                @Override
                protected Void doInBackground(Void... params)
                {
                    DatagramSocket ds = null;

                    try
                    {
                        ds = new DatagramSocket();
                        DatagramPacket dp;
                        dp = new DatagramPacket(message.getBytes(), message.length(), inetAddress, port);
                        ds.send(dp);
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                    finally
                    {
                        if (ds != null)
                        {
                            ds.close();
                        }
                    }
                    return null;
                }

                protected void onPostExecute(Void result)
                {
                    super.onPostExecute(result);
                }
            };

            if (Build.VERSION.SDK_INT >= 11) async_task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            else async_task.execute();
        }
}