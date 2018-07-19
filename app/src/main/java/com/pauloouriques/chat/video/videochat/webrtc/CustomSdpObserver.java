package com.pauloouriques.chat.video.videochat.webrtc;

import android.util.Log;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

/**
 * This class implements the SdpObserver to create the
 * peerConnection events methods.
 */
public class CustomSdpObserver implements SdpObserver {

  private String tag;

  /**
   * Creates a new CustomSdpObserver and set the log tag with this class
   * name and the String received.
   *
   * @param logTag the String to add to the tag log
   */
  public CustomSdpObserver(String logTag) {
    this.tag = this.getClass().getCanonicalName();
    this.tag = this.tag + " " + logTag;
  }

  @Override
  public void onCreateSuccess(SessionDescription sessionDescription) {
  }

  @Override
  public void onSetSuccess() {
  }

  @Override
  public void onCreateFailure(String s) {
    Log.d(tag, "onCreateFailure() called with: s = [" + s + "]");
  }

  @Override
  public void onSetFailure(String s) {
    Log.d(tag, "onSetFailure() called with: s = [" + s + "]");
  }

}
