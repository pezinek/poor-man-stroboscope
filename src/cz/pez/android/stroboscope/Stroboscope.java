package cz.pez.android.stroboscope;

import java.util.List;

import android.app.Activity;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.ToggleButton;
import android.widget.ZoomControls;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;

public class Stroboscope extends Activity implements OnEditorActionListener {
	private EditText frequency_edit;
	private EditText intensity_edit;
	private ZoomControls frequency_controls;
	private ZoomControls intensity_controls;
	private static CountDownTimer off_timer=null;
	private static CountDownTimer timer=null;
	private static CountDownTimer stats_timer=null;
	private Camera mCamera=null;
	private Parameters mCameraParams=null;
	private TextView info_text;
	private View flasher_handle;
	private View flasher;
	private TextView error_text;
	private ToggleButton onoff;
	private long blink_period;
	private long on_period;
	private long light_on_last;
	private long light_off_last;
	private long light_on_error = 0;
	private long light_off_error = 0;
	
	private static final long zoom_speed=10;
	private static final float max_frequency=100;
	private static final float min_frequency=0;
	private static final float frequency_increment=(float)1/(float)60;
	private static final float max_intensity=100;
	private static final float min_intenstiy=0;
	private static final float intensity_increment=1;
	private static final int COLOR_WHITE = 0xFFFFFFFF;
	private static final int COLOR_BLACK = 0xFF000000;
	private static final String TAG = Stroboscope.class.getSimpleName();
	private static final long stats_refresh_period = 1000; //ms
	
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        info_text = (TextView) findViewById(R.id.InfoText);
        error_text = (TextView) findViewById(R.id.ErrorText);
        frequency_edit = (EditText) findViewById(R.id.frequencyEdit);
        intensity_edit = (EditText) findViewById(R.id.intensityEdit);
        
        flasher_handle = (View) findViewById(R.id.flasher_handle);
        flasher = (View) findViewById(R.id.flasher);
        
        onoff = (ToggleButton) findViewById(R.id.onoff);
        
        frequency_edit.setOnEditorActionListener(this);
        intensity_edit.setOnEditorActionListener(this);
        
