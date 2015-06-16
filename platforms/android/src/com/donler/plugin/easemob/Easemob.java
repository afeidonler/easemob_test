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
package com.donler.plugin.easemob;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.easemob.EMCallBack;
import com.easemob.EMEventListener;
import com.easemob.EMNotifierEvent;
import com.easemob.EMValueCallBack;
import com.easemob.chat.EMChatManager;
import com.easemob.chat.EMContactManager;
import com.easemob.chat.EMChatOptions;
import com.easemob.chat.EMConversation;
import com.easemob.chat.EMGroup;
import com.easemob.chat.EMGroupManager;
import com.easemob.chat.EMMessage;
import com.easemob.chat.EMMessage.ChatType;
import com.easemob.chat.EMMessage.Type;
import com.easemob.chat.EMChat;
import com.easemob.chat.ImageMessageBody;
import com.easemob.chat.LocationMessageBody;
import com.easemob.chat.NormalFileMessageBody;
import com.easemob.chat.TextMessageBody;
import com.easemob.chat.VoiceMessageBody;
import com.easemob.exceptions.EaseMobException;
import com.easemob.util.VoiceRecorder;
import com.easemob.util.EMLog;
import com.easemob.util.EasyUtils;
import com.donler.plugin.easemob.HXNotifier;
import com.donler.plugin.easemob.HXNotifier.HXNotificationInfoProvider;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

public class Easemob extends CordovaPlugin {
  private static final String TAG = "Easemob";
  private static int pagesize = 20;
  private static CallbackContext emchatCallbackContext = null;
  private static CordovaWebView webView = null;
  private static HXNotifier noifier = null;
  private VoiceRecorder voiceRecorder;
  private static ArrayList<String> eventQueue = new ArrayList<String>();
  protected static Boolean isInBackground = true;
  private static Activity mainActivity = null;
  private static Boolean deviceready = false;

  enum actionType {
    INIT, LOGIN, LOGOUT, CHAT, RECORDSTART, RECORDEND, RECORDCANCEL, GETMESSAGES, PAUSE, RESUME, GETUNREADMSGCOUNT, RESETUNRADMSGCOUNT, GETMSGCOUNT, DELETECONVERSATIONS, DELETECONVERSATION, GETGROUPS, GETGROUP, GETCONTACTS, ADDCONTACT, DELETECONTACT, SETTING
  }

  @SuppressLint("HandlerLeak")
  private Handler micImageHandler = new Handler() {
    @Override
    public void handleMessage(android.os.Message msg) {
      // ¼�������еĴ�������what������������С
      Log.d("Easemob", msg.toString());
      fireEvent("Record", "{what:" + msg.what + "}");
      // micImage.setImageDrawable(micImages[msg.what]);
    }
  };

