package com.pauloouriques.chat.video.videochat.webrtc;

import org.json.JSONObject;

/**
 * This interface delegate the methods to be created
 * in the SignalingClient Classes.
 */
public interface SignalingInterface {
  void onRemoteHangUp(String msg);

  void onOfferReceived(JSONObject data);

  void onAnswerReceived(JSONObject data);

  void onIceCandidateReceived(JSONObject data);

  void onTryToStart();

  void onCreatedRoom();

  void onJoinedRoom();

  void onNewPeerJoined();
}