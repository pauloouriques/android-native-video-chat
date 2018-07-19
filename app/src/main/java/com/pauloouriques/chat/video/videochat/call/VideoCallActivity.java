package com.pauloouriques.chat.video.videochat.call;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.pauloouriques.chat.video.videochat.R;
import com.pauloouriques.chat.video.videochat.webrtc.CustomPeerConnectionObserver;
import com.pauloouriques.chat.video.videochat.webrtc.CustomSdpObserver;
import com.pauloouriques.chat.video.videochat.webrtc.SignalingInterface;
import com.pauloouriques.chat.video.videochat.webrtc.SignallingClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.List;

public class VideoCallActivity extends AppCompatActivity implements View.OnClickListener, SignalingInterface {

    private static final String mCurrentCallRoom = "<SOME_ROOM_NAME>";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 5152;
    private static final String TAG = "VideoCallActivity";

    private PeerConnectionFactory peerConnectionFactory;
    private VideoSource videoSource;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private SurfaceViewRenderer localVideoView;
    private SurfaceViewRenderer remoteVideoView;
    private VideoRenderer remoteRenderer;
    private PeerConnection localPeer;
    private EglBase rootEglBase;
    private SignallingClient mSignallingClient;
    private boolean gotUserMedia;
    private List<PeerConnection.IceServer> peerIceServers = new ArrayList<>();
    private MediaConstraints sdpMediaConstraints;
    private boolean isSpeakerOn;
    private FloatingActionButton mSpeakerButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_call);

        requestCameraPermission();

        mSpeakerButton = (FloatingActionButton) findViewById(R.id.speaker_change);

        AudioManager audioManager = ((AudioManager) getSystemService(AUDIO_SERVICE));
        isSpeakerOn = audioManager.isSpeakerphoneOn();

        if (isSpeakerOn) {
            mSpeakerButton.setImageResource(R.drawable.ic_speaker_phone);
        } else {
            mSpeakerButton.setImageResource(R.drawable.ic_speaker);
            setAudioOutput();
        }
        mSpeakerButton.setOnClickListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSignallingClient.close();
        rootEglBase.releaseSurface();
    }

    private void initViews() {
        FloatingActionButton hangup = (FloatingActionButton) findViewById(R.id.end_call_button);
        localVideoView = (SurfaceViewRenderer) findViewById(R.id.local_gl_surface_view);
        remoteVideoView = (SurfaceViewRenderer) findViewById(R.id.remote_gl_surface_view);
        hangup.setOnClickListener(this);
    }

    private void initVideos() {
        rootEglBase = EglBase.create();
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        remoteVideoView.init(rootEglBase.getEglBaseContext(), null);
    }

    private void getIceServers() {
        PeerConnection.IceServer peerIceServer = PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer();
        peerIceServers.add(peerIceServer);
    }


    public void start() {
        //Initialize PeerConnectionFactory globals.
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableVideoHwAcceleration(true)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        //Create a new PeerConnectionFactory instance - using Hardware encoder and decoder.
        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        DefaultVideoEncoderFactory defaultVideoEncoderFactory = new DefaultVideoEncoderFactory(
                rootEglBase.getEglBaseContext(),  /* enableIntelVp8Encoder */true,  /* enableH264HighProfile */true);
        DefaultVideoDecoderFactory defaultVideoDecoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        peerConnectionFactory = new PeerConnectionFactory(options, defaultVideoEncoderFactory, defaultVideoDecoderFactory);


        //Now create a VideoCapturer instance.
        VideoCapturer videoCapturerAndroid;
        videoCapturerAndroid = createCameraCapturer(new Camera1Enumerator(false));


        //Create MediaConstraints - Will be useful for specifying video and audio constraints.
        MediaConstraints audioConstraints = new MediaConstraints();
        MediaConstraints videoConstraints = new MediaConstraints();

        //Create a VideoSource instance
        if (videoCapturerAndroid != null) {
            videoSource = peerConnectionFactory.createVideoSource(videoCapturerAndroid);
        }
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        //create an AudioSource instance
        AudioSource audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);
        localAudioTrack.setVolume(10000000000000.0);

        if (videoCapturerAndroid != null) {
            videoCapturerAndroid.startCapture(1024, 720, 30);
        }
        localVideoView.setVisibility(View.VISIBLE);
        //create a videoRenderer based on SurfaceViewRenderer instance
        VideoRenderer localRenderer = new VideoRenderer(localVideoView);
        // And finally, with our VideoRenderer ready, we
        // can add our renderer to the VideoTrack.
        localVideoTrack.addRenderer(localRenderer);

        localVideoView.setMirror(true);
        remoteVideoView.setMirror(true);

        sdpMediaConstraints = new MediaConstraints();
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        sdpMediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));

        gotUserMedia = true;
        if (mSignallingClient.isInitiator) {
            onTryToStart();
        }
    }


    private void setAudioOutput() {
        AudioManager audioManager = ((AudioManager) getSystemService(AUDIO_SERVICE));
        if (isSpeakerOn) {
            isSpeakerOn = false;
            audioManager.setMode(AudioManager.MODE_NORMAL);
            mSpeakerButton.setImageResource(R.drawable.ic_speaker);
        } else {
            isSpeakerOn = true;
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            mSpeakerButton.setImageResource(R.drawable.ic_speaker_phone);
        }
        audioManager.setSpeakerphoneOn(isSpeakerOn);
    }

    /**
     * This method will be called directly by the app when it is the initiator and has got the local media
     * or when the remote peer sends a message through socket that it is ready to transmit AV data
     */
    @Override
    public void onTryToStart() {
        runOnUiThread(() -> {
            if (!mSignallingClient.isStarted && localVideoTrack != null && mSignallingClient.isChannelReady) {
                createPeerConnection();
                mSignallingClient.isStarted = true;
                if (mSignallingClient.isInitiator) {
                    doCall();
                }
            }
        });
    }


    /**
     * Creating the local peerconnection instance
     */
    private void createPeerConnection() {
        PeerConnection.RTCConfiguration rtcConfig =
                new PeerConnection.RTCConfiguration(peerIceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        localPeer = peerConnectionFactory.createPeerConnection(rtcConfig, new CustomPeerConnectionObserver() {
            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                super.onIceCandidate(iceCandidate);
                onIceCandidateReceived(iceCandidate);
            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                Log.d("onAddStream", "Received Remote stream");
                super.onAddStream(mediaStream);
                gotRemoteStream(mediaStream);
            }
        });

        addStreamToLocalPeer();
    }

    /**
     * Adding the stream to the localpeer
     */
    private void addStreamToLocalPeer() {
        //creating local mediastream
        MediaStream stream = peerConnectionFactory.createLocalMediaStream("102");
        stream.addTrack(localAudioTrack);
        stream.addTrack(localVideoTrack);
        localPeer.addStream(stream);
    }

    /**
     * This method is called when the app is initiator - We generate the offer and send it over through socket
     * to remote peer
     */
    private void doCall() {
        localPeer.createOffer(new CustomSdpObserver("localCreateOffer") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocalDesc"), sessionDescription);
                Log.d("onCreateSuccess", "SignallingClient emit ");
                mSignallingClient.emitMessage(sessionDescription);
            }
        }, sdpMediaConstraints);
    }

    /**
     * Received remote peer's media stream. we will get the first video track and render it
     */
    private void gotRemoteStream(MediaStream stream) {
        //we have remote video stream. add to the renderer.
        final VideoTrack videoTrack = stream.videoTracks.get(0);
        runOnUiThread(() -> {
            try {
                remoteRenderer = new VideoRenderer(remoteVideoView);
                remoteVideoView.setVisibility(View.VISIBLE);
                videoTrack.addRenderer(remoteRenderer);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        final AudioTrack audioTrack = stream.audioTracks.get(0);

    }


    /**
     * Received local ice candidate. Send it to remote peer through signalling for negotiation
     */
    public void onIceCandidateReceived(IceCandidate iceCandidate) {
        //we have received ice candidate. We can set it to the other peer.
        mSignallingClient.emitIceCandidate(iceCandidate);
    }

    /**
     * SignallingCallback - called when the room is created - i.e. you are the initiator
     */
    @Override
    public void onCreatedRoom() {
        Log.d("onCreatedRoom", "You created the room " + mSignallingClient.getmRoomName());
        if (gotUserMedia) {
            mSignallingClient.emitMessage("got user media");
        }
    }

    /**
     * SignallingCallback - called when you join the room - you are a participant
     */
    @Override
    public void onJoinedRoom() {
        Log.d("onJoinedRoom", "You joined the room " + mSignallingClient.getmRoomName());
        if (gotUserMedia) {
            mSignallingClient.emitMessage("got user media");
        }
    }

    @Override
    public void onNewPeerJoined() {
        Log.d("onNewPeerJoined", "Remote Peer Joined");
        onTryToStart();
    }

    @Override
    public void onRemoteHangUp(String msg) {
        Log.d("onRemoteHangUp", "Remote Peer hanged up");
        runOnUiThread(() -> hangup());
    }

    /**
     * SignallingCallback - Called when remote peer sends offer
     */
    @Override
    public void onOfferReceived(final JSONObject data) {
        Log.d("onOfferReceived", "Received Offer");
        runOnUiThread(() -> {
            if (!mSignallingClient.isInitiator && !mSignallingClient.isStarted) {
                onTryToStart();
            }

            try {
                localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.OFFER, data.getString("sdp")));
                doAnswer();
                updateVideoViews(true);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
    }

    private void doAnswer() {
        localPeer.createAnswer(new CustomSdpObserver("localCreateAns") {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                super.onCreateSuccess(sessionDescription);
                localPeer.setLocalDescription(new CustomSdpObserver("localSetLocal"), sessionDescription);
                mSignallingClient.emitMessage(sessionDescription);
            }
        }, new MediaConstraints());
    }

    /**
     * SignallingCallback - Called when remote peer sends answer to your offer
     */

    @Override
    public void onAnswerReceived(JSONObject data) {
        Log.d("onAnswerReceived", "Received Answer");
        try {
            localPeer.setRemoteDescription(new CustomSdpObserver("localSetRemote"), new SessionDescription(SessionDescription.Type.fromCanonicalForm(data.getString("type").toLowerCase()), data.getString("sdp")));
            updateVideoViews(true);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Remote IceCandidate received
     */
    @Override
    public void onIceCandidateReceived(JSONObject data) {
        Log.d("onIceCandidateReceived", "Received IceCandidateReceived");
        try {
            localPeer.addIceCandidate(new IceCandidate(data.getString("id"), data.getInt("label"), data.getString("candidate")));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    private void updateVideoViews(final boolean remoteVisible) {
        runOnUiThread(() -> {
            ViewGroup.LayoutParams params = localVideoView.getLayoutParams();
            if (remoteVisible) {
                params.height = dpToPx(100);
                params.width = dpToPx(100);
            } else {
                params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            localVideoView.setLayoutParams(params);
        });

    }


    /**
     * Closing up - normal hangup and app destroye
     */

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.end_call_button:
                hangup();
                break;
            case R.id.speaker_change:
                setAudioOutput();
                break;

        }
    }

    private void hangup() {
        try {
            localPeer.close();
            localPeer = null;
            mSignallingClient.close();
            localVideoView.release();
            remoteVideoView.release();
            updateVideoViews(false);
            android.os.Process.killProcess(android.os.Process.myPid());
            finish();
        } catch (Exception e) {
            android.os.Process.killProcess(android.os.Process.myPid());
            finish();
            e.printStackTrace();
        }

    }


    /**
     * Util Methods
     */
    public int dpToPx(int dp) {
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        return Math.round(dp * (displayMetrics.xdpi / DisplayMetrics.DENSITY_DEFAULT));
    }


    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }


    private void initStuff() {
        initViews();
        initVideos();
        getIceServers();
        mSignallingClient = new SignallingClient();
        mSignallingClient.init(this, mCurrentCallRoom);
        start();
    }


    private void requestCameraPermission() {
        int hasCameraPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int hasAudioPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (hasCameraPermission != PackageManager.PERMISSION_GRANTED || hasAudioPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            initStuff();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initStuff();
            } else {
                Toast.makeText(this, R.string.camera_permission_fail, Toast.LENGTH_SHORT).show();
                android.os.Process.killProcess(android.os.Process.myPid());
                finish();
            }
        }
    }
}