        frequency_controls=(ZoomControls) findViewById(R.id.frequencyControls);
        frequency_controls.setOnZoomInClickListener(new OnClickListener() {			
			@Override
			public void onClick(View v) { inc_freq(); }
		});
        frequency_controls.setOnZoomOutClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) { dec_freq(); }
		});
        
        frequency_controls.setZoomSpeed(zoom_speed);
        
        intensity_controls=(ZoomControls) findViewById(R.id.intensityControls);
        intensity_controls.setOnZoomInClickListener(new OnClickListener() {			
			@Override
			public void onClick(View v) { inc_intensity(); }
		});
        
        intensity_controls.setOnZoomOutClickListener(new OnClickListener() {			
			@Override
			public void onClick(View v) { dec_intensity(); }
		});
        
        intensity_controls.setZoomSpeed(zoom_speed);
        
        onoff.setOnCheckedChangeListener(new OnCheckedChangeListener() {			
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if (isChecked) {
					recalculate();
				} else {
					stop_timers();
				}
				
			}
		});
        onoff.setChecked(true);        
    }
    
	@Override
	public void onPause() {
		cleanup();
		super.onPause();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		setupCamera();
		if (onoff.isChecked()) {
			recalculate();
		}
	}	
    
    private void setupCamera() {
        Camera cam;
        try {
        	cam = Camera.open();
        } catch (RuntimeException e) {
        	error_text.setText(R.string.err_no_camera);
        	return;
        }
        
        Parameters params;
        params = cam.getParameters();
    	
        List<String> flashModes = params.getSupportedFlashModes();
        if (flashModes == null) {
        	error_text.setText(R.string.err_no_flash);
        	return;
        }
        
        if (! flashModes.contains(Parameters.FLASH_MODE_TORCH)) {
        	error_text.setText(R.string.err_no_torch_mode);
        	return;
        }
        
        Log.i(TAG, "Flash Modes: " + flashModes);

        mCamera=cam;
        mCameraParams=params;
    }

	@Override
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {			
		recalculate();
		return false;
	}
	
	private void stop_timers() {
		if (timer != null) {
			timer.cancel();
		}
		
		if (off_timer != null) {
			off_timer.cancel();
		}		
		lightOff();
		
		if (stats_timer != null) {
			stats_timer.cancel();
		}
	}
	
	private void cleanup() {
		if (mCamera != null) {
			mCamera.release();
			mCamera=null;
			mCameraParams=null;
		}
		
		stop_timers();
	}
	
	private float get_freq() {
		return Float.parseFloat(this.frequency_edit.getText().toString());
	}
	
	private void set_freq(float freq) {
		this.frequency_edit.setText(String.valueOf(freq));
	}
	
	private void inc_freq() {
		set_freq(get_freq()+frequency_increment);
		recalculate();		
	}
	
	private void dec_freq() {
		set_freq(get_freq()-frequency_increment);
		recalculate();
	}
	
	private float get_intensity() {
		return Float.parseFloat(this.intensity_edit.getText().toString());
	}
	
	private void set_intensity(float intensity) {
		intensity_edit.setText(String.valueOf(intensity));
	}

    private void dec_intensity() {
		set_intensity(get_intensity()-intensity_increment);
		recalculate();
	}

	private void inc_intensity() {
		set_intensity(get_intensity()+intensity_increment);
		recalculate();
	}
	
	private void lightOn() {
	    long tick = System.nanoTime();
		
		if (mCameraParams != null) {		
			if (!mCameraParams.getFlashMode().equals(Parameters.FLASH_MODE_TORCH)) {
				mCameraParams.setFlashMode(Parameters.FLASH_MODE_TORCH);
				mCamera.setParameters(mCameraParams);
			}
		}
		
		flasher.setBackgroundColor(COLOR_WHITE);
		flasher_handle.setBackgroundColor(COLOR_WHITE);

		long err = Math.abs((tick - light_on_last) - blink_period*1000000);
		light_on_last=tick;
		if (err > light_on_error) { light_on_error = err; }
	}
	
	private void lightOff() {
		long tick = System.nanoTime();
		
		if (mCameraParams != null) {
			if (!mCameraParams.getFlashMode().equals(Parameters.FLASH_MODE_OFF)) {
				mCameraParams.setFlashMode(Parameters.FLASH_MODE_OFF);
				mCamera.setParameters(mCameraParams);
			}
		}
		
		flasher.setBackgroundColor(COLOR_BLACK);
		flasher_handle.setBackgroundColor(COLOR_BLACK);
		
		long err = Math.abs(tick - light_off_last - (blink_period*1000000));
		light_off_last=tick;
		if (err > light_off_error) { light_off_error = err; }
	}
	
	private void recalculate() {
		// Toast.makeText(this, "Recalculating", Toast.LENGTH_SHORT).show();
		
		float freq=get_freq();
		if (freq <= min_frequency) {
			set_freq(min_frequency);
			frequency_controls.setIsZoomOutEnabled(false);
		} else {
			frequency_controls.setIsZoomOutEnabled(true);
		}
		
		if (freq >= max_frequency) {
			set_freq(max_frequency);
			frequency_controls.setIsZoomInEnabled(false);
		} else {
			frequency_controls.setIsZoomInEnabled(true);
		}
		
		float intensity=get_intensity();
		if (intensity <= min_intenstiy) {
			set_intensity(min_intenstiy);
			intensity_controls.setIsZoomOutEnabled(false);
		} else {
			intensity_controls.setIsZoomOutEnabled(true);
		}
		
		if (intensity >= max_intensity) {
			set_intensity(max_intensity);
			intensity_controls.setIsZoomInEnabled(false);
		} else {
			intensity_controls.setIsZoomInEnabled(true);
		}
		
		if (timer != null) { timer.cancel(); }
		if (off_timer != null) { off_timer.cancel(); }
		
		if (freq <= 0) { return; }
		if (intensity <= 0) { return; }		
		
		blink_period = (long) (1000 / freq);
		on_period = (long) ((1000 / freq) * intensity / 100);
		light_on_last = System.nanoTime();
		light_off_last = System.nanoTime();
					
		stop_timers();
		off_timer=new CountDownTimer(on_period, on_period) {			
			@Override
			public void onTick(long millisUntilFinished) {}
			
			@Override
			public void onFinish() {
				lightOff();				
			}
		};
		
		timer=new CountDownTimer(blink_period, blink_period) {			
			@Override
			public void onTick(long millisUntilFinished) {}
			
			@Override
			public void onFinish() {
				lightOn();
				off_timer.start();
				timer.start();
			}
		};
		timer.start();
		
        //TODO: Once debuged replace stats_refresh_period*60 with Long.MAX_VALUE
        stats_timer = new CountDownTimer(stats_refresh_period*60, stats_refresh_period) {			
			@Override
			public void onTick(long millisUntilFinished) {
				display_stats();
			}
			
			@Override
			public void onFinish() {
				stats_timer.start();				
			}
		};
		stats_timer.start();
	}
	
	private void display_stats() {
		float rpm = 60000 / blink_period;
		float on_err_in_ms = light_on_error / 1000000;
		float off_err_in_ms = light_off_error / 1000000;
		float rpm_err = (on_err_in_ms/blink_period) * rpm;
		float freq=1000 / (float) blink_period;
		float freq_err= (on_err_in_ms/blink_period) * freq;

		info_text.setText(String.format(getString(R.string.RPM_info), 
				blink_period, on_err_in_ms, 
				on_period, off_err_in_ms,
				freq, freq_err,
				rpm, rpm_err));
		light_on_error=0;
		light_off_error=0;
	}
}
