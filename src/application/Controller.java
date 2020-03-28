package application;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import utilities.Utilities;

public class Controller {
	
	@FXML
	private ImageView imageView; // the image display window in the GUI
	@FXML
	private VBox vbox;
	
	private Mat image;
	
	private final static int BUFFER_SIZE = 128000;
	
	private int width;
	private int height;
	private int sampleRate; // sampling frequency
	private int sampleSizeInBits;
	private int numberOfChannels;
	private double[] freq; // frequencies for each particular row
	private int numberOfQuantizionLevels;
	private int numberOfSamplesPerColumn;
	
	@FXML
	private Slider slider;
	
	@FXML
	private Slider volumeSlider;
	
	private VideoCapture capture;
	private ScheduledExecutorService timer;
	
	@FXML
	private void initialize() {
		// Optional: You should modify the logic so that the user can change these values
		// You may also do some experiments with different values
		width = 64;
		height = 64;
		sampleRate = 32000;
		sampleSizeInBits = 8;
		numberOfChannels = 1;
		
		numberOfQuantizionLevels = 16;
		
		numberOfSamplesPerColumn = 500;
		
		// assign frequencies for each particular row
		freq = new double[height]; // Be sure you understand why it is height rather than width
		freq[height/2-1] = 440.0; // 440KHz - Sound of A (La)
		for (int m = height/2; m < height; m++) {
			freq[m] = freq[m-1] * Math.pow(2, 1.0/12.0); 
		}
		for (int m = height/2-2; m >=0; m--) {
			freq[m] = freq[m+1] * Math.pow(2, -1.0/12.0); 
		}
		
		volumeSlider.valueProperty().addListener(new ChangeListener<Number>() {
				@Override
				public void changed(ObservableValue<? extends Number> observableVal, Number oldVal, Number newVal) {
					// TODO Auto-generated method stub
					Mixer.Info [] mixers = AudioSystem.getMixerInfo();  
					for (Mixer.Info mixerInfo : mixers){
					    Mixer mixer = AudioSystem.getMixer(mixerInfo);
					    Line.Info [] lineInfos = mixer.getTargetLineInfo();
					    for (Line.Info lineInfo : lineInfos){
					        Line line = null;  
					        boolean opened = true;  
					        try {
					            line = mixer.getLine(lineInfo);  
					            opened = line.isOpen() || line instanceof Clip;
					            if (!opened)    
					                line.open();
					            FloatControl volCtrl = (FloatControl)line.getControl(FloatControl.Type.VOLUME);  
					            volCtrl.setValue(newVal.floatValue() / 100);
					        }  
					        catch (LineUnavailableException e) {  
					            e.printStackTrace();  
					        }  
					        catch (IllegalArgumentException iaEx) {  
					        }  
					        finally {  
					            if (line != null && !opened) 
					                line.close();
					        }  
					    }
					}
				}
    	});
		volumeSlider.setValue(100);
	}
	
	@FXML
	public void setFileName(ActionEvent event) throws InterruptedException {
		FileChooser file = new FileChooser();
		File selectedFile = file.showOpenDialog(null);
		if(selectedFile != null) {
			capture = new VideoCapture(selectedFile.getAbsolutePath());
			//show image of first frame
			Mat frame = new Mat(); 
			if (capture.read(frame)) { // decode successfully 
				Image im = Utilities.mat2Image(frame);
				Utilities.onFXThread(imageView.imageProperty(), im);
				
				//bind the image to the vbox as user scales the window
				imageView.fitWidthProperty().bind(vbox.widthProperty());
				imageView.fitHeightProperty().bind(vbox.heightProperty());
			}
		}
	}

	@FXML
	protected void playImage(ActionEvent event) throws LineUnavailableException, InterruptedException {
		if (capture.isOpened()) { // open successfully
			createFrameGrabber();
		}
	} 
	
