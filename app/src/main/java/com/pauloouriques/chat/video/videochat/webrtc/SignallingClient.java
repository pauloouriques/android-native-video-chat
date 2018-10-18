package com.pauloouriques.chat.video.videochat.webrtc;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import io.socket.client.IO;
import io.socket.client.Socket;

/**
 * SignallingClient
 * Created by vivek-3102 on 11/03/17 and changed by pauloouriques on 07/19/18.
 */

public class SignallingClient {
    private final static String API_URL = "<signaling-server-url>";
    private static SignallingClient instance;
    private String mRoomName = null;
    private Socket socket;
    public boolean isChannelReady = false;
    public boolean isInitiator = false;
    public boolean isStarted = false;
    private SignalingInterface callback;

    private final TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new java.security.cert.X509Certificate[]{};
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }
    }};


    public void init(SignalingInterface signalingInterface, String roomName) {
        this.callback = signalingInterface;
        mRoomName = roomName;
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLS");
            sslcontext.init(null, trustAllCerts, null);
            IO.setDefaultHostnameVerifier((hostname, session) -> true);
            IO.setDefaultSSLContext(sslcontext);
            socket = IO.socket(API_URL);
            socket.connect();
            if (!mRoomName.isEmpty()) {
                emitInitStatement(mRoomName);
            }
            socket.on("created", args -> {
                isInitiator = true;
                callback.onCreatedRoom();
            });
            socket.on("full", args -> Log.d("SignallingClient", "full call() called with: args = [" + Arrays.toString(args) + "]"));
            socket.on("join", args -> {
                isChannelReady = true;
                callback.onNewPeerJoined();
            });
            socket.on("joined", args -> {
                isChannelReady = true;
                callback.onJoinedRoom();
            });
            socket.on("log", args -> Log.d("SignallingClient", "log call() called with: args = [" + Arrays.toString(args) + "]"));
            socket.on("bye", args -> callback.onRemoteHangUp((String) args[0]));
            socket.on("message", args -> {
                if (args[0] instanceof String) {
                    Log.d("SignallingClient", "String received :: " + args[0]);
                    String data = (String) args[0];
                    if (data.equalsIgnoreCase("got user media")) {
                        callback.onTryToStart();
                    }
                    if (data.equalsIgnoreCase("bye")) {
                        callback.onRemoteHangUp(data);
                    }
                } else if (args[0] instanceof JSONObject) {
                    try {
                        JSONObject data = (JSONObject) args[0];
                        String type = data.getString("type");
                        if (type.equalsIgnoreCase("offer")) {
                            callback.onOfferReceived(data);
                        } else if (type.equalsIgnoreCase("answer") && isStarted) {
                            callback.onAnswerReceived(data);
                        } else if (type.equalsIgnoreCase("candidate") && isStarted) {
                            callback.onIceCandidateReceived(data);
                        } else if (type.equalsIgnoreCase("got user media")) {
                            callback.onTryToStart();
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            });
        } catch (URISyntaxException | NoSuchAlgorithmException | KeyManagementException e) {
            e.printStackTrace();
        }
    }

    private void emitInitStatement(String message) {
        socket.emit("create or join", message);
    }

    public void emitMessage(String message) {
        try {
            Log.d("SignallingClient", "emitMessage() called with: message = [" + message + "]");
            JSONObject obj = new JSONObject();
            obj.put("type", message);
            obj.put("room", getmRoomName());
            Log.d("emitMessage", obj.toString());
            socket.emit("message", obj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void emitMessage(SessionDescription message) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", message.type.canonicalForm());
            obj.put("sdp", message.description);
            obj.put("room", getmRoomName());
            socket.emit("message", obj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void emitIceCandidate(IceCandidate iceCandidate) {
        try {
            JSONObject object = new JSONObject();
            object.put("type", "candidate");
            object.put("label", iceCandidate.sdpMLineIndex);
            object.put("id", iceCandidate.sdpMid);
            object.put("candidate", iceCandidate.sdp);
            object.put("room", getmRoomName());
            socket.emit("message", object);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void close() {
        socket.emit("bye", mRoomName);
        socket.disconnect();
        socket.close();
    }

    public String getmRoomName() {
        return this.mRoomName;
    }

}