  /**
   * Sets the context of the Command. This can then be used to do things like
   * get file paths associated with the Activity.
   * 
   * @param cordova
   *            The context of the main Activity.
   * @param webView
   *            The CordovaWebView Cordova is running in.
   */
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
    Easemob.webView = super.webView;
    mainActivity = cordova.getActivity();
  }

  /**
   * Executes the request and returns PluginResult.
   * 
   * @param action
   *            The action to execute.
   * @param args
   *            JSONArry of arguments for the plugin.
   * @param callbackContext
   *            The callback id used when calling back into JavaScript.
   * @return True if the action was valid, false if not.
   */
  public boolean execute(String action, JSONArray args,
      CallbackContext callbackContext) throws JSONException {

    Easemob.emchatCallbackContext = callbackContext;
    String target;
    EMConversation conversation;
    switch (actionType.valueOf(action.toUpperCase())) {
    case INIT:
      dealInit();
      break;
    case LOGIN:
      dealLogin(args);
      break;
    case LOGOUT:
      dealLogout();
      break;
    case CHAT:
      dealChat(args);
      break;
    case RECORDSTART:
      target = args.getString(0);
      voiceRecorder = new VoiceRecorder(micImageHandler);
      voiceRecorder.startRecording(null, target, cordova.getActivity()
          .getApplicationContext());
      break;
    case RECORDEND:
      if (voiceRecorder == null) {
        emchatCallbackContext.error("��ǰû��¼��");
      } else {
        String chatType = args.getString(0);
        target = args.getString(1);
        // ��ȡ���������˵ĻỰ���󡣲���usernameΪ�����˵�userid����groupid�������е�username�������
        conversation = EMChatManager.getInstance().getConversation(
            target);
        int length = voiceRecorder.stopRecoding();
        if (length > 0) {
          sendVoice(conversation, chatType, target,
              voiceRecorder.getVoiceFilePath(),
              voiceRecorder.getVoiceFileName(target),
              Integer.toString(length), false);
        } else {
          emchatCallbackContext.error("¼��ʱ�����");
        }
      }

      break;
    case RECORDCANCEL:
      if (voiceRecorder != null) {
        voiceRecorder.discardRecording();
        emchatCallbackContext.success("¼��ȡ��");
      } else {
        emchatCallbackContext.error("��ǰû��¼��");
      }

      break;
    case GETMESSAGES:
      dealGetMessages(args);
      break;
    case PAUSE:
      isInBackground = true;
      try {
        // ֹͣ¼��
        if (voiceRecorder.isRecording()) {
          voiceRecorder.discardRecording();
        }
      } catch (Exception e) {
      }
      break;
    case RESUME:
      isInBackground = false;
      cordova.getThreadPool().execute(new Runnable() {
        public void run() {
          deviceready();
          if (noifier != null)
            noifier.reset();
        }
      });
      break;
    case GETUNREADMSGCOUNT:
      target = args.getString(0);
      conversation = EMChatManager.getInstance().getConversation(target);
      emchatCallbackContext.success(conversation.getUnreadMsgCount());
      break;
    case RESETUNRADMSGCOUNT:
      if (args.length() == 0) {
        EMChatManager.getInstance().resetAllUnreadMsgCount();
      } else {
        target = args.getString(0);
        conversation = EMChatManager.getInstance().getConversation(
            target);
        conversation.resetUnreadMsgCount();
      }
      emchatCallbackContext.success();
      break;
    case GETMSGCOUNT:
      target = args.getString(0);
      conversation = EMChatManager.getInstance().getConversation(target);
      emchatCallbackContext.success(conversation.getMsgCount());
      break;
    case DELETECONVERSATIONS:
      target = args.getString(0);
      EMChatManager.getInstance().clearConversation(target);
      // ɾ����ĳ��user�������������¼(��������)
      // EMChatManager.getInstance().deleteConversation(target);
      emchatCallbackContext.success();
      break;
    case DELETECONVERSATION:
      target = args.getString(0);
      String msgId = args.getString(0);
      // ɾ����ǰ�Ự��ĳ�������¼
      conversation = EMChatManager.getInstance().getConversation(target);
      conversation.removeMessage(msgId);
      emchatCallbackContext.success();
      break;
    case GETGROUPS:
      Boolean serverFlag = args.getBoolean(0);
      if (serverFlag) {
        EMGroupManager.getInstance().asyncGetGroupsFromServer(
            new EMValueCallBack<List<EMGroup>>() {
              @Override
              public void onSuccess(List<EMGroup> value) {
                emchatCallbackContext
                    .success(groupsToJson(value));
              }

              @Override
              public void onError(int error, String errorMsg) {
                emchatCallbackContext.error(errorMsg);
              }
            });
      } else {
        // �ӱ��ؼ���Ⱥ���б�
        List<EMGroup> grouplist = EMGroupManager.getInstance()
            .getAllGroups();
        emchatCallbackContext.success(groupsToJson(grouplist));
      }

      break;
    case GETGROUP:
      target = args.getString(0);
      Boolean serverFlag1 = args.getBoolean(1);
      if (serverFlag1) {
        // ����Ⱥ��ID�ӷ�������ȡȺ����Ϣ
        EMGroup group;
        try {
          group = EMGroupManager.getInstance().getGroupFromServer(
              target);
          emchatCallbackContext.success(groupToJson(group));
          // �����ȡ������Ⱥ����Ϣ
          EMGroupManager.getInstance()
              .createOrUpdateLocalGroup(group);
        } catch (EaseMobException e) {
          e.printStackTrace();
          emchatCallbackContext.success("��ȡȺ����Ϣʧ��");
        }

      } else {
        // ����Ⱥ��ID�ӱ��ػ�ȡȺ����Ϣ
        EMGroup group = EMGroupManager.getInstance().getGroup(target);
        // group.getMembers();//��ȡȺ��Ա
        // group.getOwner();//��ȡȺ��
        emchatCallbackContext.success(group.toString());
      }

      break;
    case GETCONTACTS:
      try {
        List<String> usernames = EMContactManager.getInstance()
            .getContactUserNames();// ���첽ִ��
        JSONArray mJSONArray = new JSONArray(usernames);
        emchatCallbackContext.success(mJSONArray);
      } catch (EaseMobException e) {
        emchatCallbackContext.error(e.toString());
      }
      break;
    case ADDCONTACT:
      try {
        target = args.getString(0);
        String reason;
        if (args.length() > 1) {
          reason = args.getString(1);
        } else {
          reason = "";
        }
        // ����ΪҪ��ӵĺ��ѵ�username���������
        EMContactManager.getInstance().addContact(target, reason);// ���첽����
        emchatCallbackContext.success("�ɹ�");
      } catch (EaseMobException e) {
        emchatCallbackContext.error(e.toString());
      }
      break;
    case DELETECONTACT:
      try {
        target = args.getString(0);
        EMContactManager.getInstance().deleteContact(target);// ���첽����
        emchatCallbackContext.success("�ɹ�");
      } catch (EaseMobException e) {
        emchatCallbackContext.error(e.toString());
      }
      break;
    case SETTING:
      dealSetting(args);
      break;

    default:
      return false;
    }
    return true;
    // return super.execute(action, args, callbackContext);
  }

  /**
   * �����ı���Ϣ
   * 
   * @param content
   *            message content
   * @param isResend
   *            boolean resend
   */
  // private void sendText(String content) {

  // if (content.length() > 0) {
  // EMMessage message = EMMessage.createSendMessage(EMMessage.Type.TXT);
  // // �����Ⱥ�ģ�����chattype,Ĭ���ǵ���
  // if (chatType == CHATTYPE_GROUP){
  // message.setChatType(ChatType.GroupChat);
  // }else if(chatType == CHATTYPE_CHATROOM){
  // message.setChatType(ChatType.ChatRoom);
  // }

  // TextMessageBody txtBody = new TextMessageBody(content);
  // // ������Ϣbody
  // message.addBody(txtBody);
  // // ����Ҫ����˭,�û�username����Ⱥ��groupid
  // message.setReceipt(toChatUsername);
  // // ��messgage�ӵ�conversation��
  // conversation.addMessage(message);
  // // ֪ͨadapter����Ϣ�䶯��adapter����ݼ��������message��ʾ��Ϣ�͵���sdk�ķ��ͷ���
  // adapter.refreshSelectLast();
  // mEditTextContent.setText("");

  // setResult(RESULT_OK);

  // }
  // }
  /**
   * ��������
   * 
   * @param filePath
   * @param fileName
   * @param length
   * @param isResend
   */
  private void sendVoice(EMConversation conversation, String chatType,
      String toChatUsername, String filePath, String fileName,
      String length, boolean isResend) {
    if (!(new File(filePath).exists())) {
      return;
    }
    try {
      final EMMessage message = EMMessage
          .createSendMessage(EMMessage.Type.VOICE);
      // �����Ⱥ�ģ�����chattype,Ĭ���ǵ���
      if (chatType == "group") {
        message.setChatType(ChatType.GroupChat);
      } else if (chatType == "chatroom") {
        message.setChatType(ChatType.ChatRoom);
      }
      message.setReceipt(toChatUsername);
      int len = Integer.parseInt(length);
      VoiceMessageBody body = new VoiceMessageBody(new File(filePath),
          len);
      message.addBody(body);

      conversation.addMessage(message);
      // ������Ϣ
      EMChatManager.getInstance().sendMessage(message, new EMCallBack() {
        @Override
        public void onSuccess() {
          Log.d("main", "���ͳɹ�");
          emchatCallbackContext.success(messageToJson(message));
        }

        @Override
        public void onProgress(int progress, String status) {

        }

        @Override
        public void onError(int code, String errorMessage) {
          Log.d("main", "����ʧ�ܣ�");
          emchatCallbackContext.error(messageToJson(message));
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * ����ͼƬ
   * 
   * @param filePath
   */
  // private void sendPicture(final String filePath) {
  // String to = toChatUsername;
  // // create and add image message in view
  // final EMMessage message =
  // EMMessage.createSendMessage(EMMessage.Type.IMAGE);
  // // �����Ⱥ�ģ�����chattype,Ĭ���ǵ���
  // if (chatType == CHATTYPE_GROUP){
  // message.setChatType(ChatType.GroupChat);
  // }else if(chatType == CHATTYPE_CHATROOM){
  // message.setChatType(ChatType.ChatRoom);
  // }

  // message.setReceipt(to);
  // ImageMessageBody body = new ImageMessageBody(new File(filePath));
  // // Ĭ�ϳ���100k��ͼƬ��ѹ���󷢸��Է����������óɷ���ԭͼ
  // // body.setSendOriginalImage(true);
  // message.addBody(body);
  // conversation.addMessage(message);

  // listView.setAdapter(adapter);
  // adapter.refreshSelectLast();
  // setResult(RESULT_OK);
  // // more(more);
  // }

  /**
   * ������Ƶ��Ϣ
   */
  // private void sendVideo(final String filePath, final String thumbPath,
  // final int length) {
  // final File videoFile = new File(filePath);
  // if (!videoFile.exists()) {
  // return;
  // }
  // try {
  // EMMessage message = EMMessage.createSendMessage(EMMessage.Type.VIDEO);
  // // �����Ⱥ�ģ�����chattype,Ĭ���ǵ���
  // if (chatType == CHATTYPE_GROUP){
  // message.setChatType(ChatType.GroupChat);
  // }else if(chatType == CHATTYPE_CHATROOM){
  // message.setChatType(ChatType.ChatRoom);
  // }
  // String to = toChatUsername;
  // message.setReceipt(to);
  // VideoMessageBody body = new VideoMessageBody(videoFile, thumbPath,
  // length, videoFile.length());
  // message.addBody(body);
  // conversation.addMessage(message);
  // listView.setAdapter(adapter);
  // adapter.refreshSelectLast();
  // setResult(RESULT_OK);
  // } catch (Exception e) {
  // e.printStackTrace();
  // }

  // }
  /**
   * Calls all pending callbacks after the deviceready event has been fired.
   */
  private static void deviceready() {
    deviceready = true;

    for (String js : eventQueue) {
      webView.sendJavascript(js);
    }

    eventQueue.clear();
  }

  /**
   * Fires the given event.
   * 
   * @param {String} event The Name of the event
   * @param {String} json A custom (JSON) string
   */
  public static void fireEvent(String event, String json) {
    String js = "setTimeout(easemob.on" + event + "(" + json + "),0)";
    if (deviceready == false) {
      eventQueue.add(js);
    } else {
      webView.sendJavascript(js);
    }
  }

  private void dealInit() {
    try {
      int pid = android.os.Process.myPid();
      String processAppName = getAppName(pid);
      // ���app������Զ�̵�service����application:onCreate�ᱻ����2��
      // Ϊ�˷�ֹ����SDK����ʼ��2�Σ��Ӵ��жϻᱣ֤SDK����ʼ��1��
      // Ĭ�ϵ�app�����԰���ΪĬ�ϵ�process name�����У�����鵽��process name����app��process
      // name����������
      if (processAppName == null
          || !processAppName.equalsIgnoreCase(cordova.getActivity()
              .getPackageName())) {
        Log.e(TAG, "enter the service process!");
        // ���application::onCreate �Ǳ�service ���õģ�ֱ�ӷ���
        return;
      }
      EMChat.getInstance().init(mainActivity);
      bindListener();
      // debug ģʽ����
      EMChat.getInstance().setDebugMode(true);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void dealChat(JSONArray _args) {
    final JSONArray args = _args;
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        String chatType, target, _contentType;
        JSONObject params, content, extend;
        Type contentType;
        final EMMessage message;
        try {
          params = args.getJSONObject(0);
          target = params.getString("target");
          EMConversation conversation = EMChatManager.getInstance()
              .getConversation(target);
          if (params.has("resend") && params.getBoolean("resend")) {
            String msgId = params.getString("msgId");
            message = conversation.getMessage(msgId);
            message.status = EMMessage.Status.CREATE;
          } else {
            chatType = params.getString("chatType");
            _contentType = params.getString("contentType");
            content = params.getJSONObject("content");
            contentType = EMMessage.Type.valueOf(_contentType
                .toUpperCase());
            // ��ȡ���������˵ĻỰ���󡣲���usernameΪ�����˵�userid����groupid�������е�username�������

            message = EMMessage.createSendMessage(contentType);
            // �����Ⱥ�ģ�����chattype,Ĭ���ǵ���
            if (chatType.equals("group")) {
              message.setChatType(ChatType.GroupChat);
            }
            switch (contentType) {
            case VOICE:
              VoiceMessageBody voiceBody = new VoiceMessageBody(
                  new File(content.getString("filePath")),
                  content.getInt("len"));
              message.addBody(voiceBody);
              break;
            case IMAGE:
              String path;
              try {
                path = getRealPathFromURI(content
                    .getString("filePath"));
              } catch (Exception e) {
                emchatCallbackContext.error("����ʧ�ܣ�");
                return;
              }

              File file = new File(path);

              ImageMessageBody imageBody = new ImageMessageBody(
                  file);
              // Ĭ�ϳ���100k��ͼƬ��ѹ���󷢸��Է����������óɷ���ԭͼ
              // body.setSendOriginalImage(true);
              message.addBody(imageBody);
              break;
            case LOCATION:
              LocationMessageBody locationBody = new LocationMessageBody(
                  content.getString("locationAddress"),
                  content.getDouble("latitude"), content
                      .getDouble("longitude"));
              message.addBody(locationBody);
              break;
            case FILE:
              NormalFileMessageBody fileBody = new NormalFileMessageBody(
                  new File(content.getString("filePath")));
              message.addBody(fileBody);
              break;
            case TXT:
            default:
              // ������Ϣbody
              TextMessageBody textBody = new TextMessageBody(
                  content.getString("text"));
              message.addBody(textBody);
              break;
            }
            if (params.has("extend")) {
              extend = params.getJSONObject("extend");
              message.setAttribute("extend", extend);
              // Iterator it = extend.keys();
              // while (it.hasNext()) {
              // String key = (String) it.next();
              // Object v = extend.get(key);
              // if (v instanceof Integer || v instanceof Long ||
              // v instanceof Float || v instanceof Double) {
              // int value = ((Number)v).intValue();
              // message.setAttribute(key, value);
              // } else if (v instanceof Boolean) {
              // boolean boolToUse = ((Boolean)v).booleanValue();
              // message.setAttribute(key, boolToUse);
              // } else {
              // String stringToUse = extend.getString(key);
              // message.setAttribute(key, stringToUse);
              // }

              // }
            }

            // ���ý�����
            message.setReceipt(target);
            // ����Ϣ���뵽�˻Ự������
            conversation.addMessage(message);
          }

          // ������Ϣ
          EMChatManager.getInstance().sendMessage(message,
              new EMCallBack() {
                @Override
                public void onSuccess() {
                  Log.d("main", "���ͳɹ�");
                  emchatCallbackContext
                      .success(messageToJson(message));
                }

                @Override
                public void onProgress(int progress,
                    String status) {
                }

                @Override
                public void onError(int code,
                    String errorMessage) {
                  Log.d("main", "����ʧ�ܣ�");
                  JSONObject obj = messageToJson(message);
                  emchatCallbackContext.error(obj);
                }
              });
        } catch (JSONException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
          emchatCallbackContext.error("��������");
          return;
        } catch (IllegalArgumentException e) {
          emchatCallbackContext.error("CHAT���ʹ���");
        }
      }

    });

  }

  private void dealLogin(JSONArray args) {
    String user, psword;
    try {
      user = args.getString(0);
      psword = args.getString(1);
      EMChatManager.getInstance().login(user, psword, new EMCallBack() {// �ص�
            @Override
            public void onSuccess() {
              cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                  EMGroupManager.getInstance()
                      .loadAllGroups();
                  EMChatManager.getInstance()
                      .loadAllConversations();
                  Log.d("main", "��½����������ɹ���");
                  emchatCallbackContext.success("��½����������ɹ���");
                }
              });
            }

            @Override
            public void onProgress(int progress, String status) {

            }

            @Override
            public void onError(int code, String message) {
              Log.d("main", "��½���������ʧ�ܣ�");
              emchatCallbackContext.error("��½���������ʧ�ܣ�");
            }
          });
    } catch (JSONException e) {
      e.printStackTrace();
      emchatCallbackContext.error("���ʹ���");
    }

  }

  private void dealLogout() {
    // �˷���Ϊ�첽����
    EMChatManager.getInstance().logout(new EMCallBack() {

      @Override
      public void onSuccess() {
        // ��������յ��ص�����ִ�н�������¼�
        EMChatManager.getInstance().unregisterEventListener(
            new EMEventListener() {

              @Override
              public void onEvent(EMNotifierEvent event) {
                // TODO Auto-generated method stub

              }
            });
        emchatCallbackContext.success("�˳��ɹ���");
      }

      @Override
      public void onProgress(int progress, String status) {
        // TODO Auto-generated method stub

      }

      @Override
      public void onError(int code, String message) {
        emchatCallbackContext.error("�˳�ʧ�ܣ�");
      }
    });

  }

  private void dealGetMessages(JSONArray args) {
    final JSONArray _args = args;

    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          String chatType = _args.getString(0);
          String target = _args.getString(1);
          EMConversation conversation = EMChatManager.getInstance()
              .getConversation(target);
          List<EMMessage> messages;
          if (_args.length() < 3) {
            // ��ȡ�˻Ự��������Ϣ
            messages = conversation.getAllMessages();
          } else {
            final String startMsgId = _args.getString(2);
            if (chatType.equals("group")) {
              // �����Ⱥ�ģ���������˷���
              messages = conversation.loadMoreGroupMsgFromDB(
                  startMsgId, pagesize);
            } else {
              // sdk��ʼ�����ص������¼Ϊ20��������ʱ��Ҫȥdb���ȡ����
              // ��ȡstartMsgId֮ǰ��pagesize����Ϣ���˷�����ȡ��messages
              // sdk���Զ����뵽�˻Ự�У�app�������ٴΰѻ�ȡ����messages��ӵ��Ự��
              messages = conversation.loadMoreMsgFromDB(
                  startMsgId, pagesize);
            }
          }
          // δ����Ϣ������
          conversation.resetUnreadMsgCount();
          JSONArray mJSONArray = new JSONArray();
          for (int i = 0; i < messages.size(); i++) {
            JSONObject message = messageToJson(messages.get(i));
            mJSONArray.put(message);
          }
          PluginResult pluginResult = new PluginResult(
              PluginResult.Status.OK, mJSONArray);
          emchatCallbackContext.sendPluginResult(pluginResult);
        } catch (JSONException e) {
          e.printStackTrace();
          emchatCallbackContext.error("��ȡ�����¼ʧ�ܣ�");
        }
      }

    });

  }

  private void dealSetting(JSONArray args) {
    JSONObject params;
    try {
      params = args.getJSONObject(0);
      // ���Ȼ�ȡEMChatOptions
      EMChatOptions chatOptions = EMChatManager.getInstance()
          .getChatOptions();
      if (params.has("NotifyBySoundAndVibrate")) {
        // �����Ƿ���������Ϣ����(�򿪻��߹ر���Ϣ����������ʾ)
        chatOptions.setNotifyBySoundAndVibrate(params
            .getBoolean("NotifyBySoundAndVibrate")); // Ĭ��Ϊtrue
                                  // ��������Ϣ����
      }
      if (params.has("NoticeBySound")) {
        // �����Ƿ���������Ϣ��������
        chatOptions
            .setNoticeBySound(params.getBoolean("NoticeBySound")); // Ĭ��Ϊtrue
                                        // ������������

      }
      if (params.has("NoticedByVibrate")) {
        // �����Ƿ���������Ϣ������
        chatOptions.setNoticedByVibrate(params
            .getBoolean("NoticedByVibrate")); // Ĭ��Ϊtrue ��������Ϣ����
      }
      if (params.has("UseSpeaker")) {
        // ����������Ϣ�����Ƿ�����Ϊ����������
        chatOptions.setUseSpeaker(params.getBoolean("UseSpeaker")); // Ĭ��Ϊtrue
                                      // ��������Ϣ����
      }
      if (params.has("ShowNotificationInBackgroud")) {
        // ���ú�̨��������Ϣʱ�Ƿ�֪ͨͨ����ʾ
        chatOptions.setShowNotificationInBackgroud(params
            .getBoolean("ShowNotificationInBackgroud")); // Ĭ��Ϊtrue
                                    // ��������Ϣ����
      }
      emchatCallbackContext.success("���óɹ�");
    } catch (JSONException e) {
      e.printStackTrace();
      emchatCallbackContext.error("����ʧ��");
    }

  }

  private void bindListener() {
    noifier = new HXNotifier();
    noifier.init(cordova.getActivity().getApplicationContext());
    // EMChatOptions chatOptions = EMChatManager.getInstance()
    // .getChatOptions();
    // chatOptions.setOnNotificationClickListener(getOnNotificationClickListener());
    // ������Ϣ��������
    // noifier.setNotificationInfoProvider(getNotificationListener());
    // �������е�event�¼�
    EMChatManager.getInstance().registerEventListener(
        new EMEventListener() {

          @Override
          public void onEvent(EMNotifierEvent event) {
            EMMessage message = null;
            Context appContext = cordova.getActivity()
                .getApplicationContext();
            if (event.getData() instanceof EMMessage) {
              message = (EMMessage) event.getData();
              EMLog.d(TAG,
                  "receive the event : " + event.getEvent()
                      + ",id : " + message.getMsgId());
            }

            switch (event.getEvent()) {
            case EventNewMessage:
              // Ӧ���ں�̨������Ҫˢ��UI,֪ͨ����ʾ����Ϣ
              if (!EasyUtils.isAppRunningForeground(appContext)) {
                EMLog.d(TAG, "app is running in backgroud");
                noifier.onNewMsg(message);
              } else {
                String msg = messageToJson(message).toString();
                fireEvent("ReciveMessage", msg);
                EMLog.d(TAG, message.toString());
              }
              break;
            case EventOfflineMessage:
              @SuppressWarnings("unchecked")
              List<EMMessage> messages = (List<EMMessage>) event
                  .getData();
              if (!EasyUtils.isAppRunningForeground(appContext)) {
                EMLog.d(TAG, "received offline messages");
                noifier.onNewMesg(messages);
              } else {

                JSONArray mJSONArray = new JSONArray();
                for (int i = 0; i < messages.size(); i++) {
                  JSONObject _message = messageToJson(messages
                      .get(i));
                  mJSONArray.put(_message);
                }
                fireEvent("ReciveMessage",
                    mJSONArray.toString());
                EMLog.d(TAG, message.toString());
              }
              break;
            // below is just
            // giving a example
            // to show a cmd
            // toast, the app
            // should not follow
            // this
            // so be careful of
            // this
            case EventNewCMDMessage:
              break;
            case EventDeliveryAck:
              message.setDelivered(true);
              break;
            case EventReadAck:
              message.setAcked(true);
              break;
            // add other events
            // in case you are
            // interested in
            default:
              break;
            }
          };
        });
  }

  /**
   * ��ϢתΪjson��ʽ
   * 
   * @param message
   *            ��Ϣ
   * @return json��ʽ��message
   */
  public static JSONObject messageToJson(EMMessage message) {

    JSONObject msgJson = new JSONObject();

    try {
      msgJson.put("direct", message.direct)
          .put("type", message.getType().toString())
          .put("status", message.status)
          .put("isAcked", message.isAcked)
          .put("progress", message.progress)
          .put("isDelivered", message.isDelivered)
          .put("msgTime", message.getMsgTime())
          .put("from", message.getFrom()).put("to", message.getTo())
          .put("msgId", message.getMsgId())
          .put("chatType", message.getChatType())
          .put("unRead", message.isUnread())
          .put("isListened", message.isListened())
          .put("userName", message.getUserName());

      JSONObject body = new JSONObject();

      switch (message.getType()) {
      case VOICE:
        VoiceMessageBody voiceBody = (VoiceMessageBody) message
            .getBody();
        body.put("localUrl", voiceBody.getLocalUrl())
            .put("remoteUrl", voiceBody.getRemoteUrl())
            .put("name", voiceBody.getFileName())
            .put("size", voiceBody.getLength());
        break;
      case IMAGE:
        ImageMessageBody imageBody = (ImageMessageBody) message
            .getBody();
          body.put("localUrl", imageBody.getLocalUrl())
              .put("remoteUrl", imageBody.getRemoteUrl())
              .put("thumbnailUrl", imageBody.getThumbnailUrl());
          

        break;
      case LOCATION:
        LocationMessageBody locationBody = (LocationMessageBody) message
            .getBody();
        body.put("address", locationBody.getAddress())
            .put("latitude", locationBody.getLatitude())
            .put("longitude", locationBody.getLongitude());
        break;
      case FILE:
        NormalFileMessageBody fileBody = (NormalFileMessageBody) message
            .getBody();
        body.put("name", fileBody.getFileName())
            .put("size", fileBody.getFileSize())
            .put("localUrl", fileBody.getLocalUrl())
            .put("remoteUrl", fileBody.getRemoteUrl());

        break;
      case TXT:
      default:
        TextMessageBody txtBody = (TextMessageBody) message.getBody();
        body.put("text", txtBody.getMessage());
        break;
      }
      try {
        msgJson.put("extend", message.getJSONObjectAttribute("extend"));
      } catch (EaseMobException e) {
        e.printStackTrace();
      }
      msgJson.put("body", body);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    return msgJson;

  }

  /**
   * Ⱥ��תΪjson��ʽ
   * 
   * @param group
   *            Ⱥ����Ϣ
   * @return json��ʽ��Ⱥ����Ϣ
   */
  public static JSONObject groupToJson(EMGroup group) {

    JSONObject msgJson = new JSONObject();

    try {
      msgJson.put("description", group.getDescription())
          .put("id", group.getGroupId())
          .put("name", group.getGroupName())
          .put("memberCount", group.getAffiliationsCount());
    } catch (JSONException e) {
      e.printStackTrace();
    }

    return msgJson;

  }

  /**
   * Ⱥ��תΪjsonArray��ʽ
   * 
   * @param groups
   *            Ⱥ����Ϣ
   * @return json��ʽ��Ⱥ����Ϣ
   */
  public static JSONArray groupsToJson(List<EMGroup> groups) {

    JSONArray mJSONArray = new JSONArray();
    for (int i = 0; i < groups.size(); i++) {
      JSONObject _group = groupToJson(groups.get(i));
      mJSONArray.put(_group);
    }

    return mJSONArray;

  }

  /**
   * �Զ���֪ͨ����ʾ����
   * 
   * @return
   */
  protected HXNotificationInfoProvider getNotificationListener() {
    // ���Ը���Ĭ�ϵ�����
    return new HXNotificationInfoProvider() {

      @Override
      public String getTitle(EMMessage message) {
        // �޸ı���,����ʹ��Ĭ��
        return null;
      }

      @Override
      public int getSmallIcon(EMMessage message) {
        // ����Сͼ�꣬����ΪĬ��
        return 0;
      }

      @Override
      public String getDisplayedText(EMMessage message) {
        // ����״̬������Ϣ��ʾ�����Ը���message����������Ӧ��ʾ
        // String ticker = CommonUtils.getMessageDigest(message,
        // appContext);
        // if(message.getType() == Type.TXT){
        // ticker = ticker.replaceAll("\\[.{2,3}\\]", "[����]");
        // }

        // return message.getFrom() + ": " + ticker;
        return null;
      }

      @Override
      public String getLatestText(EMMessage message, int fromUsersNum,
          int messageNum) {
        return null;
        // return fromUsersNum + "�����ѣ�������" + messageNum + "����Ϣ";
      }

      @Override
      public Intent getLaunchIntent(EMMessage message) {
        // ���õ��֪ͨ����ת�¼�
        // String msg = messageToJson(message).toString();
        // fireEvent("ClickNotification", msg);
        return null;
      }
    };
  }

  private String getRealPathFromURI(String uriStr) {
    if (uriStr.indexOf("file:") == 0) {
      return uriStr.substring(7);
    } else {
      Uri contentUri = Uri.parse(uriStr);
      Cursor cursor = cordova.getActivity().getContentResolver()
          .query(contentUri, null, null, null, null);
      if (cursor != null) {
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex("_data");
        String picturePath = cursor.getString(columnIndex);
        cursor.close();
        cursor = null;

        if (picturePath == null || picturePath.equals("null")) {
          throw new IllegalArgumentException("null");
        }
        return picturePath;
      } else {
        return contentUri.getPath();
      }
    }

  }

  private String getAppName(int pID) {
    String processName = null;
    ActivityManager am = (ActivityManager) cordova.getActivity()
        .getSystemService(android.content.Context.ACTIVITY_SERVICE);
    List l = am.getRunningAppProcesses();
    Iterator i = l.iterator();
    PackageManager pm = cordova.getActivity().getPackageManager();
    while (i.hasNext()) {
      ActivityManager.RunningAppProcessInfo info = (ActivityManager.RunningAppProcessInfo) (i
          .next());
      try {
        if (info.pid == pID) {
          CharSequence c = pm.getApplicationLabel(pm
              .getApplicationInfo(info.processName,
                  PackageManager.GET_META_DATA));
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