	protected void play() throws LineUnavailableException {
		// This method "plays" the image opened by the user
		// You should modify the logic so that it plays a video rather than an image
		if (image != null) {
			// convert the image from RGB to grayscale
			Mat grayImage = new Mat();
			Imgproc.cvtColor(image, grayImage, Imgproc.COLOR_BGR2GRAY);
			
			// resize the image
			Mat resizedImage = new Mat();
			Imgproc.resize(grayImage, resizedImage, new Size(width, height));
			
			// quantization
			double[][] roundedImage = new double[resizedImage.rows()][resizedImage.cols()];
			for (int row = 0; row < resizedImage.rows(); row++) {
				for (int col = 0; col < resizedImage.cols(); col++) {
					roundedImage[row][col] = (double)Math.floor(resizedImage.get(row, col)[0]/numberOfQuantizionLevels) / numberOfQuantizionLevels;
				}
			}
			
			// I used an AudioFormat object and a SourceDataLine object to perform audio output. Feel free to try other options
	        AudioFormat audioFormat = new AudioFormat(sampleRate, sampleSizeInBits, numberOfChannels, true, true);
            SourceDataLine sourceDataLine = AudioSystem.getSourceDataLine(audioFormat);
            sourceDataLine.open(audioFormat, sampleRate);
            sourceDataLine.start();
            
            for (int col = 0; col < width; col++) {
            	byte[] audioBuffer = new byte[numberOfSamplesPerColumn];
            	for (int t = 1; t <= numberOfSamplesPerColumn; t++) {
            		double signal = 0;
                	for (int row = 0; row < height; row++) {
                		int m = height - row - 1; // Be sure you understand why it is height rather width, and why we subtract 1 
                		int time = t + col * numberOfSamplesPerColumn;
                		double ss = Math.sin(2 * Math.PI * freq[m] * (double)time/sampleRate);
                		signal += roundedImage[row][col] * ss;
                	}
                	double normalizedSignal = signal / height; // signal: [-height, height];  normalizedSignal: [-1, 1]
                	audioBuffer[t-1] = (byte) (normalizedSignal*0x7F); // Be sure you understand what the weird number 0x7F is for
            	}
            	sourceDataLine.write(audioBuffer, 0, numberOfSamplesPerColumn);
            }
            sourceDataLine.drain();
            sourceDataLine.close();
		} else {
			// What should you do here?
		}
	} 
	
	/**
	 * Plays a click noise
	 * Adapted from https://stackoverflow.com/questions/2416935/how-to-play-wav-files-with-java
	 * @throws LineUnavailableException
	 * @throws IOException 
	 * @throws UnsupportedAudioFileException 
	 */
	public static void playClick() throws LineUnavailableException, UnsupportedAudioFileException, IOException {
		// play 2click2furious wav file
		String strFilename = "resources/2click2furious.wav";
		File soundFile;
		AudioInputStream audioStream;
	    AudioFormat audioFormat;
	    SourceDataLine sourceLine;
	    
	    soundFile = new File(strFilename);

        audioStream = AudioSystem.getAudioInputStream(soundFile);
        audioFormat = audioStream.getFormat();

        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);

        sourceLine = (SourceDataLine) AudioSystem.getLine(info);
        sourceLine.open(audioFormat);
        sourceLine.start();

        int nBytesRead = 0;
        byte[] abData = new byte[BUFFER_SIZE];
        while (nBytesRead != -1) {
            try {
                nBytesRead = audioStream.read(abData, 0, abData.length);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (nBytesRead >= 0) {
                @SuppressWarnings("unused")
                int nBytesWritten = sourceLine.write(abData, 0, nBytesRead);
            }
        }

        sourceLine.drain();
        sourceLine.close();
	} 
	
	protected void createFrameGrabber() throws InterruptedException { 
		if (capture != null && capture.isOpened()) { // the video must be open 
			double framePerSecond = capture.get(Videoio.CAP_PROP_FPS); // create a runnable to fetch new frames periodically 
			Runnable frameGrabber = new Runnable() {
				int counter = 0;
				@Override 
				public void run() { 
					Mat frame = new Mat(); 
					if (capture.read(frame)) { // decode successfully 
						Image im = Utilities.mat2Image(frame);
						Utilities.onFXThread(imageView.imageProperty(), im); 
						image = frame;
						try {
							play();
						} catch (LineUnavailableException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						double currentFrameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES); 
						double totalFrameCount = capture.get(Videoio.CAP_PROP_FRAME_COUNT);
						slider.setValue(currentFrameNumber / totalFrameCount * (slider.getMax() - slider.getMin())); 
						if (currentFrameNumber == totalFrameCount) {
							capture.release();
							slider.setValue(0);
						}
						counter++;
					} else { // reach the end of the video
							capture.set(Videoio.CAP_PROP_POS_FRAMES, 0); 
					} 
					if (counter == 2) {
						// play click noise
						try {
							playClick();
						} catch (LineUnavailableException | UnsupportedAudioFileException | IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						counter = 0;
					}
				} 
			}; 
			// terminate the timer if it is running 
			if (timer != null && !timer.isShutdown()) { 
				timer.shutdown();
				timer.awaitTermination(Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS); 
			} // run the frame grabber 
			timer = Executors.newSingleThreadScheduledExecutor();
			timer.scheduleAtFixedRate(frameGrabber, 0, Math.round(1000/framePerSecond), TimeUnit.MILLISECONDS); 
		} 
	}
	 
}
