/**
 * The MIT License (MIT)
 * Copyright (c) 2012 David Carver
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
 * OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS
 * OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF
 * OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package us.nineworlds.serenity.ui.video.player;

import java.io.InputStream;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Collection;

import us.nineworlds.plex.rest.PlexappFactory;
import us.nineworlds.serenity.SerenityApplication;
import us.nineworlds.serenity.core.services.CompletedVideoRequest;
import us.nineworlds.serenity.core.subtitles.formats.Caption;
import us.nineworlds.serenity.core.subtitles.formats.FormatASS;
import us.nineworlds.serenity.core.subtitles.formats.FormatSRT;
import us.nineworlds.serenity.core.subtitles.formats.TimedTextObject;
import us.nineworlds.serenity.ui.activity.SerenityActivity;
import us.nineworlds.serenity.R;

import com.google.analytics.tracking.android.EasyTracker;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A view that handles the internal video playback and representation of a movie
 * or tv show.
 * 
 * @author dcarver
 * 
 */
public class SerenitySurfaceViewVideoActivity extends SerenityActivity
		implements SurfaceHolder.Callback {

	/**
	 * 
	 */
	static final int PROGRESS_UPDATE_DELAY = 5000;
	static final int SUBTITLE_DISPLAY_CHECK = 100;

	static final String TAG = "SerenitySurfaceViewVideoActivity";
	static final int CONTROLLER_DELAY = 16000; // Sixteen seconds
	private MediaPlayer mediaPlayer;
	private String videoURL;
	private SurfaceView surfaceView;
	private MediaController mediaController;
	private String aspectRatio;
	private String videoId;
	private int resumeOffset;
	private boolean mediaplayer_error_state = false;
	private boolean mediaplayer_released = false;
	private String subtitleURL;
	private String subtitleType;
	private String mediaTagIdentifier;
	private TimedTextObject subtitleTimedText;
	private boolean subtitlesPlaybackEnabled = true;

	private Handler subtitleDisplayHandler = new Handler();
	private Runnable subtitle = new Runnable() {
		@Override
		public void run() {
			if (isMediaPlayerStateValid() && mediaPlayer.isPlaying()) {
				if (hasSubtitles()) {
					int currentPos = mediaPlayer.getCurrentPosition();
					Collection<Caption> subtitles = subtitleTimedText.captions
							.values();
					for (Caption caption : subtitles) {
						if (currentPos >= caption.start.getMilliseconds()
								&& currentPos <= caption.end.getMilliseconds()) {
							onTimedText(caption);
							break;
						} else if (currentPos > caption.end.getMilliseconds()) {
							onTimedText(null);
						}
					}
				} else {
					subtitlesPlaybackEnabled = false;
					Toast.makeText(getApplicationContext(), "Invalid or Missing Subtitle. Subtitle playback disabled.", Toast.LENGTH_LONG).show();
				}
			}
			if (subtitlesPlaybackEnabled) {
				subtitleDisplayHandler.postDelayed(this, SUBTITLE_DISPLAY_CHECK);
			}

		}

		/**
		 * @return
		 */
		protected boolean hasSubtitles() {
			return subtitleTimedText != null
					&& subtitleTimedText.captions != null;
		};
	};

	private Handler progressReportinghandler = new Handler();
	private Runnable progressRunnable = new Runnable() {

		@Override
		public void run() {
			try {
				if (isMediaPlayerStateValid() && mediaPlayer.isPlaying()) {
					new UpdateProgressRequest().execute();
					progressReportinghandler.postDelayed(this,
							PROGRESS_UPDATE_DELAY); // Update progress every 5
													// seconds
				}
			} catch (IllegalStateException ex) {
				Log.w(getClass().getName(),
						"Illegalstate exception occurred durring progress update. No further updates will occur.",
						ex);
			}
		};
	};

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {

	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		try {
			mediaPlayer.setDisplay(holder);
			mediaPlayer.setDataSource(videoURL);
			mediaPlayer.setOnPreparedListener(new VideoPlayerPrepareListener(
					this, mediaPlayer, mediaController, surfaceView,
					resumeOffset, videoId, aspectRatio,
					progressReportinghandler, progressRunnable, subtitleURL));
			mediaPlayer
					.setOnCompletionListener(new VideoPlayerOnCompletionListener());
			mediaPlayer.prepareAsync();

		} catch (Exception ex) {
			Log.e(TAG, "Video Playback Error. ", ex);
		}

	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (!mediaplayer_released) {
			mediaPlayer.release();
			mediaplayer_released = true;
		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.video_playback);
		init();
	}

	/**
	 * Initialize the mediaplayer and mediacontroller.
	 */
	protected void init() {
		mediaPlayer = new MediaPlayer();
		mediaPlayer.setOnErrorListener(new SerenityOnErrorListener());
		surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
		surfaceView.setKeepScreenOn(true);
		SurfaceHolder holder = surfaceView.getHolder();
		holder.addCallback(this);
		holder.setSizeFromLayout();

		retrieveIntentExtras();
	}

	protected void retrieveIntentExtras() {
		Bundle extras = getIntent().getExtras();

		videoURL = extras.getString("videoUrl");
		if (videoURL == null) {
			videoURL = extras.getString("encodedvideoUrl");
			if (videoURL != null) {
				videoURL = URLDecoder.decode(videoURL);
			}
		}
		videoId = extras.getString("id");
		String summary = extras.getString("summary");
		String title = extras.getString("title");
		String posterURL = extras.getString("posterUrl");
		aspectRatio = extras.getString("aspectRatio");
		String videoFormat = extras.getString("videoFormat");
		String videoResolution = extras.getString("videoResolution");
		String audioFormat = extras.getString("audioFormat");
		String audioChannels = extras.getString("audioChannels");
		resumeOffset = extras.getInt("resumeOffset");
		subtitleURL = extras.getString("subtitleURL");
		subtitleType = extras.getString("subtitleFormat");
		mediaTagIdentifier = extras.getString("mediaTagId");

		new SubtitleAsyncTask().execute();

		initMediaController(summary, title, posterURL, videoFormat,
				videoResolution, audioFormat, audioChannels);

	}

	/**
	 * @param summary
	 * @param title
	 * @param posterURL
	 * @param videoFormat
	 * @param videoResolution
	 * @param audioFormat
	 * @param audioChannels
	 */
	protected void initMediaController(String summary, String title,
			String posterURL, String videoFormat, String videoResolution,
			String audioFormat, String audioChannels) {

		mediaController = new MediaController(this, summary, title, posterURL,
				videoResolution, videoFormat, audioFormat, audioChannels,
				mediaTagIdentifier);
		mediaController.setAnchorView(surfaceView);
		mediaController.setMediaPlayer(new SerenityMediaPlayerControl(
				mediaPlayer));
	}

	@Override
	public void finish() {
		super.finish();
		subtitleDisplayHandler.removeCallbacks(subtitle);
		progressReportinghandler.removeCallbacks(progressRunnable);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (mediaController.isShowing()) {
			if (isKeyCodeBack(keyCode)) {
				mediaController.hide();

				if (isMediaPlayerStateValid() && mediaPlayer.isPlaying()) {
					mediaPlayer.stop();
				}
				return super.onKeyDown(keyCode, event);
			}
		} else {
			if (isKeyCodeBack(keyCode)) {
				if (isMediaPlayerStateValid() && mediaPlayer.isPlaying()) {
					mediaPlayer.stop();
				}
				return super.onKeyDown(keyCode, event);
			}
		}

		if (isKeyCodeInfo(keyCode)) {
			if (isMediaPlayerStateValid()) {
				if (mediaController.isShowing()) {
					mediaController.hide();
				} else {
					mediaController.show(CONTROLLER_DELAY);
				}
			}
			return true;
		}

		if (isKeyCodePauseResume(keyCode)) {
			if (isMediaPlayerStateValid() && mediaPlayer.isPlaying()) {
				mediaPlayer.pause();
				mediaController.show(CONTROLLER_DELAY);
				progressReportinghandler.removeCallbacks(progressRunnable);
			} else {
				mediaPlayer.start();
				mediaController.hide();
				progressReportinghandler.postDelayed(progressRunnable, 5000);
			}
			return true;
		}

		if (isKeyCodeSkipForward(keyCode) && isMediaPlayerStateValid()) {
			int skipOffset = 10000 + mediaPlayer.getCurrentPosition();
			int duration = mediaPlayer.getDuration();
			if (skipOffset > duration) {
				skipOffset = duration - 1;
			}
			if (!mediaController.isShowing()) {
				mediaController.show(CONTROLLER_DELAY);
			}
			mediaPlayer.seekTo(skipOffset);
			return true;
		}

		if (isKeyCodeSkipBack(keyCode) && isMediaPlayerStateValid()) {
			int skipOffset = mediaPlayer.getCurrentPosition() - 10000;
			if (skipOffset < 0) {
				skipOffset = 0;
			}
			if (!mediaController.isShowing()) {
				mediaController.show(CONTROLLER_DELAY);
			}
			mediaPlayer.seekTo(skipOffset);
			return true;
		}

		if (isKeyCodeStop(keyCode) && isMediaPlayerStateValid()) {
			if (mediaPlayer.isPlaying()) {
				mediaPlayer.pause();
				if (!mediaController.isShowing()) {
					mediaController.show(CONTROLLER_DELAY);
				}
			}
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	/**
	 * @param keyCode
	 * @return
	 */
	protected boolean isKeyCodeStop(int keyCode) {
		return keyCode == KeyEvent.KEYCODE_MEDIA_STOP
				|| keyCode == KeyEvent.KEYCODE_S;
	}

	/**
	 * @param keyCode
	 * @return
	 */
	@Override
	protected boolean isKeyCodeSkipBack(int keyCode) {
		return keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
				|| keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
				|| keyCode == KeyEvent.KEYCODE_R;
	}

	/**
	 * @param keyCode
	 * @return
	 */
	@Override
	protected boolean isKeyCodeSkipForward(int keyCode) {
		return keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
				|| keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
				|| keyCode == KeyEvent.KEYCODE_F;
	}

	/**
	 * @param keyCode
	 * @return
	 */
	protected boolean isKeyCodePauseResume(int keyCode) {
		return keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
				|| keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
				|| keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
				|| keyCode == KeyEvent.KEYCODE_P
				|| keyCode == KeyEvent.KEYCODE_SPACE;
	}

	protected boolean isKeyCodeInfo(int keyCode) {
		return keyCode == KeyEvent.KEYCODE_INFO
				|| keyCode == KeyEvent.KEYCODE_I;
	}

	protected boolean isKeyCodeBack(int keyCode) {
		return keyCode == KeyEvent.KEYCODE_BACK
				|| keyCode == KeyEvent.KEYCODE_ESCAPE;
	}

	protected boolean isMediaPlayerStateValid() {
		if (mediaPlayer != null && mediaplayer_error_state == false
				&& mediaplayer_released == false) {
			return true;
		}
		return false;
	}

	@Override
	protected void onStart() {
		super.onStart();
		EasyTracker.getInstance().activityStart(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		EasyTracker.getInstance().activityStop(this);
	}

	/**
	 * A task that updates the progress position of a video while it is being
	 * played.
	 * 
	 * @author dcarver
	 * 
	 */
	protected class UpdateProgressRequest extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			PlexappFactory factory = SerenityApplication.getPlexFactory();
			if (isMediaPlayerStateValid() && mediaPlayer.isPlaying()) {
				String offset = Integer.valueOf(
						mediaPlayer.getCurrentPosition()).toString();
				factory.setProgress(videoId, offset);
			}
			return null;
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (event.getAction() == MotionEvent.ACTION_DOWN) {
			if (mediaController.isShowing()) {
				mediaController.hide();
			} else {
				mediaController.show();
			}
			return true;
		}
		return super.onTouchEvent(event);
	}

	protected class VideoPlayerOnCompletionListener implements
			OnCompletionListener {

		@Override
		public void onCompletion(MediaPlayer mp) {
			new CompletedVideoRequest(videoId).execute();
			if (!mediaplayer_released) {
				if (isMediaPlayerStateValid()) {
					if (mediaController.isShowing()) {
						mediaController.hide();
					}
				}
				mp.release();
				mediaplayer_released = true;
			}
			finish();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * android.media.MediaPlayer.OnTimedTextListener#onTimedText(android.media
	 * .MediaPlayer, android.media.TimedText)
	 */
	public void onTimedText(Caption text) {
		TextView subtitles = (TextView) findViewById(R.id.txtSubtitles);
		if (text == null) {
			subtitles.setVisibility(View.INVISIBLE);
			return;
		}
		subtitles.setText(Html.fromHtml(text.content));
		subtitles.setVisibility(View.VISIBLE);
	}

	public class SubtitleAsyncTask extends AsyncTask<Void, Void, Void> {

		@Override
		protected Void doInBackground(Void... params) {
			if (subtitleURL != null) {
				try {
					URL url = new URL(subtitleURL);
					InputStream stream = url.openStream();

					if ("srt".equals(subtitleType)) {
						FormatSRT formatSRT = new FormatSRT();
						subtitleTimedText = formatSRT.parseFile(stream);
					} else if ("ass".equals(subtitleType)) {
						FormatASS formatASS = new FormatASS();
						subtitleTimedText = formatASS.parseFile(stream);
					}
					subtitleDisplayHandler.post(subtitle);
				} catch (Exception e) {
					Log.e(getClass().getName(), e.getMessage(), e);
				}
			}
			return null;
		}

	}
}
