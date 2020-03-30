package application;

import java.awt.Color;
import java.awt.image.BufferedImage;
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
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import utilities.Utilities;

public class Controller {
	
	@FXML
	private ImageView originalVid; // the image display window in the GUI
	@FXML
	private ImageView stiColumn;
	@FXML
	private ImageView stiRow;
	@FXML
	private VBox vbox;
		
	@FXML
	private Slider slider;
	
	@FXML
	private Slider volumeSlider;
	
	private VideoCapture capture;
	private ScheduledExecutorService timer;
	
	@FXML
	private void initialize() {
		System.out.println("Welcome to my program!");
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
				Utilities.onFXThread(originalVid.imageProperty(), im);
			}
		}
	}

	@FXML
	protected void playImage(ActionEvent event) throws LineUnavailableException, InterruptedException {
		if (capture.isOpened()) { // open successfully
			createFrameGrabber();
		}
	} 
			
	protected void createFrameGrabber() throws InterruptedException { 
		if (capture != null && capture.isOpened()) { // the video must be open 
			double framePerSecond = capture.get(Videoio.CAP_PROP_FPS); // create a runnable to fetch new frames periodically 
			
			int height = (int) capture.get(Videoio.CAP_PROP_FRAME_HEIGHT);
			int width = (int) capture.get(Videoio.CAP_PROP_FRAME_WIDTH);
			int frameCount = (int) capture.get(Videoio.CAP_PROP_FRAME_COUNT);
			
			
			int[][] stiCols = new int[frameCount][height];
			int[][] stiRows = new int[frameCount][width];
			

			stiColumn.setFitHeight(height);
			stiColumn.setFitWidth(frameCount);
			stiRow.setFitHeight(width);
			stiRow.setFitWidth(frameCount);

			BufferedImage stiColImage = new BufferedImage(frameCount, height, BufferedImage.TYPE_INT_ARGB);
			BufferedImage stiRowImage = new BufferedImage(frameCount, width, BufferedImage.TYPE_INT_ARGB);
			
			Runnable frameGrabber = new Runnable() {
				@Override 
				public void run() { 
					Mat frame = new Mat(); 
					if (capture.read(frame)) { // decode successfully 
						double currentFrameNumber = capture.get(Videoio.CAP_PROP_POS_FRAMES);
						// Get center column
						getColumn(frame, stiCols[(int)currentFrameNumber - 1], height, width);
						getRow(frame, stiRows[(int)currentFrameNumber - 1], height, width);

						Image im = Utilities.mat2Image(frame);
						Utilities.onFXThread(originalVid.imageProperty(), im); 

						slider.setValue(currentFrameNumber / frameCount * (slider.getMax() - slider.getMin())); 
						if (currentFrameNumber == frameCount) {
							capture.release();
							slider.setValue(0);
						}
						
						
						int i = (int) currentFrameNumber - 1;
                        for (int j = 0; j < height; j++) {
                            Color cCol = new Color(stiCols[i][j], true);
                            stiColImage.setRGB(i, j, cCol.getRGB());
                        }
                        
                        for (int j = 0; j < width; j++) {
                            Color cRow = new Color(stiRows[i][j], true);
                            stiRowImage.setRGB(i, j, cRow.getRGB());                        	
                        }
                        
                      
                        // Set image
                        Image cardCol = SwingFXUtils.toFXImage(stiColImage, null);
                        stiColumn.setImage(cardCol);
                        Image cardRow = SwingFXUtils.toFXImage(stiRowImage, null);
                        stiRow.setImage(cardRow);
                        
					} else { // reach the end of the video
							capture.set(Videoio.CAP_PROP_POS_FRAMES, 0); 
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
	
	protected void getColumn(Mat frame, int[] rgb, int height, int width) {
		BufferedImage img = Utilities.matToBufferedImage(frame);
		for(int i = 0; i < height; i++) {
			rgb[i] = img.getRGB(width/2, i);
		}
	}
	
	protected void getRow(Mat frame, int[] rgb, int height, int width) {
		BufferedImage img = Utilities.matToBufferedImage(frame);
		for(int i = 0; i < width; i++) {
			rgb[i] = img.getRGB(i, height/2);
		}
	}
	
	protected float[] getChromaticity(Color rgb) {
		float[] rg = new float[2];
		float r;
		float g;
		
		int R = rgb.getRed();
		int G = rgb.getGreen();
		int B = rgb.getBlue();
		int sum = R + G + B;
		
		if (sum == 0) { // if pixel is black
			r = 0;
			g = 0;
		} else {
			r = R / sum;
			g = G / sum;
		}
		rg[0] = r;
		rg[1] = g;
		
		return rg;
	}
	 
}
