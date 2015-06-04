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

//姝ゅ�����������瑕���� XML�����㈢��������涓����
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
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
import com.easemob.chat.EMChatManager;
import com.easemob.chat.EMChatOptions;
import com.easemob.chat.EMConversation;
import com.easemob.chat.EMGroupManager;
import com.easemob.chat.EMMessage;
import com.easemob.chat.EMMessage.ChatType;
import com.easemob.chat.EMMessage.Type;
import com.easemob.chat.ImageMessageBody;
import com.easemob.chat.LocationMessageBody;
import com.easemob.chat.MessageBody;
import com.easemob.chat.NormalFileMessageBody;
import com.easemob.chat.OnNotificationClickListener;
import com.easemob.chat.TextMessageBody;
import com.easemob.chat.VideoMessageBody;
import com.easemob.chat.VoiceMessageBody;
import com.easemob.util.VoiceRecorder;
import com.easemob.util.EMLog;
import com.easemob.util.EasyUtils;
import com.donler.plugin.easemob.HXNotifier;
import com.donler.plugin.easemob.HXNotifier.HXNotificationInfoProvider;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

public class Easemob extends CordovaPlugin {
	private static final String TAG = "Easemob";
	private static int pagesize = 20;
	private static CallbackContext emchatCallbackContext = null;
	private static CordovaWebView webView = null;
	private static HXNotifier noifier = null;
	private VoiceRecorder voiceRecorder;
	private static ArrayList<String> eventQueue = new ArrayList<String>();
	protected static Boolean isInBackground = true;
	private static Boolean deviceready = false;
	enum actionType {
		login,
		logout,
		chat,
		recordstart,
		recordend,
		recordcancel,
		getMessages,
		pause,
		resume
	}
	private Handler micImageHandler = new Handler() {
		@Override
		public void handleMessage(android.os.Message msg) {
			// 切换msg切换图片
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
		// 杩�������args n灏辨��JS cordova.exec()���绗�浜�涓�������
		// 浠�JSON��煎��寰���帮��涓�瀹�瑕�瀹�椤哄��

		Easemob.emchatCallbackContext = callbackContext;
		if (action.equals("login")) {
			dealLogin(args);
		} else if (action.equals("logout")) {
			// 此方法为异步方法
			EMChatManager.getInstance().logout(new EMCallBack() {

				@Override
				public void onSuccess() {
					// TODO Auto-generated method stub
					emchatCallbackContext.success("退出成功！");
				}

				@Override
				public void onProgress(int progress, String status) {
					// TODO Auto-generated method stub

				}

				@Override
				public void onError(int code, String message) {
					// TODO Auto-generated method stub
					emchatCallbackContext.error("退出失败！");
				}
			});
		} else if (action.equals("chat")) {
			dealChat(args);
		} else if (action.equals("recordstart")) {
			final String target = args.getString(0);
			voiceRecorder = new VoiceRecorder(micImageHandler);
			voiceRecorder.startRecording(null, target, cordova.getActivity()
					.getApplicationContext());
		} else if (action.equals("recordend")) {
			final String chatType = args.getString(0);
			final String target = args.getString(1);
			// 获取到与聊天人的会话对象。参数username为聊天人的userid或者groupid，后文中的username皆是如此
			EMConversation conversation = EMChatManager.getInstance()
					.getConversation(target);
			int length = voiceRecorder.stopRecoding();
			if (length > 0) {
				sendVoice(conversation, chatType, target,
						voiceRecorder.getVoiceFilePath(),
						voiceRecorder.getVoiceFileName(target),
						Integer.toString(length), false);
			}
		} else if (action.equals("recordcancel")) {
			if (voiceRecorder != null)
				voiceRecorder.discardRecording();
		} else if (action.equals("getMessages")) {
			dealGetMessages(args);
		} else if (action.equalsIgnoreCase("pause")) {
			isInBackground = true;
		}

		else if (action.equalsIgnoreCase("resume")) {
			isInBackground = false;
			cordova.getThreadPool().execute(new Runnable() {
				public void run() {
					deviceready();
				}
			});
		}
		return true;
		// return super.execute(action, args, callbackContext);
	}

	/**
	 * 发送文本消息
	 * 
	 * @param content
	 *            message content
	 * @param isResend
	 *            boolean resend
	 */
	// private void sendText(String content) {

	// if (content.length() > 0) {
	// EMMessage message = EMMessage.createSendMessage(EMMessage.Type.TXT);
	// // 如果是群聊，设置chattype,默认是单聊
	// if (chatType == CHATTYPE_GROUP){
	// message.setChatType(ChatType.GroupChat);
	// }else if(chatType == CHATTYPE_CHATROOM){
	// message.setChatType(ChatType.ChatRoom);
	// }

	// TextMessageBody txtBody = new TextMessageBody(content);
	// // 设置消息body
	// message.addBody(txtBody);
	// // 设置要发给谁,用户username或者群聊groupid
	// message.setReceipt(toChatUsername);
	// // 把messgage加到conversation中
	// conversation.addMessage(message);
	// // 通知adapter有消息变动，adapter会根据加入的这条message显示消息和调用sdk的发送方法
	// adapter.refreshSelectLast();
	// mEditTextContent.setText("");

	// setResult(RESULT_OK);

	// }
	// }
	/**
	 * 发送语音
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
			// 如果是群聊，设置chattype,默认是单聊
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
			// 发送消息
			EMChatManager.getInstance().sendMessage(message, new EMCallBack() {
				@Override
				public void onSuccess() {
					Log.d("main", "发送成功");
					emchatCallbackContext.success("发送成功");
				}

				@Override
				public void onProgress(int progress, String status) {

				}

				@Override
				public void onError(int code, String message) {
					Log.d("main", "发送失败！");
					emchatCallbackContext.error("发送失败！");
				}
			});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 发送图片
	 * 
	 * @param filePath
	 */
	// private void sendPicture(final String filePath) {
	// String to = toChatUsername;
	// // create and add image message in view
	// final EMMessage message =
	// EMMessage.createSendMessage(EMMessage.Type.IMAGE);
	// // 如果是群聊，设置chattype,默认是单聊
	// if (chatType == CHATTYPE_GROUP){
	// message.setChatType(ChatType.GroupChat);
	// }else if(chatType == CHATTYPE_CHATROOM){
	// message.setChatType(ChatType.ChatRoom);
	// }

	// message.setReceipt(to);
	// ImageMessageBody body = new ImageMessageBody(new File(filePath));
	// // 默认超过100k的图片会压缩后发给对方，可以设置成发送原图
	// // body.setSendOriginalImage(true);
	// message.addBody(body);
	// conversation.addMessage(message);

	// listView.setAdapter(adapter);
	// adapter.refreshSelectLast();
	// setResult(RESULT_OK);
	// // more(more);
	// }

	/**
	 * 发送视频消息
	 */
	// private void sendVideo(final String filePath, final String thumbPath,
	// final int length) {
	// final File videoFile = new File(filePath);
	// if (!videoFile.exists()) {
	// return;
	// }
	// try {
	// EMMessage message = EMMessage.createSendMessage(EMMessage.Type.VIDEO);
	// // 如果是群聊，设置chattype,默认是单聊
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

	private void dealChat(JSONArray _args) {
		final JSONArray args = _args;
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				String chatType, target, _contentType;
				JSONObject content;
				Type contentType;
				try {
					chatType = args.getString(0);
					target = args.getString(1);
					_contentType = args.getString(2);
					content = args.getJSONObject(3);
					contentType = EMMessage.Type.valueOf(_contentType);
					// 获取到与聊天人的会话对象。参数username为聊天人的userid或者groupid，后文中的username皆是如此
					EMConversation conversation = EMChatManager.getInstance()
							.getConversation(target);
					EMMessage message = EMMessage
							.createSendMessage(contentType);
					// 如果是群聊，设置chattype,默认是单聊
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
							emchatCallbackContext.error("发送失败！");
							return;
						}

						File file = new File(path);

						ImageMessageBody imageBody = new ImageMessageBody(file);
						// 默认超过100k的图片会压缩后发给对方，可以设置成发送原图
						// body.setSendOriginalImage(true);
						message.addBody(imageBody);
						break;
					case LOCATION:
						LocationMessageBody locationBody = new LocationMessageBody(
								content.getString("locationAddress"), content
										.getDouble("latitude"), content
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
						// 设置消息body
						TextMessageBody textBody = new TextMessageBody(content
								.getString("text"));
						message.addBody(textBody);
						break;
					}
					// 设置接收人
					message.setReceipt(target);
					// 把消息加入到此会话对象中
					conversation.addMessage(message);
					// 发送消息
					EMChatManager.getInstance().sendMessage(message,
							new EMCallBack() {
								@Override
								public void onSuccess() {
									Log.d("main", "发送成功");
									emchatCallbackContext.success("发送成功");
								}

								@Override
								public void onProgress(int progress,
										String status) {

								}

								@Override
								public void onError(int code, String message) {
									Log.d("main", "发送失败！");
									emchatCallbackContext.error("发送失败！");
								}
							});
				} catch (JSONException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
					emchatCallbackContext.error("参数错误");
					return;
				} catch (IllegalArgumentException e) {
					emchatCallbackContext.error("CHAT类型错误");
				}
			}

		});

	}

	private void dealLogin(JSONArray args) {
		String user, psword;
		try {
			user = args.getString(0);
			psword = args.getString(1);
			EMChatManager.getInstance().login(user, psword, new EMCallBack() {// 回调
						@Override
						public void onSuccess() {
							cordova.getThreadPool().execute(new Runnable() {
								public void run() {
									EMGroupManager.getInstance()
											.loadAllGroups();
									EMChatManager.getInstance()
											.loadAllConversations();
									Log.d("main", "登陆聊天服务器成功！");
									emchatCallbackContext.success("登陆聊天服务器成功！");
									noifier = new HXNotifier();
									noifier.init(cordova.getActivity()
											.getApplicationContext());
									EMChatOptions chatOptions = EMChatManager
											.getInstance().getChatOptions();
									// chatOptions.setOnNotificationClickListener(getOnNotificationClickListener());
									// 覆盖消息提醒设置
									// noifier.setNotificationInfoProvider(getNotificationListener());
									// 接收所有的event事件
									EMChatManager.getInstance()
											.registerEventListener(
													new EMEventListener() {

														@Override
														public void onEvent(
																EMNotifierEvent event) {
															EMMessage message = null;
															Context appContext = cordova
																	.getActivity()
																	.getApplicationContext();
															if (event.getData() instanceof EMMessage) {
																message = (EMMessage) event
																		.getData();
																EMLog.d(TAG,
																		"receive the event : "
																				+ event.getEvent()
																				+ ",id : "
																				+ message
																						.getMsgId());
															}

															switch (event
																	.getEvent()) {
															case EventNewMessage:
																// 应用在后台，不需要刷新UI,通知栏提示新消息
																if (!EasyUtils
																		.isAppRunningForeground(appContext)) {
																	EMLog.d(TAG,
																			"app is running in backgroud");
																	noifier.onNewMsg(message);
																} else {
																	String msg = messageToJson(
																			message)
																			.toString();
																	fireEvent(
																			"ReciveMessage",
																			msg);
																	EMLog.d(TAG,
																			message.toString());
																}
																break;
															case EventOfflineMessage:
																if (!EasyUtils
																		.isAppRunningForeground(appContext)) {
																	EMLog.d(TAG,
																			"received offline messages");
																	List<EMMessage> messages = (List<EMMessage>) event
																			.getData();
																	noifier.onNewMesg(messages);
																} else {
																	String msg = messageToJson(
																			message)
																			.toString();
																	fireEvent(
																			"ReciveMessage",
																			msg);
																	EMLog.d(TAG,
																			message.toString());
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

							});
						}

						@Override
						public void onProgress(int progress, String status) {

						}

						@Override
						public void onError(int code, String message) {
							Log.d("main", "登陆聊天服务器失败！");
							emchatCallbackContext.error("登陆聊天服务器失败！");
						}
					});
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			emchatCallbackContext.error("发送错误！");
		}

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
						// 获取此会话的所有消息
						messages = conversation.getAllMessages();
					} else {
						final String startMsgId = _args.getString(2);
						if (chatType.equals("group")) {
							// 如果是群聊，调用下面此方法
							messages = conversation.loadMoreGroupMsgFromDB(
									startMsgId, pagesize);
						} else {
							// sdk初始化加载的聊天记录为20条，到顶时需要去db里获取更多
							// 获取startMsgId之前的pagesize条消息，此方法获取的messages
							// sdk会自动存入到此会话中，app中无需再次把获取到的messages添加到会话中
							messages = conversation.loadMoreMsgFromDB(
									startMsgId, pagesize);
						}
					}
					// 未读消息数清零
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
					// TODO Auto-generated catch block
					e.printStackTrace();
					emchatCallbackContext.error("获取聊天记录失败！");
				}
			}

		});

	}

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
				body.put("url", voiceBody.getLocalUrl())
						.put("name", voiceBody.getFileName())
						.put("size", voiceBody.getLength());
				break;
			case IMAGE:
				ImageMessageBody imageBody = (ImageMessageBody) message
						.getBody();
				body.put("url", imageBody.getThumbnailUrl());
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
				body.put("url", fileBody.getLocalUrl())
						.put("name", fileBody.getFileName())
						.put("size", fileBody.getFileSize());
				break;
			case TXT:
			default:
				TextMessageBody txtBody = (TextMessageBody) message.getBody();
				body.put("text", txtBody.getMessage());
				break;
			}
			msgJson.put("body", body);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return msgJson;

	}

	/**
	 * 自定义通知栏提示内容
	 * 
	 * @return
	 */
	protected HXNotificationInfoProvider getNotificationListener() {
		// 可以覆盖默认的设置
		return new HXNotificationInfoProvider() {

			@Override
			public String getTitle(EMMessage message) {
				// 修改标题,这里使用默认
				return null;
			}

			@Override
			public int getSmallIcon(EMMessage message) {
				// 设置小图标，这里为默认
				return 0;
			}

			@Override
			public String getDisplayedText(EMMessage message) {
				// 设置状态栏的消息提示，可以根据message的类型做相应提示
				// String ticker = CommonUtils.getMessageDigest(message,
				// appContext);
				// if(message.getType() == Type.TXT){
				// ticker = ticker.replaceAll("\\[.{2,3}\\]", "[表情]");
				// }

				// return message.getFrom() + ": " + ticker;
				return null;
			}

			@Override
			public String getLatestText(EMMessage message, int fromUsersNum,
					int messageNum) {
				return null;
				// return fromUsersNum + "个基友，发来了" + messageNum + "条消息";
			}

			@Override
			public Intent getLaunchIntent(EMMessage message) {
				// 设置点击通知栏跳转事件
				// String msg = messageToJson(message).toString();
				// fireEvent("ClickNotification", msg);
				return null;
			}
		};
	}

	private String getRealPathFromURI(String uriStr) {
		if (uriStr.indexOf("file:") == 0) {
			return uriStr.substring(7);
		}
		Uri contentUri = Uri.parse(uriStr);
		Cursor cursor = cordova.getActivity().getContentResolver()
				.query(contentUri, null, null, null, null);
		if (cursor != null) {
			cursor.moveToFirst();
			int columnIndex = cursor.getColumnIndex("_data");
			String picturePath = cursor.getString(columnIndex);
			String picturePath1 = cursor.getString(0);
			String picturePath2 = cursor.getString(1);
			String picturePath3 = cursor.getString(2);
			String picturePath4 = cursor.getString(3);
			String picturePath5 = cursor.getString(4);
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
