/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */

package com.ionicframework.test726684;

import java.util.Iterator;
import java.util.List;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import org.apache.cordova.*;

import com.easemob.chat.EMChat;

public class CordovaApp extends CordovaActivity
{
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        try{
            int pid = android.os.Process.myPid();
            String processAppName = getAppName(pid);
            // ����app������Զ�̵�service����application:onCreate�ᱻ����2��
            // Ϊ�˷�ֹ����SDK����ʼ��2�Σ��Ӵ��жϻᱣ֤SDK����ʼ��1��
            // Ĭ�ϵ�app�����԰���ΪĬ�ϵ�process name�����У������鵽��process name����app��process name����������

            if (processAppName == null ||!processAppName.equalsIgnoreCase("com.ionicframework.test726684")) {
                Log.e(TAG, "enter the service process!");
                //"com.easemob.chatuidemo"Ϊdemo�İ����������Լ���Ŀ��Ҫ�ĳ��Լ�����
                
                // ����application::onCreate �Ǳ�service ���õģ�ֱ�ӷ���
                return;
            }
            EMChat.getInstance().init(this);
            EMChat.getInstance().setDebugMode(true);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        super.init();
        
        // Set by <content src="index.html" /> in config.xml
        loadUrl(launchUrl);
    }
    private String getAppName(int pID) {
        String processName = null;
        ActivityManager am = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        List l = am.getRunningAppProcesses();
        Iterator i = l.iterator();
        PackageManager pm = this.getPackageManager();
        while (i.hasNext()) {
            ActivityManager.RunningAppProcessInfo info = (ActivityManager.RunningAppProcessInfo) (i.next());
            try {
                if (info.pid == pID) {
                    CharSequence c = pm.getApplicationLabel(pm.getApplicationInfo(info.processName, PackageManager.GET_META_DATA));
                    // Log.d("Process", "Id: "+ info.pid +" ProcessName: "+
                    // info.processName +"  Label: "+c.toString());
                    // processName = c.toString();
                    processName = info.processName;
                    return processName;
                }
            } catch (Exception e) {
                // Log.d("Process", "Error>> :"+ e.toString());
            }
        }
        return processName;
    }
}
